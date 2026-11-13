package com.scrollguard;

/**
 * 计时口径的唯一实现。
 *
 * <p>GuardService 每秒把「这一刻算不算数」喂进来，由这里决定：连续时长涨不涨、
 * 离开多久清零、攒够多少该落盘、什么时候该拦。抽成一个不碰 Android 的类，
 * 是为了这九种情况能在没有手机的情况下逐条测出来（见 DEVPLAN 的 M3）。
 */
public final class Ticker {

    /** 连续不满足条件超过这么久，连续时长清零 */
    public static final long IDLE_RESET_MS = 2500L;

    /** 每攒够这么久，把「今天看了多久」落盘一次 */
    public static final long FLUSH_EVERY_MS = 15000L;

    /** 单次 tick 的合理上限；休眠回来那种大跳按 1 秒算 */
    public static final long MAX_STEP_MS = 5000L;

    /** 最近这么久里滑动过，就算「有动静」 */
    public static final long SCROLL_WINDOW_MS = 45000L;

    private long countedMs;
    private long idleMs;
    private long pendingMs;

    /** 这一轮已经拦过没有；拦过之后要等到清零才能再拦 */
    private boolean fired;

    public long countedMs() {
        return countedMs;
    }

    public long idleMs() {
        return idleMs;
    }

    public long pendingMs() {
        return pendingMs;
    }

    /**
     * 这一刻算不算数：竖着拿 + 屏幕亮着 + 前台是盯着的那两个 App + 真有动静。
     */
    public static boolean counts(boolean portrait, boolean screenOn, boolean isTarget, boolean active) {
        return portrait && screenOn && isTarget && active;
    }

    /**
     * 严格模式下额外要求「有声音在播」或「最近滑动过」；非严格模式一律算有动静。
     */
    public static boolean active(boolean strict, boolean playing, boolean scrolledRecently) {
        return !strict || playing || scrolledRecently;
    }

    /** 把两次 tick 的间隔夹到合理范围：0、负数、以及休眠回来的大跳都当成 1 秒 */
    public static long clamp(long dt) {
        if (dt <= 0L || dt > MAX_STEP_MS) {
            return 1000L;
        }
        return dt;
    }

    /**
     * 走一步。
     *
     * @param dt       距上次的毫秒数（先过 {@link #clamp(long)}）
     * @param counting 这一刻算不算数
     * @param limitMs  连续攒够多少算满
     * @return true 表示这一步刚好攒够了，该拦
     */
    public boolean advance(long dt, boolean counting, long limitMs) {
        if (dt < 0L) {
            dt = 0L;
        }
        if (counting) {
            idleMs = 0L;
            countedMs += dt;
            pendingMs += dt;
            if (countedMs >= limitMs && !fired) {
                fired = true;
                return true;
            }
            return false;
        }
        idleMs += dt;
        if (idleMs >= IDLE_RESET_MS) {
            idleMs = 0L;
            countedMs = 0L;
            fired = false;
        }
        return false;
    }

    /** 解锁或没拦成之后清空连续时长；今天的统计不动 */
    public void resetCounting() {
        countedMs = 0L;
        idleMs = 0L;
        fired = false;
    }

    /** 取走攒着还没落盘的时长 */
    public long takePending() {
        long v = pendingMs;
        pendingMs = 0L;
        return v;
    }
}
