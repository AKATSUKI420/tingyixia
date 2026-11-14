package com.scrollguard;

import java.util.Locale;

/**
 * 锁屏之后多久自己解开。0 = 不自动解开（得输密码）。
 * 纯算术，不碰 Android，测试里直接调。
 */
public final class AutoUnlock {

    public static final int OFF = 0;
    public static final int MIN = 0;
    public static final int MAX = 600;

    private AutoUnlock() {
    }

    /** 锁一会儿就自己解开的那段时长；不自动解开时返回 -1。 */
    public static long windowMs(int minutes) {
        if (minutes <= 0) {
            return -1L;
        }
        int m = Math.min(minutes, MAX);
        return m * 60000L;
    }

    /** 还差多少毫秒自己解开；不自动解开返回 -1；已经到点返回 0。 */
    public static long remainingMs(int minutes, long shownAt, long now) {
        long w = windowMs(minutes);
        if (w < 0) {
            return -1L;
        }
        long left = shownAt + w - now;
        return left > 0 ? left : 0L;
    }

    /** 到点该解开了没有。 */
    public static boolean due(int minutes, long shownAt, long now) {
        long w = windowMs(minutes);
        return w >= 0 && now - shownAt >= w;
    }

    /** 首页那一行的值。 */
    public static String label(int minutes) {
        if (minutes <= 0) {
            return "不自动解开";
        }
        return Math.min(minutes, MAX) + " 分钟后自动解开";
    }

    /** 遮罩下面那行倒计时。 */
    public static String countdown(long remainingMs) {
        long total = (remainingMs + 999L) / 1000L;
        long m = total / 60L;
        long s = total % 60L;
        if (m <= 0) {
            return s + " 秒后自己解开";
        }
        return String.format(Locale.US, "%d 分 %02d 秒后自己解开", m, s);
    }
}
