package com.scrollguard;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.view.accessibility.AccessibilityEvent;

/**
 * 只看两件事：现在前台是哪个应用、有没有滑动。别的都不碰。
 */
public class ShieldService extends AccessibilityService {

    /** 这些包名弹出来的窗口不算"切走了" */
    private static final String[] NOISE = {
            "android",
            "com.android.systemui",
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller",
            "com.android.packageinstaller",
            "com.google.android.packageinstaller"
    };

    private static boolean isNoise(String pkg) {
        for (String n : NOISE) {
            if (n.equals(pkg)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        GuardState.get().lastScrollAt = 0L;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e == null) {
            return;
        }
        CharSequence pkgCs = e.getPackageName();
        if (pkgCs == null) {
            return;
        }
        String pkg = pkgCs.toString();
        GuardState st = GuardState.get();

        if (e.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (isNoise(pkg)) {
                return;
            }
            st.fgPackage = pkg;
            st.touchScroll();
        } else if (e.getEventType() == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            if (Prefs.targets(this).contains(pkg)) {
                st.touchScroll();
            }
        }
    }

    @Override
    public void onInterrupt() {
        // 不用做什么，服务断了下一次事件会自己回来
    }

    @Override
    public boolean onUnbind(Intent intent) {
        GuardState.get().fgPackage = "";
        return super.onUnbind(intent);
    }
}
