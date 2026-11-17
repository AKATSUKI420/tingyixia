package com.scrollguard;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.view.accessibility.AccessibilityEvent;

/**
 * 只看两件事：现在前台是哪个应用、有没有滑动。别的都不碰。
 * 判断本身在 {@link Shield} 里。
 */
public class ShieldService extends AccessibilityService {

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
            Shield.onWindow(st, pkg);
        } else if (e.getEventType() == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            Shield.onScroll(st, Prefs.targets(this), pkg, Scope.isGlobal(this));
        }
    }

    @Override
    public void onInterrupt() {
        // 不用做什么，服务断了下一次事件会自己回来
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Shield.onGone(GuardState.get());
        return super.onUnbind(intent);
    }
}
