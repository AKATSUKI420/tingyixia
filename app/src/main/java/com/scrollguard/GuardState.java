package com.scrollguard;

/**
 * 无障碍服务与计时服务之间的共享状态。都在同一个进程里，直接读就行。
 */
public final class GuardState {

    private static volatile GuardState instance;

    public static GuardState get() {
        GuardState s = instance;
        if (s == null) {
            synchronized (GuardState.class) {
                s = instance;
                if (s == null) {
                    s = new GuardState();
                    instance = s;
                }
            }
        }
        return s;
    }

    private GuardState() {
    }

    /** 计时服务是否在跑 */
    public volatile boolean serviceRunning = false;

    /** 当前前台应用的包名，不知道就是空串；系统弹窗不覆盖它 */
    public volatile String fgPackage = "";

    /** 每收到一次真正的前台切换就加一；用来判断切走之后有没有回到目标 App */
    public volatile long fgSeq = 0L;

    /** 屏幕是不是竖着的（由计时服务按当前配置判定） */
    public volatile boolean portrait = true;

    /** 最近 45 秒里有没有滑动过 */
    public volatile long lastScrollAt = 0L;

    /** 这一轮已经连续计时多久 */
    public volatile long countedMs = 0L;

    /** 此刻是不是正盖着锁屏 */
    public volatile boolean locked = false;

    public void touchScroll() {
        lastScrollAt = System.currentTimeMillis();
    }

    public boolean scrolledWithin(long windowMs) {
        return System.currentTimeMillis() - lastScrollAt < windowMs;
    }
}
