package com.scrollguard;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.content.res.Configuration;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;

/**
 * 每秒看一眼：现在是不是在刷竖屏短视频？连着刷满就算。
 */
public class GuardService extends Service {

    public static final String ACTION_TEST = "com.scrollguard.TEST";

    private static final String CHANNEL = "guard";
    private static final int NOTI_ID = 1;

    private final Ticker ticker = new Ticker();

    private Handler handler;
    private LockOverlay overlay;
    private long lastTick;
    private long lastPlayCheck;
    private boolean playValue = true;

    private final Runnable loop = new Runnable() {
        @Override
        public void run() {
            try {
                step();
            } catch (Throwable ignored) {
                // 不让一次异常把整个计时停掉
            }
            if (handler != null) {
                handler.postDelayed(this, 1000);
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
        overlay = new LockOverlay(this);
        GuardState.get().serviceRunning = true;
        lastTick = SystemClock.elapsedRealtime();
        goForeground();
        ReviveJob.schedule(this);
        handler.postDelayed(loop, 1000);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!Prefs.enabled(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (intent != null && ACTION_TEST.equals(intent.getAction())) {
            tryTest();
        }
        return START_STICKY;
    }

    private void tryTest() {
        if (!Settings.canDrawOverlays(this)) {
            return;
        }
        if (overlay.isShowing()) {
            return;
        }
        overlay.show((int) Prefs.limitMin(this), () -> {
            GuardState s = GuardState.get();
            ticker.resetCounting();
            s.countedMs = 0L;
            s.locked = false;
            lastTick = SystemClock.elapsedRealtime();
        });
    }

    private void step() {
        long now = SystemClock.elapsedRealtime();
        long dt = Ticker.clamp(now - lastTick);
        lastTick = now;

        if (!Prefs.enabled(this)) {
            stopSelf();
            return;
        }

        if (overlay.isShowing()) {
            return;
        }

        GuardState st = GuardState.get();
        st.portrait = getResources().getConfiguration().orientation
                != Configuration.ORIENTATION_LANDSCAPE;

        boolean active = Ticker.active(
                Prefs.strict(this), isPlaying(), st.scrolledWithin(Ticker.SCROLL_WINDOW_MS));
        boolean counting = Ticker.counts(
                st.portrait, screenOn(), Prefs.targets(this).contains(st.fgPackage), active);

        boolean due = ticker.advance(dt, counting, Prefs.limitMin(this) * 60000L);
        st.countedMs = ticker.countedMs();

        if (ticker.pendingMs() >= Ticker.FLUSH_EVERY_MS) {
            flush();
        }
        if (due) {
            lock();
        }
    }

    private void lock() {
        GuardState st = GuardState.get();
        if (overlay.isShowing()) {
            return;
        }
        flush();
        if (!Settings.canDrawOverlays(this)) {
            // 没有悬浮窗权限就拦不住，别一直重试
            ticker.resetCounting();
            st.countedMs = 0L;
            return;
        }
        st.locked = true;
        Prefs.bumpTodayLocks(this);
        overlay.show((int) Prefs.limitMin(this), () -> {
            GuardState s = GuardState.get();
            ticker.resetCounting();
            s.countedMs = 0L;
            s.locked = false;
            lastTick = SystemClock.elapsedRealtime();
        });
    }

    /** 熄屏了就不算 */
    private boolean screenOn() {
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            return pm == null || pm.isInteractive();
        } catch (Throwable t) {
            return true;
        }
    }

    /** 严格模式用：有没有声音在响（能听见就基本是在播） */
    private boolean isPlaying() {
        long now = System.currentTimeMillis();
        if (now - lastPlayCheck < 2000L) {
            return playValue;
        }
        boolean v;
        try {
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            v = am != null && am.isMusicActive();
        } catch (Throwable t) {
            v = true;
        }
        lastPlayCheck = now;
        playValue = v;
        return v;
    }

    private void flush() {
        long v = ticker.takePending();
        if (v <= 0L) {
            return;
        }
        Prefs.addTodayMs(this, v);
    }

    private void goForeground() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL, "防护在运行", NotificationManager.IMPORTANCE_MIN);
            ch.setShowBadge(false);
            ch.enableVibration(false);
            ch.setSound(null, null);
            nm.createNotificationChannel(ch);
        }

        PendingIntent pi = PendingIntent.getActivity(
                this, 0, new Intent(this, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String line = "抖音 / 哔哩哔哩 竖屏连着刷 " + Prefs.limitMin(this) + " 分钟会拦一下";
        Notification n = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_noti)
                .setContentTitle("停一下在看着")
                .setContentText(line)
                .setOngoing(true)
                .setShowWhen(false)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setContentIntent(pi)
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTI_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTI_ID, n);
        }
    }

    @Override
    public void onDestroy() {
        flush();
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }
        if (overlay != null) {
            overlay.dismiss();
        }
        GuardState.get().serviceRunning = false;
        GuardState.get().fgPackage = "";
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
