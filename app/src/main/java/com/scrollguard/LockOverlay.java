package com.scrollguard;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
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
    private View content;
    private WindowManager.LayoutParams lp;
    private boolean attached;
    private boolean hiding;
    private boolean unlocking;
    private int transition;
    private ValueAnimator windowFade;
    private String motion = Motion.DEFAULT;
    private PinPadView pad;
    private TextView footer;
    private long shownAt;
    private int autoUnlockMin;
    private int fails;
    private long cooldownUntil;
    private boolean checking;
    private Runnable onUnlock;
    private Runnable onFocusLost;

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
            // 藏起来的时候别再每秒改页脚；那会让一个看不见的全屏窗口一直重排，
            // 切最近任务时被它拖出卡顿。自动解开仍然要按时检查。
            if (attached && !hiding) {
                long sec = (now - shownAt) / 1000L;
                footer.setText(footerLine(sec, autoUnlockMin, shownAt, now));
            }
            if ((attached && !hiding) || autoUnlockMin > 0) {
                handler.postDelayed(this, 1000);
            }
        }
    };

    public LockOverlay(Context c) {
        ctx = c.getApplicationContext();
        wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
    }

    public boolean isShowing() {
        return view != null;
    }

    /** 这会儿是不是真的挡在屏幕上；动画退场和藏起来时都算没挡。 */
    public boolean isCovering() {
        return attached && !hiding && view != null && lp != null
                && (lp.flags & WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) == 0;
    }

    /** 只给测试看：窗口是不是还挂在 WindowManager 上。 */
    boolean isAttached() {
        return attached;
    }

    /** 遮罩窗口失去焦点（按 Home、切最近任务、跳到别的 App）时通知服务。 */
    public void setOnFocusLost(Runnable r) {
        onFocusLost = r;
    }

    /**
     * 只遮罩 App 时用：切到别处先把这一层收起来，锁本身还在；
     * 回到目标 App 再盖回去，不重新计时，也不重复记一次拦截。
     */
    public void setCovering(boolean covering) {
        if (unlocking || view == null || lp == null) {
            return;
        }
        if (covering) {
            if (attached && isCovering()) {
                return;
            }
            transition++;
            hiding = false;
            lp.flags &= ~(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                    | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
            if (!attached) {
                lp.alpha = 0f;
                try {
                    wm.addView(view, lp);
                    attached = true;
                } catch (Throwable ignored) {
                    return;
                }
            } else {
                try {
                    wm.updateViewLayout(view, lp);
                } catch (Throwable ignored) {
                    // 窗口已经被系统拿掉了，下一次 show 会重新来
                }
            }
            startWindowFade(1f, enterBackgroundMs(), transition, null);
            startContentIn();
            handler.removeCallbacks(ticker);
            handler.postDelayed(ticker, 1000);
            return;
        }

        if (!attached || !isCovering()) {
            return;
        }
        transition++;
        hiding = true;
        lp.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        try {
            wm.updateViewLayout(view, lp);
        } catch (Throwable ignored) {
            // 窗口已经被系统拿掉了
        }
        startWindowFade(0f, exitBackgroundMs(), transition, this::removeWindow);
        startContentOut();
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

        motion = Prefs.motion(ctx);
        Context themed = new ContextThemeWrapper(ctx, R.style.LockTheme);
        View v = LayoutInflater.from(themed).inflate(R.layout.overlay_lock, null);

        TextView title = v.findViewById(R.id.lock_title);
        footer = v.findViewById(R.id.lock_footer);
        pad = v.findViewById(R.id.pad);
        content = v.findViewById(R.id.lock_content);

        title.setText(Text.lockTitle(limitMin, global));
        footer.setText("");
        pad.setMessage("");
        pad.setListener(this::onPin);
        prepareContentIn();

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
        lp.alpha = 0f;
        lp.windowAnimations = 0;

        try {
            wm.addView(v, lp);
        } catch (Throwable t) {
            content = null;
            pad = null;
            footer = null;
            return;
        }

        view = v;
        this.lp = lp;
        attached = true;
        hiding = false;
        unlocking = false;
        transition++;
        onUnlock = unlockCallback;
        if (v instanceof LockRoot) {
            ((LockRoot) v).setOnFocusLost(() -> {
                if (onFocusLost != null) {
                    onFocusLost.run();
                }
            });
        }
        shownAt = SystemClock.elapsedRealtime();
        autoUnlockMin = autoMinutes;
        fails = 0;
        cooldownUntil = 0;
        checking = false;
        startWindowFade(1f, enterBackgroundMs(), transition, null);
        startContentIn();
        handler.postDelayed(ticker, 1000);
    }

    public void dismiss() {
        transition++;
        handler.removeCallbacksAndMessages(null);
        cancelWindowFade();
        if (content != null) {
            content.animate().cancel();
        }
        View v = view;
        view = null;
        attached = false;
        hiding = false;
        if (v != null) {
            try {
                wm.removeView(v);
            } catch (Throwable ignored) {
                // 已经被系统拿掉了
            }
        }
        content = null;
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
        if (view == null) {
            return;
        }
        if (unlocking) {
            return;
        }
        if (!attached) {
            dismiss();
            if (cb != null) {
                cb.run();
            }
            return;
        }

        transition++;
        unlocking = true;
        hiding = true;
        if (lp != null) {
            lp.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                    | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
            try {
                wm.updateViewLayout(view, lp);
            } catch (Throwable ignored) {
                // 窗口已经被系统拿掉了
            }
        }
        int gen = transition;
        startWindowFade(0f, exitBackgroundMs(), gen, () -> {
            if (gen != transition) {
                return;
            }
            removeWindow();
            cleanupAfterDismiss();
            if (cb != null) {
                cb.run();
            }
        });
        startContentOut();
    }

    private void removeWindow() {
        if (!attached || view == null) {
            return;
        }
        attached = false;
        try {
            wm.removeView(view);
        } catch (Throwable ignored) {
            // 已经被系统拿掉了
        }
    }

    private void cleanupAfterDismiss() {
        handler.removeCallbacksAndMessages(null);
        cancelWindowFade();
        view = null;
        content = null;
        pad = null;
        footer = null;
        lp = null;
        onUnlock = null;
        checking = false;
        autoUnlockMin = AutoUnlock.OFF;
        hiding = false;
        unlocking = false;
    }

    private void cancelWindowFade() {
        if (windowFade != null) {
            ValueAnimator a = windowFade;
            windowFade = null;
            a.cancel();
        }
    }

    private void startWindowFade(float to, long duration, int gen, Runnable end) {
        cancelWindowFade();
        if (view == null || lp == null) {
            if (end != null) {
                end.run();
            }
            return;
        }
        final float from = lp.alpha;
        if (Math.abs(from - to) < 0.01f) {
            lp.alpha = to;
            if (end != null) {
                end.run();
            }
            return;
        }
        ValueAnimator a = ValueAnimator.ofFloat(from, to);
        a.setDuration(duration);
        a.setInterpolator(to > from ? new DecelerateInterpolator() : new AccelerateInterpolator());
        a.addUpdateListener(anim -> {
            if (view == null || lp == null) {
                return;
            }
            lp.alpha = (float) anim.getAnimatedValue();
            try {
                wm.updateViewLayout(view, lp);
            } catch (Throwable ignored) {
                // 窗口已经被系统拿掉了
            }
        });
        a.addListener(new AnimatorListenerAdapter() {
            private boolean canceled;

            @Override
            public void onAnimationCancel(Animator animation) {
                canceled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (canceled || gen != transition || windowFade != a) {
                    return;
                }
                windowFade = null;
                if (end != null) {
                    end.run();
                }
            }
        });
        windowFade = a;
        a.start();
    }

    private void prepareContentIn() {
        if (content == null) {
            return;
        }
        content.animate().cancel();
        if (Motion.STAGGER.equals(motion)) {
            content.setAlpha(1f);
            content.setTranslationY(0f);
            content.setScaleX(1f);
            content.setScaleY(1f);
            for (View v : staggerBlocks()) {
                v.setAlpha(0f);
                v.setTranslationY(Ui.dp(ctx, 4));
            }
            return;
        }
        content.setAlpha(0f);
        content.setTranslationY(startOffsetDp());
        content.setScaleX(startScale());
        content.setScaleY(startScale());
    }

    private void startContentIn() {
        if (content == null) {
            return;
        }
        content.animate().cancel();
        if (Motion.STAGGER.equals(motion)) {
            content.setAlpha(1f);
            View[] blocks = staggerBlocks();
            for (int i = 0; i < blocks.length; i++) {
                View v = blocks[i];
                v.setAlpha(0f);
                v.setTranslationY(Ui.dp(ctx, 4));
                v.animate()
                        .alpha(1f)
                        .translationY(0f)
                        .setStartDelay(i * 25L)
                        .setDuration(180)
                        .setInterpolator(new DecelerateInterpolator())
                        .start();
            }
            return;
        }
        content.setAlpha(0f);
        content.setTranslationY(startOffsetDp());
        content.setScaleX(startScale());
        content.setScaleY(startScale());
        content.animate()
                .alpha(1f)
                .translationY(0f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(enterContentMs())
                .setInterpolator(Motion.SPRING.equals(motion)
                        ? new OvershootInterpolator(0.8f) : new DecelerateInterpolator())
                .start();
    }

    private void startContentOut() {
        if (content == null) {
            return;
        }
        content.animate().cancel();
        if (Motion.STAGGER.equals(motion)) {
            content.animate()
                    .alpha(0f)
                    .setDuration(120)
                    .setInterpolator(new AccelerateInterpolator())
                    .start();
            return;
        }
        content.animate()
                .alpha(0f)
                .translationY(Motion.SHEET.equals(motion) ? Ui.dp(ctx, 32) : endOffsetDp())
                .scaleX(Motion.SPRING.equals(motion) ? 0.96f : 0.97f)
                .scaleY(Motion.SPRING.equals(motion) ? 0.96f : 0.97f)
                .setDuration(exitContentMs())
                .setInterpolator(new AccelerateInterpolator())
                .start();
    }

    private View[] staggerBlocks() {
        View root = content != null ? content : view;
        if (root == null) {
            return new View[0];
        }
        return new View[]{
                root.findViewById(R.id.lock_icon),
                root.findViewById(R.id.lock_kicker),
                root.findViewById(R.id.lock_title),
                root.findViewById(R.id.lock_sub),
                root.findViewById(R.id.pad),
                root.findViewById(R.id.lock_footer)
        };
    }

    private float startScale() {
        if (Motion.SPRING.equals(motion)) {
            return 0.90f;
        }
        return Motion.FADE.equals(motion) ? 0.97f : 1f;
    }

    private int startOffsetDp() {
        if (Motion.SHEET.equals(motion)) {
            return 32;
        }
        return Motion.FADE.equals(motion) ? 6 : 0;
    }

    private int endOffsetDp() {
        return Motion.FADE.equals(motion) ? 6 : 0;
    }

    private long enterBackgroundMs() {
        return Motion.SHEET.equals(motion) || Motion.SPRING.equals(motion) ? 100 : 120;
    }

    private long exitBackgroundMs() {
        switch (Motion.normalize(motion)) {
            case Motion.SHEET:
                return 180;
            case Motion.STAGGER:
                return 120;
            case Motion.SPRING:
                return 120;
            default:
                return 140;
        }
    }

    private long enterContentMs() {
        return Motion.SHEET.equals(motion) || Motion.SPRING.equals(motion) ? 260 : 220;
    }

    private long exitContentMs() {
        switch (Motion.normalize(motion)) {
            case Motion.SHEET:
                return 180;
            case Motion.STAGGER:
                return 120;
            case Motion.SPRING:
                return 120;
            default:
                return 140;
        }
    }
}
