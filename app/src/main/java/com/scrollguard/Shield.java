package com.scrollguard;

import java.util.Set;

/**
 * 无障碍事件怎么变成"现在在刷"——判断全在这里，不碰系统，方便测。
 */
final class Shield {

    /** 这些包名弹出来的窗口不算"切走了" */
    private static final String[] NOISE = {
            "android",
            "com.android.systemui",
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller",
            "com.android.packageinstaller",
            "com.google.android.packageinstaller"
    };

    private Shield() {
    }

    static boolean isNoise(String pkg) {
        if (pkg == null) {
            return true;
        }
        for (String n : NOISE) {
            if (n.equals(pkg)) {
                return true;
            }
        }
        return false;
    }

    /** 窗口变了：记下前台是谁，顺便当作"刚有动静" */
    static void onWindow(GuardState st, String pkg) {
        if (isNoise(pkg)) {
            return;
        }
        st.fgPackage = pkg;
        st.touchScroll();
    }

    /** 滑了一下：App 范围只认目标应用；全局范围除了系统界面都算动静 */
    static void onScroll(GuardState st, Set<String> targets, String pkg, boolean global) {
        if (pkg == null) {
            return;
        }
        if (global ? !isNoise(pkg) : targets != null && targets.contains(pkg)) {
            st.touchScroll();
        }
    }

    /** 无障碍断了，就当不知道现在在哪 */
    static void onGone(GuardState st) {
        st.fgPackage = "";
    }
}
