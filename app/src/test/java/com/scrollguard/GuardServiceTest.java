package com.scrollguard;

import android.app.Application;
import android.content.Intent;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.shadows.ShadowSettings;

import java.lang.reflect.Field;
import java.time.Duration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

/**
 * M2/M3：计时服务会不会真的在"连着刷满"时把遮罩盖上去，以及各种不该算的情况。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class GuardServiceTest {

    private Application app;

    @Before
    public void setUp() {
        app = RuntimeEnvironment.getApplication();
        Prefs.wipe(app);
        Pin.save(app, "123456");
        Prefs.setEnabled(app, true);
        Prefs.setLimitMin(app, 1);
        ShadowSettings.setCanDrawOverlays(true);
        RuntimeEnvironment.setQualifiers("+port");

        GuardState st = GuardState.get();
        st.fgPackage = "";
        st.countedMs = 0L;
        st.locked = false;
        st.lastScrollAt = 0L;
        st.serviceRunning = false;
    }

    private static void idle(long ms) {
        ShadowLooper.shadowMainLooper().idleFor(Duration.ofMillis(ms));
    }

    private GuardService start() {
        ServiceController<GuardService> c = Robolectric.buildService(GuardService.class);
        c.create();
        return c.get();
    }

    private static LockOverlay overlayOf(GuardService s) {
        try {
            Field f = GuardService.class.getDeclaredField("overlay");
            f.setAccessible(true);
            return (LockOverlay) f.get(s);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    public void locksAfterTheFullMinuteOfDouyin() {
        GuardState.get().fgPackage = Prefs.DOUYIN;
        GuardService svc = start();

        idle(30_000);
        assertEquals("30 秒还不该拦", 0, Prefs.todayLocks(app));
        assertTrue(GuardState.get().countedMs >= 29_000);

        idle(31_000);

        assertTrue("满一分钟应该拦", GuardState.get().locked);
        assertEquals(1, Prefs.todayLocks(app));
        assertTrue("遮罩没盖上去", overlayOf(svc).isShowing());
        assertTrue("今天该记上了", Prefs.todayMs(app) >= 55_000);
    }

    @Test
    public void unlockingClearsTheCount() {
        GuardState.get().fgPackage = Prefs.DOUYIN;
        GuardService svc = start();
        idle(61_000);
        assertTrue(overlayOf(svc).isShowing());

        LockOverlay overlay = overlayOf(svc);
        PinPadView pad = field(overlay, "pad");
        TestPad.type(pad, "123456");
        Wait.until("解不开", () -> !overlay.isShowing());

        assertFalse(GuardState.get().locked);
        assertTrue("解锁后从头算", GuardState.get().countedMs < 3000L);
    }

    private static PinPadView field(LockOverlay o, String name) {
        try {
            Field f = LockOverlay.class.getDeclaredField(name);
            f.setAccessible(true);
            return (PinPadView) f.get(o);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    public void landscapeDoesNotCount() {
        RuntimeEnvironment.setQualifiers("+land");
        GuardState.get().fgPackage = Prefs.DOUYIN;
        start();

        idle(61_000);

        assertFalse("横过来不算", GuardState.get().locked);
        assertEquals(0, Prefs.todayLocks(app));
        assertEquals(0L, GuardState.get().countedMs);
    }

    @Test
    public void otherAppsDoNotCount() {
        GuardState.get().fgPackage = "com.tencent.mm";
        start();

        idle(61_000);

        assertFalse(GuardState.get().locked);
        assertEquals(0, Prefs.todayLocks(app));
    }

    /** v1.1：切到别的 App 只是暂停，攒下的时间不清零 */
    @Test
    public void switchingAwayKeepsTheCount() {
        GuardState.get().fgPackage = Prefs.DOUYIN;
        start();
        idle(30_000);
        long kept = GuardState.get().countedMs;
        assertTrue(kept >= 29_000L);

        GuardState.get().fgPackage = "";
        idle(10_000);

        assertEquals("切走不该清零", kept, GuardState.get().countedMs);
        assertFalse(GuardState.get().locked);
    }

    /** v1.1：切走再回来，接着攒，攒满总时长照样拦 */
    @Test
    public void comingBackStillLocksAtTheTotal() {
        GuardState.get().fgPackage = Prefs.DOUYIN;
        GuardService svc = start();

        idle(30_000);
        GuardState.get().fgPackage = "";
        idle(10_000);
        assertFalse("才攒了 30 秒，不该拦", GuardState.get().locked);

        GuardState.get().fgPackage = Prefs.DOUYIN;
        idle(31_000);

        assertTrue("回来接着攒满一分钟就该拦", GuardState.get().locked);
        assertEquals(1, Prefs.todayLocks(app));
        assertTrue(overlayOf(svc).isShowing());
    }

    @Test
    public void biliCountsToo() {
        GuardState.get().fgPackage = Prefs.BILI;
        GuardService svc = start();

        idle(61_000);

        assertTrue(overlayOf(svc).isShowing());
    }

    @Test
    public void disabledServiceStopsItself() {
        Prefs.setEnabled(app, false);
        ServiceController<GuardService> c = Robolectric.buildService(GuardService.class);

        c.create();
        c.startCommand(0, 0);

        assertTrue("关着的时候不该常驻", shadowOf(c.get()).isStoppedBySelf());
    }

    @Test
    public void testActionShowsTheLayerRightAway() {
        Intent i = new Intent(app, GuardService.class).setAction(GuardService.ACTION_TEST);
        ServiceController<GuardService> c = Robolectric.buildService(GuardService.class, i);

        GuardService svc = c.create().get();
        c.startCommand(0, 0);

        assertTrue(overlayOf(svc).isShowing());
        assertFalse("试一下不算真拦", GuardState.get().locked);
        assertEquals(0, Prefs.todayLocks(app));
    }

    @Test
    public void startsRunningAndSchedulesTheReviveJob() {
        start();

        assertTrue(GuardState.get().serviceRunning);
        android.app.job.JobScheduler js = (android.app.job.JobScheduler)
                app.getSystemService(android.content.Context.JOB_SCHEDULER_SERVICE);
        assertEquals(1, js.getAllPendingJobs().size());
    }
}
