package com.scrollguard;

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
    private Runnable contentFrame;
    private boolean contentAnimating;
    private String motion = Motion.DEFAULT;
    private float speed = MotionSpeed.DEFAULT;
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
                // 窗口看着是盖着的，但输入通道可能已经被系统悄悄掐掉。
                // 主动要一次焦点，至少别让焦点留在下面的 App 上。
                view.requestFocus();
                return;
            }
            transition++;
            hiding = false;
            lp.flags &= ~(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                    | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
            lp.alpha = 0f;
            // Some OEMs keep a window's input channel disabled after it was made
            // non-touchable/non-focusable.  Re-adding the same view gives it a
            // fresh input channel, so the PIN pad stays tappable after a quick
            // hide/show (for example when Bilibili briefly steals focus).
            if (attached) {
                try {
                    wm.removeViewImmediate(view);
                } catch (Throwable ignored) {
                    // The system may already have removed it.
                }
                attached = false;
            }
            try {
                wm.addView(view, lp);
                attached = true;
            } catch (Throwable ignored) {
                // Last resort: keep the existing window; it may still recover
                // on the next layout pass.
                try {
                    wm.updateViewLayout(view, lp);
                    attached = true;
                } catch (Throwable alsoIgnored) {
                    return;
                }
            }
            view.setAlpha(1f);
            view.requestFocus();
            prepareContentIn();
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
        // 不要把窗口设成 NOT_TOUCHABLE：部分 OEM 在之后清掉这个标记时
        // 不会恢复输入通道，遮罩会一直看得见、却怎么点都没反应。
        // 退场动画跑完后直接把窗口摘掉，下一次重新 addView 就是新通道。
        startExitFrame(this::removeWindow);
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
        show(limitMin, AutoUnlock.OFF, unlockCallback);
    }

    public void show(int limitMin, int autoMinutes, Runnable unlockCallback) {
        if (view != null || wm == null) {
            return;
        }

        motion = Prefs.motion(ctx);
        speed = Prefs.motionSpeed(ctx);
        Context themed = new ContextThemeWrapper(ctx, R.style.LockTheme);
        View v = LayoutInflater.from(themed).inflate(R.layout.overlay_lock, null);

        TextView title = v.findViewById(R.id.lock_title);
        footer = v.findViewById(R.id.lock_footer);
        pad = v.findViewById(R.id.pad);
        content = v.findViewById(R.id.lock_content);

        title.setText(Text.lockTitle(limitMin));
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
            Runnable leave = () -> {
                if (onFocusLost != null) {
                    onFocusLost.run();
                }
            };
            ((LockRoot) v).setOnFocusLost(leave);
            ((LockRoot) v).setOnExitRequested(leave);
        }
        shownAt = SystemClock.elapsedRealtime();
        autoUnlockMin = autoMinutes;
        fails = 0;
        cooldownUntil = 0;
        checking = false;
        view.setAlpha(1f);
        view.requestFocus();
        startContentIn();
        handler.postDelayed(ticker, 1000);
    }

    public void dismiss() {
        transition++;
        handler.removeCallbacksAndMessages(null);
        cancelContentFrame();
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
        // 同上：退场期间也不设 NOT_TOUCHABLE，动画结束直接摘窗口。
        startExitFrame(() -> {
            removeWindow();
            cleanupAfterDismiss();
            if (cb != null) {
                cb.run();
            }
        });
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
        cancelContentFrame();
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

    private void setWindowAlpha(float alpha) {
        if (lp == null || view == null) {
            return;
        }
        lp.alpha = Math.max(0f, Math.min(1f, alpha));
        try {
            wm.updateViewLayout(view, lp);
        } catch (Throwable ignored) {
            // 窗口可能已经被系统拿掉
        }
    }

    private void cancelContentFrame() {
        if (contentFrame != null) {
            handler.removeCallbacks(contentFrame);
            contentFrame = null;
        }
        contentAnimating = false;
    }

    private void prepareContentIn() {
        cancelContentFrame();
        if (content == null) {
            return;
        }
        if (Motion.STAGGER.equals(motion)) {
            content.setAlpha(1f);
            for (View v : staggerBlocks()) {
                if (v != null) {
                    v.setAlpha(0f);
                    v.setTranslationY(Ui.dp(ctx, 4));
                }
            }
        } else {
            content.setAlpha(0f);
            content.setTranslationY(0f);
            content.setScaleX(startScale());
            content.setScaleY(startScale());
        }
    }

    private void startContentIn() {
        if (content == null || view == null) {
            return;
        }
        cancelContentFrame();
        contentAnimating = true;
        final long start = SystemClock.uptimeMillis();
        final long duration = Math.max(enterBackgroundMs(), enterContentMs());
        contentFrame = new Runnable() {
            @Override
            public void run() {
                if (!attached || view == null || content == null) {
                    contentAnimating = false;
                    contentFrame = null;
                    return;
                }
                float t = Math.min(1f,
                        (SystemClock.uptimeMillis() - start) / (float) duration);
                applyEnter(t);
                if (t < 1f) {
                    handler.postDelayed(this, 16);
                } else {
                    contentAnimating = false;
                    contentFrame = null;
                    settleContent();
                }
            }
        };
        handler.post(contentFrame);
    }

    private void startExitFrame(Runnable end) {
        if (content == null || view == null) {
            if (end != null) {
                end.run();
            }
            return;
        }
        cancelContentFrame();
        contentAnimating = true;
        final long start = SystemClock.uptimeMillis();
        final long duration = Math.max(exitBackgroundMs(), exitContentMs()) + 16;
        contentFrame = new Runnable() {
            @Override
            public void run() {
                if (view == null || content == null) {
                    contentAnimating = false;
                    contentFrame = null;
                    if (end != null) {
                        end.run();
                    }
                    return;
                }
                float t = Math.min(1f,
                        (SystemClock.uptimeMillis() - start) / (float) duration);
                applyExit(t);
                if (t < 1f) {
                    handler.postDelayed(this, 16);
                } else {
                    contentAnimating = false;
                    contentFrame = null;
                    if (end != null) {
                        end.run();
                    }
                }
            }
        };
        handler.post(contentFrame);
    }

    private void applyEnter(float t) {
        if (view == null || content == null) {
            return;
        }
        setWindowAlpha(Math.min(1f, t * 2.2f));
        if (Motion.STAGGER.equals(motion)) {
            content.setAlpha(1f);
            long elapsed = (long) (t * Math.max(enterContentMs(), 1));
            long step = Math.max(1L, Math.round(25 / speed));
            long block = Math.max(1L, Math.round(180 / speed));
            View[] blocks = staggerBlocks();
            for (int i = 0; i < blocks.length; i++) {
                View v = blocks[i];
                if (v == null) {
                    continue;
                }
                float bt = Math.max(0f, Math.min(1f, (elapsed - i * step) / (float) block));
                float be = new DecelerateInterpolator().getInterpolation(bt);
                v.setAlpha(be);
                v.setTranslationY(Ui.dp(ctx, 4) * (1f - be));
            }
            return;
        }
        float e = Motion.SPRING.equals(motion)
                ? new OvershootInterpolator(0.8f).getInterpolation(t)
                : new DecelerateInterpolator().getInterpolation(t);
        centerPivotIfNeeded();
        content.setAlpha(Math.min(1f, t * 1.6f));
        content.setTranslationY(Ui.dp(ctx, startOffsetDp()) * (1f - e));
        float scale = startScale() + (1f - startScale()) * e;
        content.setScaleX(scale);
        content.setScaleY(scale);
    }

    private void applyExit(float t) {
        if (view == null || content == null) {
            return;
        }
        float e = new AccelerateInterpolator().getInterpolation(t);
        centerPivotIfNeeded();
        setWindowAlpha(1f - t);
        content.setAlpha(1f - t);
        int toY = Motion.SHEET.equals(motion) ? 32 : endOffsetDp();
        content.setTranslationY(Ui.dp(ctx, toY) * e);
        float toScale = Motion.CENTER.equals(motion) ? 0.55f
                : Motion.SPRING.equals(motion) ? 0.96f : 0.97f;
        float scale = 1f - (1f - toScale) * e;
        content.setScaleX(scale);
        content.setScaleY(scale);
        if (Motion.STAGGER.equals(motion)) {
            for (View v : staggerBlocks()) {
                if (v != null) {
                    v.setAlpha(1f - t);
                }
            }
        }
    }

    private void settleContent() {
        if (view == null || content == null) {
            return;
        }
        view.setAlpha(1f);
        setWindowAlpha(1f);
        view.requestFocus();
        content.setAlpha(1f);
        content.setTranslationY(0f);
        content.setScaleX(1f);
        content.setScaleY(1f);
        for (View v : staggerBlocks()) {
            if (v != null) {
                v.setAlpha(1f);
                v.setTranslationY(0f);
            }
        }
    }

    boolean hasContentAnimation() {
        return contentAnimating;
    }

    /** 中心放大/缩小要用内容自己的中心当轴；布局还没量好时先不动。 */
    private void centerPivotIfNeeded() {
        if (!Motion.CENTER.equals(motion) || content == null) {
            return;
        }
        int w = content.getWidth();
        int h = content.getHeight();
        if (w > 0 && h > 0) {
            content.setPivotX(w / 2f);
            content.setPivotY(h / 2f);
        }
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
        if (Motion.CENTER.equals(motion)) {
            return 0.55f;
        }
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
        return scaled(Motion.SHEET.equals(motion) || Motion.SPRING.equals(motion) ? 100 : 120);
    }

    private long exitBackgroundMs() {
        return scaled(exitMs());
    }

    private long enterContentMs() {
        return scaled(Motion.STAGGER.equals(motion) ? 320
                : Motion.CENTER.equals(motion) ? 300
                : Motion.SHEET.equals(motion) || Motion.SPRING.equals(motion) ? 260 : 220);
    }

    private long exitContentMs() {
        return scaled(exitMs());
    }

    private long exitMs() {
        switch (Motion.normalize(motion)) {
            case Motion.SHEET:
            case Motion.CENTER:
                return 180;
            case Motion.STAGGER:
            case Motion.SPRING:
                return 120;
            default:
                return 140;
        }
    }

    /** 倍率越大，动画越快，时长越短。 */
    private long scaled(long base) {
        return Math.max(1L, Math.round(base / speed));
    }
}
