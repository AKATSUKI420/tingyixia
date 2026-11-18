package com.scrollguard;

import android.app.Application;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * M1：怎么从"窗口变了/滑了一下"看出"现在在刷短视频"。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class ShieldTest {

    private Application app;
    private GuardState st;

    @Before
    public void setUp() {
        app = RuntimeEnvironment.getApplication();
        Prefs.wipe(app);
        st = GuardState.get();
        st.fgPackage = "";
        st.lastScrollAt = 0L;
    }

    @Test
    public void windowChangeRecordsWhoIsInFront() {
        Shield.onWindow(st, Prefs.DOUYIN);

        assertEquals(Prefs.DOUYIN, st.fgPackage);
        assertTrue("刚打开也算有动静", st.scrolledWithin(1000L));
    }

    @Test
    public void systemWindowsAreNotARealSwitch() {
        st.fgPackage = Prefs.DOUYIN;

        Shield.onWindow(st, "com.android.systemui");
        Shield.onWindow(st, "android");
        Shield.onWindow(st, "com.android.permissioncontroller");

        assertEquals("下拉通知栏不该把计时清掉", Prefs.DOUYIN, st.fgPackage);
    }

    @Test
    public void ourOwnOverlayWindowIsNotARealSwitch() {
        st.fgPackage = Prefs.BILI;

        Shield.onWindow(st, app.getPackageName());

        assertEquals("遮罩自己的窗口不该把前台换成自己", Prefs.BILI, st.fgPackage);
    }

    @Test
    public void scrollInATargetAppCounts() {
        assertFalse(st.scrolledWithin(1000L));

        Shield.onScroll(st, Prefs.targets(app), Prefs.BILI, false);

        assertTrue(st.scrolledWithin(1000L));
    }

    @Test
    public void scrollElsewhereIsIgnored() {
        Shield.onScroll(st, Prefs.targets(app), "com.tencent.mm", false);

        assertFalse(st.scrolledWithin(1000L));
    }

    @Test
    public void scrollAnywhereCountsInGlobalMode() {
        Shield.onScroll(st, Prefs.targets(app), "com.tencent.mm", true);

        assertTrue("全局模式下别的 App 滑动也算动静", st.scrolledWithin(1000L));
    }

    @Test
    public void systemScrollDoesNotCountEvenInGlobalMode() {
        Shield.onScroll(st, Prefs.targets(app), "com.android.systemui", true);

        assertFalse(st.scrolledWithin(1000L));
    }

    @Test
    public void missingPackageChangesNothing() {
        st.fgPackage = Prefs.DOUYIN;

        Shield.onWindow(st, null);
        Shield.onScroll(st, Prefs.targets(app), null, false);

        assertEquals(Prefs.DOUYIN, st.fgPackage);
        assertFalse(st.scrolledWithin(1000L));
    }

    @Test
    public void disconnectedServiceForgetsWhereYouAre() {
        st.fgPackage = Prefs.DOUYIN;

        Shield.onGone(st);

        assertEquals("", st.fgPackage);
    }

    @Test
    public void noiseListIsExact() {
        assertTrue(Shield.isNoise("com.android.systemui"));
        assertFalse(Shield.isNoise("com.ss.android.ugc.aweme"));
        assertTrue("空包名当噪音", Shield.isNoise(null));
    }
}
