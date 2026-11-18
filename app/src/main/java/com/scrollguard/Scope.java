package com.scrollguard;

import android.content.Context;

/**
 * 遮罩只覆盖抖音 / 哔哩哔哩：竖屏 + 屏幕亮着 + 前台是目标应用 + 有动静。
 * 人切走或回到桌面，遮罩先收起来，回到目标 App 再盖回去。
 *
 * <p>旧版本存过“全局遮罩”的值，这里统一按只遮罩 App 处理，避免升级后
 * 还留下一个已经下线的模式。
 */
public final class Scope {

    public static final String APP = "app";

    public static final String LABEL_APP = "只遮罩 App";

    private Scope() {
    }

    /** 不论旧值是什么，都回到只遮罩 App */
    public static String normalize(String raw) {
        return APP;
    }

    public static boolean isGlobal(Context c) {
        return false;
    }

    public static String label(Context c) {
        return LABEL_APP;
    }

    /** 这一刻算不算数：只认竖屏、目标应用、屏幕亮着和有动静。 */
    public static boolean counts(Context c, String scope, boolean portrait, boolean screenOn,
                                 String fgPackage, boolean target, boolean active) {
        return Ticker.counts(portrait, screenOn, target, active);
    }
}
