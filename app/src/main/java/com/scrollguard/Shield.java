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

    /** 遮罩自己的窗口，不能算作"切到了别的 App" */
    private static final String SELF_PACKAGE = "com.scrollguard";

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
        onWindow(st, pkg, SELF_PACKAGE);
    }

    static void onWindow(GuardState st, String pkg, String selfPackage) {
        // 关键：遮罩自己也是前台窗口。如果把它记成前台，服务会以为你切走了，
        // 收起遮罩；收起后抖音/B 站又变回前台，遮罩再盖上——就成了闪屏。
        if (isNoise(pkg) || pkg.equals(selfPackage)) {
            return;
        }
        st.fgPackage = pkg;
        st.fgSeq++;
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
