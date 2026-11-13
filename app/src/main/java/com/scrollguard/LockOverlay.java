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
    private PinPadView pad;
    private TextView footer;
    private long shownAt;
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
            long sec = (SystemClock.elapsedRealtime() - shownAt) / 1000L;
            footer.setText(Text.lockFooter(sec));
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

    public void show(int limitMin, Runnable unlockCallback) {
        if (view != null || wm == null) {
            return;
        }

        Context themed = new ContextThemeWrapper(ctx, R.style.LockTheme);
        View v = LayoutInflater.from(themed).inflate(R.layout.overlay_lock, null);

        TextView title = v.findViewById(R.id.lock_title);
        footer = v.findViewById(R.id.lock_footer);
        pad = v.findViewById(R.id.pad);

        title.setText(Text.lockTitle(limitMin));
        footer.setText("");
        pad.setMessage("");
        pad.setListener(this::onPin);

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;

        try {
            wm.addView(v, lp);
        } catch (Throwable t) {
            pad = null;
            footer = null;
            return;
        }

        view = v;
        onUnlock = unlockCallback;
        shownAt = SystemClock.elapsedRealtime();
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
        onUnlock = null;
        checking = false;
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
