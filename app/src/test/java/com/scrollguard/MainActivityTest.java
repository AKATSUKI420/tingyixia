package com.scrollguard;

import android.app.Application;
import android.app.Activity;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

/**
 * M1/M5：首页——没密码先设密码，有密码才给看状态；状态行得说实话。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class MainActivityTest {

    private Application app;

    @Before
    public void setUp() {
        app = RuntimeEnvironment.getApplication();
        Prefs.wipe(app);
        GuardState st = GuardState.get();
        st.serviceRunning = false;
        st.countedMs = 0L;
        st.locked = false;
    }

    private MainActivity open() {
        return Robolectric.buildActivity(MainActivity.class).setup().get();
    }

    @Test
    public void withoutAPinItSendsYouToSetup() {
        open();

        Intent next = shadowOf(app).getNextStartedActivity();
        assertNotNull("没密码却让进首页", next);
        assertEquals(SetupActivity.class.getName(), next.getComponent().getClassName());
    }

    @Test
    public void withAPinThePageOpens() {
        Pin.save(app, "123456");
        MainActivity a = open();

        assertNotNull(a);
        assertTrue("首页没搭起来", hasText(a.findViewById(android.R.id.content), null));
    }

    @Test
    public void offStateSaysSo() {
        Pin.save(app, "123456");
        MainActivity a = open();

        assertTrue(hasText(a.findViewById(android.R.id.content), "现在关着，什么都不管。"));
    }

    @Test
    public void onWithoutAccessibilitySaysItCannotBlock() {
        Pin.save(app, "123456");
        Prefs.setEnabled(app, true);
        MainActivity a = open();

        assertTrue("得提醒无障碍没开",
                hasText(a.findViewById(android.R.id.content), "无障碍没开 —— 现在其实拦不住。"));
    }

    @Test
    public void onWithoutOverlayPermissionSaysItCannotCover() {
        Pin.save(app, "123456");
        Prefs.setEnabled(app, true);
        android.provider.Settings.Secure.putString(
                app.getContentResolver(),
                android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                new android.content.ComponentName(app, ShieldService.class).flattenToString());

        MainActivity a = open();

        assertTrue("得提醒悬浮窗没开",
                hasText(a.findViewById(android.R.id.content), "悬浮窗没开 —— 到点了也盖不住。"));
    }

    private static boolean hasText(View v, String what) {
        if (v == null) {
            return false;
        }
        if (v instanceof TextView) {
            CharSequence t = ((TextView) v).getText();
            if (what == null) {
                return t != null && t.length() > 0;
            }
            return what.contentEquals(t);
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                if (hasText(g.getChildAt(i), what)) {
                    return true;
                }
            }
        }
        return false;
    }
}
