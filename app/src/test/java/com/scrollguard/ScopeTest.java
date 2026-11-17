package com.scrollguard;

import android.app.Application;
import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowPackageManager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

/**
 * M6：只锁 App 和锁全局，两种口径各算哪些时候。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class ScopeTest {

    private Application app;

    @Before
    public void setUp() {
        app = RuntimeEnvironment.getApplication();
        Prefs.wipe(app);
    }

    @Test
    public void defaultsToAppOnly() {
        assertEquals(Scope.APP, Prefs.scope(app));
        assertFalse(Scope.isGlobal(app));
        assertEquals(Scope.LABEL_APP, Scope.label(app));
    }

    @Test
    public void appModeNeedsPortraitTargetAndActivity() {
        assertTrue(Scope.counts(app, Scope.APP, true, true, Prefs.DOUYIN, true, true));
        assertFalse("横屏不算",
                Scope.counts(app, Scope.APP, false, true, Prefs.DOUYIN, true, true));
        assertFalse("别的 App 不算",
                Scope.counts(app, Scope.APP, true, true, "com.tencent.mm", false, true));
        assertFalse("目标关掉不算",
                Scope.counts(app, Scope.APP, true, true, Prefs.DOUYIN, false, true));
        assertFalse("熄屏不算",
                Scope.counts(app, Scope.APP, true, false, Prefs.DOUYIN, true, true));
        assertFalse("没动静不算",
                Scope.counts(app, Scope.APP, true, true, Prefs.DOUYIN, true, false));
    }

    @Test
    public void globalModeCountsAnyAppInAnyOrientation() {
        assertTrue("全局模式横屏也算",
                Scope.counts(app, Scope.GLOBAL, false, true, "com.tencent.mm", false, true));
        assertTrue(Scope.counts(app, Scope.GLOBAL, true, true, Prefs.DOUYIN, false, true));
        assertFalse("熄屏还是不算",
                Scope.counts(app, Scope.GLOBAL, true, false, "com.tencent.mm", true, true));
        assertFalse("没动静还是不算",
                Scope.counts(app, Scope.GLOBAL, true, true, "com.tencent.mm", true, false));
    }

    @Test
    public void globalModeSkipsOwnAppAndSystemWindows() {
        assertFalse("自己的界面不算",
                Scope.counts(app, Scope.GLOBAL, true, true, app.getPackageName(), true, true));
        assertFalse("系统界面不算",
                Scope.counts(app, Scope.GLOBAL, true, true, "com.android.systemui", true, true));
        assertFalse(Scope.counts(app, Scope.GLOBAL, true, true, "android", true, true));
        assertFalse(Scope.counts(app, Scope.GLOBAL, true, true, "", true, true));
        assertFalse(Scope.counts(app, Scope.GLOBAL, true, true, null, true, true));
    }

    @Test
    public void globalModeSkipsTheLauncher() {
        ComponentName home = new ComponentName("com.example.launcher", "com.example.launcher.Home");
        ShadowPackageManager spm = shadowOf(app.getPackageManager());
        spm.addActivityIfNotPresent(home);
        IntentFilter filter = new IntentFilter(Intent.ACTION_MAIN);
        filter.addCategory(Intent.CATEGORY_HOME);
        filter.addCategory(Intent.CATEGORY_DEFAULT);
        spm.addIntentFilterForActivity(home, filter);

        assertFalse("桌面上不算",
                Scope.counts(app, Scope.GLOBAL, true, true, home.getPackageName(), true, true));
        assertTrue("别的应用还算",
                Scope.counts(app, Scope.GLOBAL, true, true, "com.tencent.mm", true, true));
    }

    @Test
    public void normalizeFallsBackToApp() {
        assertEquals(Scope.GLOBAL, Scope.normalize(Scope.GLOBAL));
        assertEquals(Scope.APP, Scope.normalize(Scope.APP));
        assertEquals(Scope.APP, Scope.normalize("nonsense"));
        assertEquals(Scope.APP, Scope.normalize(null));
    }
}
