package com.scrollguard;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;

/**
 * 拦截范围：只锁抖音 / 哔哩哔哩，还是整台手机都算。
 *
 * <p>只锁 App 时沿用原来的口径：竖屏 + 屏幕亮着 + 前台是目标应用 + 有动静。
 * 锁全局时只要屏幕亮着、前台不是桌面 / 系统界面 / 本应用，横竖屏都算。
 */
public final class Scope {

    public static final String APP = "app";
    public static final String GLOBAL = "global";

    public static final String LABEL_APP = "只锁抖音 / 哔哩哔哩";
    public static final String LABEL_GLOBAL = "整台手机";

    private Scope() {
    }

    /** 只认这两个值，别的（包括老版本没存过）都当只锁 App */
    public static String normalize(String raw) {
        return GLOBAL.equals(raw) ? GLOBAL : APP;
    }

    public static boolean isGlobal(Context c) {
        return GLOBAL.equals(Prefs.scope(c));
    }

    public static String label(Context c) {
        return isGlobal(c) ? LABEL_GLOBAL : LABEL_APP;
    }

    /**
     * 全局模式下，这个前台应用算不算「在用手机」。
     * 自己、系统界面、桌面不算；别的都算。
     */
    static boolean countable(Context c, String pkg) {
        if (pkg == null || pkg.isEmpty()) {
            return false;
        }
        if (c.getPackageName().equals(pkg)) {
            return false;
        }
        if (Shield.isNoise(pkg)) {
            return false;
        }
        return !isHome(c, pkg);
    }

    private static boolean isHome(Context c, String pkg) {
        try {
            Intent home = new Intent(Intent.ACTION_MAIN);
            home.addCategory(Intent.CATEGORY_HOME);
            ResolveInfo ri = c.getPackageManager()
                    .resolveActivity(home, 0);
            return ri != null && ri.activityInfo != null
                    && pkg.equals(ri.activityInfo.packageName);
        } catch (Throwable t) {
            return false;
        }
    }

    /** 这一刻算不算数。App 范围看竖屏和目标应用；全局范围只看屏幕、动静和前台是谁。 */
    public static boolean counts(Context c, String scope, boolean portrait, boolean screenOn,
                                 String fgPackage, boolean target, boolean active) {
        if (GLOBAL.equals(normalize(scope))) {
            return Ticker.counts(true, screenOn, countable(c, fgPackage), active);
        }
        return Ticker.counts(portrait, screenOn, target, active);
    }
}
