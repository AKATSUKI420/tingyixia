package com.scrollguard;

import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

/**
 * 盖在短视频上面的那一层。用悬浮窗做，按 Home 也不会掉。
 */
public final class LockOverlay {

    private static final int COOLDOWN_SEC = 30;
    private static final int FAILS_BEFORE_WAIT = 3;

    private final Context ctx;
    private final WindowManager wm;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private View view;
    private WindowManager.LayoutParams lp;
    private PinPadView pad;
    private TextView footer;
    private long shownAt;
    private int autoUnlockMin;
    private int fails;
    private long cooldownUntil;
    private boolean checking;
    private Runnable onUnlock;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            if (view == null) {
                return;
            }
            long now = SystemClock.elapsedRealtime();
            if (AutoUnlock.due(autoUnlockMin, shownAt, now)) {
                finishedUnlock();
                return;
            }
            long sec = (now - shownAt) / 1000L;
            footer.setText(footerLine(sec, autoUnlockMin, shownAt, now));
            handler.postDelayed(this, 1000);
        }
    };

    public LockOverlay(Context c) {
        ctx = c.getApplicationContext();
        wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
    }

    public boolean isShowing() {
        return view != null;
    }

    /** 这会儿是不是真的挡在屏幕上；只遮罩 App 时可能还挂着但已经藏起来。 */
    public boolean isCovering() {
        return view != null && lp != null
                && (lp.flags & WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) == 0;
    }

    /**
     * 只遮罩 App 时用：切到别处先把这一层藏起来，锁本身还在；
     * 回到目标 App 再盖回去，不重新计时，也不重复记一次拦截。
     */
    public void setCovering(boolean covering) {
        if (view == null || lp == null || isCovering() == covering) {
            return;
        }
        if (covering) {
            lp.flags &= ~(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                    | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
            lp.alpha = 1f;
        } else {
            lp.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                    | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
            lp.alpha = 0f;
        }
        try {
            wm.updateViewLayout(view, lp);
        } catch (Throwable ignored) {
            // 窗口已经被系统拿掉了，下一次 show 会重新来
        }
    }

    /** 遮罩下面那行字：停了多久；设了自动解开就再缀一句还剩多久。 */
    static String footerLine(long sec, int autoMinutes, long shownAt, long now) {
        String line = Text.lockFooter(sec);
        if (autoMinutes > 0) {
            line = line + " · " + AutoUnlock.countdown(
                    AutoUnlock.remainingMs(autoMinutes, shownAt, now));
        }
        return line;
    }

    public void show(int limitMin, Runnable unlockCallback) {
        show(limitMin, AutoUnlock.OFF, false, unlockCallback);
    }

    public void show(int limitMin, int autoMinutes, Runnable unlockCallback) {
        show(limitMin, autoMinutes, false, unlockCallback);
    }

    public void show(int limitMin, int autoMinutes, boolean global, Runnable unlockCallback) {
        if (view != null || wm == null) {
            return;
        }

        Context themed = new ContextThemeWrapper(ctx, R.style.LockTheme);
        View v = LayoutInflater.from(themed).inflate(R.layout.overlay_lock, null);

        TextView title = v.findViewById(R.id.lock_title);
        footer = v.findViewById(R.id.lock_footer);
        pad = v.findViewById(R.id.pad);

        title.setText(Text.lockTitle(limitMin, global));
        footer.setText("");
        pad.setMessage("");
        pad.setListener(this::onPin);

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.OPAQUE);
        lp.gravity = Gravity.TOP | Gravity.START;
        // The lock screen is fully opaque. Making that explicit avoids a
        // translucent-to-opaque surface swap on the first frame, which shows
        // up as a brief flash over the app underneath.
        lp.alpha = 1f;
        lp.windowAnimations = 0;

        try {
            wm.addView(v, lp);
        } catch (Throwable t) {
            pad = null;
            footer = null;
            return;
        }

        view = v;
        this.lp = lp;
        onUnlock = unlockCallback;
        shownAt = SystemClock.elapsedRealtime();
        autoUnlockMin = autoMinutes;
        fails = 0;
        cooldownUntil = 0;
        checking = false;
        handler.postDelayed(ticker, 1000);
    }

    public void dismiss() {
        handler.removeCallbacksAndMessages(null);
        View v = view;
        view = null;
        if (v != null) {
            try {
                wm.removeView(v);
            } catch (Throwable ignored) {
                // 已经被系统拿掉了
            }
        }
        pad = null;
        footer = null;
        lp = null;
        onUnlock = null;
        checking = false;
        autoUnlockMin = AutoUnlock.OFF;
    }

    private void onPin(String pin) {
        if (checking || view == null) {
            return;
        }
        long now = SystemClock.elapsedRealtime();
        if (now < cooldownUntil) {
            long left = (cooldownUntil - now + 999L) / 1000L;
            pad.error("等一下，还有 " + left + " 秒");
            return;
        }

        checking = true;
        pad.setMessage("看一眼…");

        new Thread(() -> {
            boolean ok = Pin.check(ctx, pin);
            handler.post(() -> {
                if (view == null) {
                    return;
                }
                checking = false;
                if (ok) {
                    finishedUnlock();
                } else {
                    beWrong(SystemClock.elapsedRealtime());
                }
            });
        }, "pin-check").start();
    }

    private void beWrong(long now) {
        fails++;
        if (fails >= FAILS_BEFORE_WAIT) {
            fails = 0;
            cooldownUntil = now + COOLDOWN_SEC * 1000L;
            pad.error("错太多次了，等 " + COOLDOWN_SEC + " 秒");
        } else if (fails == FAILS_BEFORE_WAIT - 1) {
            pad.error("不对 · 再错要等 " + COOLDOWN_SEC + " 秒");
        } else {
            pad.error("密码不对");
        }
    }

    private void finishedUnlock() {
        Runnable cb = onUnlock;
        dismiss();
        if (cb != null) {
            cb.run();
        }
    }
}
