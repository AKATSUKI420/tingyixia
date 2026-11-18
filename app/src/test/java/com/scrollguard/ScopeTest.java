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
 * 只遮罩 App 的口径：竖屏 + 目标应用 + 屏幕亮着 + 有动静。
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
    public void appOnlyIsTheOnlyModeLeft() {
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
    public void oldGlobalValueFallsBackToApp() {
        assertEquals(Scope.APP, Scope.normalize("global"));
        assertEquals(Scope.APP, Scope.normalize(Scope.APP));
        assertEquals(Scope.APP, Scope.normalize("nonsense"));
        assertEquals(Scope.APP, Scope.normalize(null));
    }
}
