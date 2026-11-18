package com.scrollguard;

import android.app.Application;
import android.content.Intent;
import android.view.View;
import android.widget.TextView;

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

    private static String titleOf(GuardService s) {
        try {
            Field f = LockOverlay.class.getDeclaredField("view");
            f.setAccessible(true);
            View v = (View) f.get(overlayOf(s));
            return ((TextView) v.findViewById(R.id.lock_title)).getText().toString();
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

    private static View viewOf(LockOverlay o) {
        try {
            Field f = LockOverlay.class.getDeclaredField("view");
            f.setAccessible(true);
            return (View) f.get(o);
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

    // ---------- 问题2：只遮罩 App / 全局遮罩 ----------

    @Test
    public void globalModeCountsAnyAppEvenInLandscape() {
        Prefs.setScope(app, Scope.GLOBAL);
        RuntimeEnvironment.setQualifiers("+land");
        GuardState.get().fgPackage = "com.tencent.mm";
        GuardService svc = start();

        idle(61_000);

        assertTrue("全局模式横屏也该拦", overlayOf(svc).isShowing());
        assertEquals(1, Prefs.todayLocks(app));
        assertEquals(Text.lockTitle(1, true), titleOf(svc));
    }

    @Test
    public void globalModeSkipsOurOwnApp() {
        Prefs.setScope(app, Scope.GLOBAL);
        GuardState.get().fgPackage = app.getPackageName();
        GuardService svc = start();

        idle(61_000);

        assertFalse("在自己首页上不该计时", GuardState.get().locked);
        assertFalse(overlayOf(svc).isShowing());
        assertEquals(0, Prefs.todayLocks(app));
    }

    @Test
    public void globalModeDoesNotNeedTheTargetSwitches() {
        Prefs.setScope(app, Scope.GLOBAL);
        Prefs.setTarget(app, Prefs.DOUYIN, false);
        Prefs.setTarget(app, Prefs.BILI, false);
        GuardState.get().fgPackage = "com.tencent.mm";
        GuardService svc = start();

        idle(61_000);

        assertTrue("全局模式不看那两个开关", overlayOf(svc).isShowing());
        assertEquals(1, Prefs.todayLocks(app));
    }

    @Test
    public void globalModeIgnoresSystemWindows() {
        Prefs.setScope(app, Scope.GLOBAL);
        GuardState.get().fgPackage = "com.android.systemui";
        GuardService svc = start();

        idle(61_000);

        assertFalse(overlayOf(svc).isShowing());
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

    @Test
    public void appOnlyMaskHidesWhenLeavingAndCoversAgainWhenBack() {
        GuardState st = GuardState.get();
        st.fgPackage = Prefs.DOUYIN;
        GuardService svc = start();

        idle(61_000);
        LockOverlay overlay = overlayOf(svc);
        assertTrue("满一分钟该拦", overlay.isCovering());
        assertEquals(1, Prefs.todayLocks(app));

        st.fgPackage = "com.tencent.mm";
        idle(1_000);

        assertTrue("锁本身还在", overlay.isShowing());
        assertFalse("切走遮罩该收起来", overlay.isCovering());
        assertEquals("切走不该算新的一次拦截", 1, Prefs.todayLocks(app));

        st.fgPackage = Prefs.DOUYIN;
        idle(1_000);

        assertTrue("回到目标 App 该再盖住", overlay.isCovering());
        assertEquals("回来也不该重复记拦截", 1, Prefs.todayLocks(app));
    }

    @Test
    public void appOnlyMaskHidesForHomeButNotForSystemUi() {
        GuardState st = GuardState.get();
        st.fgPackage = Prefs.DOUYIN;
        GuardService svc = start();

        idle(61_000);
        LockOverlay overlay = overlayOf(svc);
        assertTrue(overlay.isCovering());

        Shield.onWindow(st, "com.example.launcher");
        idle(1_000);
        assertFalse("按 Home 该收起遮罩", overlay.isCovering());

        Shield.onWindow(st, Prefs.DOUYIN);
        idle(1_000);
        assertTrue("回到目标 App 该再盖住", overlay.isCovering());

        Shield.onWindow(st, "com.android.systemui");
        idle(1_000);
        assertTrue("下拉通知栏 / 最近任务不算切走", overlay.isCovering());
    }

    /**
     * 按 Home / 切最近任务时，遮罩窗口会先失去焦点，而无障碍的前台包名
     * 可能还没来得及更新。不能干等它，先把遮罩摘掉；等目标 App 真正
     * 再收到窗口事件（fgSeq 变了）才盖回去。
     */
    @Test
    public void losingWindowFocusHidesAndReturningCoversAgain() {
        GuardState st = GuardState.get();
        st.fgPackage = Prefs.DOUYIN;
        GuardService svc = start();

        idle(61_000);
        LockOverlay overlay = overlayOf(svc);
        assertTrue(overlay.isCovering());

        View root = viewOf(overlay);
        root.onWindowFocusChanged(true);
        root.onWindowFocusChanged(false);
        idle(1_000);

        assertTrue("锁还在", overlay.isShowing());
        assertFalse("按 Home / 最近任务后先别再挡着", overlay.isCovering());

        Shield.onWindow(st, Prefs.DOUYIN);
        idle(1_000);

        assertTrue("真的回到抖音再盖", overlay.isCovering());
        assertEquals("回来不该重复记拦截", 1, Prefs.todayLocks(app));
    }

    @Test
    public void previewStaysUpWhenWindowFocusIsLost() {
        GuardState st = GuardState.get();
        st.fgPackage = "com.tencent.mm";

        Intent i = new Intent(app, GuardService.class).setAction(GuardService.ACTION_TEST);
        ServiceController<GuardService> c = Robolectric.buildService(GuardService.class, i);
        GuardService svc = c.create().get();
        c.startCommand(0, 0);
        idle(200);

        LockOverlay overlay = overlayOf(svc);
        assertTrue(overlay.isCovering());

        View root = viewOf(overlay);
        root.onWindowFocusChanged(true);
        root.onWindowFocusChanged(false);
        idle(1_000);

        assertTrue("试一下的预览不该被焦点变化收走", overlay.isCovering());
    }

    /**
     * 遮罩盖上去时，无障碍会收到一条 com.scrollguard 的窗口事件。
     * 如果把它当成“切到了别的 App”，遮罩就会收起；收起后 B 站又变回前台，
     * 遮罩再盖上——一秒一次，就是用户看到的闪屏。
     */
    @Test
    public void appOnlyMaskStaysUpWhenOurOwnOverlayWindowAppears() {
        GuardState st = GuardState.get();
        st.fgPackage = Prefs.DOUYIN;
        GuardService svc = start();

        idle(61_000);
        LockOverlay overlay = overlayOf(svc);
        assertTrue("满一分钟该拦", overlay.isCovering());

        Shield.onWindow(st, app.getPackageName());
        idle(3_000);

        assertTrue("遮罩自己的窗口不该让它收起", overlay.isShowing());
        assertTrue("遮罩应该继续盖着", overlay.isCovering());
        assertEquals("不该因为自己的窗口重复记拦截", 1, Prefs.todayLocks(app));
    }

    @Test
    public void globalMaskStaysWhenLeavingTheApp() {
        Prefs.setScope(app, Scope.GLOBAL);
        GuardState st = GuardState.get();
        st.fgPackage = Prefs.DOUYIN;
        GuardService svc = start();

        idle(61_000);
        LockOverlay overlay = overlayOf(svc);
        assertTrue(overlay.isCovering());

        st.fgPackage = "com.tencent.mm";
        idle(1_000);

        assertTrue("全局遮罩不该跟着切走", overlay.isShowing());
        assertTrue(overlay.isCovering());
    }

    @Test
    public void previewStaysUpEvenOutsideTheTargetApps() {
        GuardState st = GuardState.get();
        st.fgPackage = "com.tencent.mm";

        Intent i = new Intent(app, GuardService.class).setAction(GuardService.ACTION_TEST);
        ServiceController<GuardService> c = Robolectric.buildService(GuardService.class, i);
        GuardService svc = c.create().get();
        c.startCommand(0, 0);

        assertTrue("试一下要能看见", overlayOf(svc).isCovering());

        idle(2_000);

        assertTrue("试一下不该被只遮罩 App 的规则收走", overlayOf(svc).isCovering());
    }

    // ---------- 问题3：锁一会儿自己解开 ----------

    /** 设了自动解开：到点自己走人，计时也从头开始，不然一解锁就又满了 */
    @Test
    public void autoUnlockLetsGoAndResetsTheCount() {
        Prefs.setAutoUnlockMin(app, 2L);
        GuardState.get().fgPackage = Prefs.DOUYIN;
        GuardService svc = start();

        idle(61_000);
        LockOverlay overlay = overlayOf(svc);
        assertTrue("满一分钟该拦", overlay.isShowing());

        idle(59_000);
        assertTrue("两分钟还没到，不该放人", overlay.isShowing());

        idle(61_000);

        assertFalse("到点了应该自己解开", overlay.isShowing());
        assertFalse(GuardState.get().locked);
        assertTrue("自动解开后也该从头算", GuardState.get().countedMs < 3000L);
    }

    /** 没设自动解开就一直盖着，没人替用户放行 */
    @Test
    public void withoutAutoUnlockItStaysUp() {
        GuardState.get().fgPackage = Prefs.DOUYIN;
        GuardService svc = start();

        idle(61_000);
        LockOverlay overlay = overlayOf(svc);
        assertTrue(overlay.isShowing());

        idle(5 * 60_000);

        assertTrue("没设自动解开就不许自己走", overlay.isShowing());
        assertTrue(GuardState.get().locked);
    }
}
