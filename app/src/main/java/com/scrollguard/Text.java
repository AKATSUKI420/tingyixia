package com.scrollguard;

import android.os.Build;

import java.util.Locale;

/**
 * 一处放全部文案，改起来只改这里。
 */
public final class Text {

    private Text() {
    }

    public static String mmss(long ms) {
        if (ms < 0) {
            ms = 0;
        }
        long total = ms / 1000L;
        long m = total / 60L;
        long s = total % 60L;
        return String.format(Locale.US, "%d 分 %02d 秒", m, s);
    }

    /** 今天刷了多久，粗一点 */
    public static String dur(long ms) {
        long total = ms / 1000L;
        if (total < 60) {
            return total + " 秒";
        }
        long m = total / 60L;
        if (m < 60) {
            return m + " 分钟";
        }
        return (m / 60L) + " 小时 " + (m % 60L) + " 分";
    }

    public static String limit(long min) {
        if (min < 60) {
            return min + " 分钟";
        }
        long h = min / 60;
        long m = min % 60;
        return m == 0 ? h + " 小时" : h + " 小时 " + m + " 分";
    }

    public static String lockTitle(long min) {
        return "已经刷了 " + min + " 分钟";
    }

    public static String lockFooter(long sec) {
        if (sec < 20) {
            return "先坐一会儿";
        }
        if (sec < 60) {
            return "停下来了 " + sec + " 秒";
        }
        long m = sec / 60L;
        long s = sec % 60L;
        return "停下来了 " + m + " 分 " + s + " 秒";
    }
}
