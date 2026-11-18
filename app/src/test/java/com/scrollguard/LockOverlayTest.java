package com.scrollguard;

import android.app.Application;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import java.lang.reflect.Field;
import java.time.Duration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * M2：遮罩本身——盖得上、写着什么、输对放人、输错不放、返回键没用。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class LockOverlayTest {

    private Application app;
    private LockOverlay overlay;
    private PinPadView pad;
    private boolean unlocked;

    @Before
    public void setUp() {
        app = RuntimeEnvironment.getApplication();
        Prefs.wipe(app);
        Pin.save(app, "123456");
        overlay = new LockOverlay(app);
    }

    private void show(int limitMin) {
        show(limitMin, AutoUnlock.OFF);
    }

    private void show(int limitMin, int autoUnlockMin) {
        unlocked = false;
        overlay.show(limitMin, autoUnlockMin, () -> unlocked = true);
        View v = view(overlay);
        assertNotNull("那一层没挂上去", v);
        pad = v.findViewById(R.id.pad);
    }

    private void showGlobal(int limitMin) {
        unlocked = false;
        overlay.show(limitMin, AutoUnlock.OFF, true, () -> unlocked = true);
        View v = view(overlay);
        assertNotNull("那一层没挂上去", v);
        pad = v.findViewById(R.id.pad);
    }

    private static View view(LockOverlay o) {
        try {
            Field f = LockOverlay.class.getDeclaredField("view");
            f.setAccessible(true);
            return (View) f.get(o);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static WindowManager.LayoutParams windowParams(LockOverlay o) {
        try {
            Field f = LockOverlay.class.getDeclaredField("lp");
            f.setAccessible(true);
            return (WindowManager.LayoutParams) f.get(o);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static String motionOf(LockOverlay o) {
        try {
            Field f = LockOverlay.class.getDeclaredField("motion");
            f.setAccessible(true);
            return (String) f.get(o);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    /**
     * 全屏遮罩本身完全不透明。第一帧如果先按 TRANSLUCENT 合成，
     * 视频上面会闪一下；这里把窗口格式和进入动画固定住。
     */
    @Test
    public void windowIsOpaqueAndHasNoWindowAnimation() {
        show(10);

        WindowManager.LayoutParams lp = windowParams(overlay);
        assertEquals(PixelFormat.OPAQUE, lp.format);
        assertEquals(0, lp.windowAnimations);
        assertTrue("窗口整体淡入由内容层控制，alpha 在 0 到 1 之间",
                lp.alpha >= 0f && lp.alpha <= 1f);
    }

    @Test
    public void windowFadesInEvenWhenSystemAnimationsAreOff() {
        android.provider.Settings.Global.putFloat(app.getContentResolver(),
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 0f);

        show(10);
        WindowManager.LayoutParams lp = windowParams(overlay);
        assertEquals("第一帧不能直接全亮", 0f, lp.alpha, 0.001f);

        ShadowLooper.shadowMainLooper().idleFor(Duration.ofMillis(80));
        assertTrue("窗口应该正在淡入，而不是一帧跳完：" + lp.alpha, lp.alpha > 0f && lp.alpha < 1f);

        ShadowLooper.shadowMainLooper().idleFor(Duration.ofMillis(500));
        assertEquals("淡入结束应该完全盖住", 1f, lp.alpha, 0.001f);
    }

    @Test
    public void speedChangesHowLongTheContentTakes() {
        Prefs.setMotionSpeed(app, 0.6f);
        show(10);
        ShadowLooper.shadowMainLooper().idleFor(Duration.ofMillis(300));
        assertTrue("慢速 0.6× 这时还没播完", overlay.hasContentAnimation());

        overlay.dismiss();
        Prefs.setMotionSpeed(app, 2f);
        show(10);
        ShadowLooper.shadowMainLooper().idleFor(Duration.ofMillis(300));
        assertFalse("快速 2× 早该播完", overlay.hasContentAnimation());
    }

    @Test
    public void showsLayerWithTheReason() {
        show(10);

        assertTrue(overlay.isShowing());
        TextView title = view(overlay).findViewById(R.id.lock_title);
        assertEquals(Text.lockTitle(10), title.getText().toString());
        assertNotNull(view(overlay).findViewById(R.id.pad));
    }

    /** 全局范围拦下来时说"用了多久"，不是"刷了多久" */
    @Test
    public void globalTitleSaysUsedNotScrolled() {
        showGlobal(10);

        TextView title = view(overlay).findViewById(R.id.lock_title);
        assertEquals("已经用了 10 分钟", title.getText().toString());
        assertEquals(Text.lockTitle(10, true), title.getText().toString());
    }

    @Test
    public void lockScreenUsesIOSLayout() {
        show(10);
        View v = view(overlay);

        assertNotNull("锁屏顶上该有个小锁图标", v.findViewById(R.id.lock_icon));

        TextView title = v.findViewById(R.id.lock_title);
        assertEquals(Ui.sp(app, 32), title.getTextSize(), 1f);
        assertEquals("浅色锁屏标题该是黑的", Color.BLACK, title.getCurrentTextColor());

        TextView kicker = v.findViewById(R.id.lock_kicker);
        assertEquals(Ui.sp(app, 13), kicker.getTextSize(), 1f);

        TextView footer = v.findViewById(R.id.lock_footer);
        assertEquals(Ui.sp(app, 13), footer.getTextSize(), 1f);
    }

    @Test
    public void footerKeepsCounting() {
        show(10);

        assertEquals("", ((TextView) view(overlay).findViewById(R.id.lock_footer)).getText().toString());

        ShadowLooper.shadowMainLooper().idleFor(Duration.ofSeconds(3));
        TextView footer = view(overlay).findViewById(R.id.lock_footer);
        assertEquals(Text.lockFooter(3), footer.getText().toString());
    }

    @Test
    public void rightPinLetsYouOutAndTellsTheService() {
        show(10);
        TestPad.type(pad, "123456");

        Wait.until("输对了还不放人", () -> !overlay.isShowing());

        assertFalse(overlay.isShowing());
        assertTrue("解锁回调没跑", unlocked);
    }

    @Test
    public void wrongPinKeepsItUp() {
        show(10);
        TestPad.type(pad, "000000");

        Wait.until("没有报错", () -> "密码不对".equals(TestPad.message(pad)));

        assertTrue("输错了不能放人", overlay.isShowing());
        assertEquals("密码不对", TestPad.message(pad));
        assertFalse(unlocked);
    }

    @Test
    public void threeWrongPinsStartACooldown() {
        show(10);

        TestPad.type(pad, "111111");
        Wait.until("第一错", () -> "密码不对".equals(TestPad.message(pad)));

        TestPad.type(pad, "111111");
        Wait.until("第二错", () -> TestPad.message(pad).contains("再错要等"));

        TestPad.type(pad, "111111");
        Wait.until("第三错", () -> TestPad.message(pad).contains("错太多次"));

        // 冷却期里就算输对也不放
        TestPad.type(pad, "123456");
        Wait.until("冷却没生效", () -> TestPad.message(pad).contains("等一下，还有"));

        assertTrue(overlay.isShowing());
        assertFalse(unlocked);
    }

    @Test
    public void backKeyIsSwallowed() {
        show(10);
        View root = view(overlay);

        assertTrue(root.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK)));
        assertTrue(root.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK)));
        assertFalse("别的键不该被吞",
                root.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_UP)));
    }

    @Test
    public void hidingKeepsTheLockAndCanCoverAgain() {
        show(10);
        assertTrue(overlay.isCovering());
        assertTrue(overlay.isAttached());

        overlay.setCovering(false);

        assertTrue("锁还在，只是不挡着别的 App", overlay.isShowing());
        assertFalse("这会儿不该再挡屏幕", overlay.isCovering());

        ShadowLooper.shadowMainLooper().idleFor(Duration.ofMillis(400));
        assertFalse("退场动画结束后窗口要从 WindowManager 摘掉", overlay.isAttached());

        overlay.setCovering(true);
        ShadowLooper.shadowMainLooper().idleFor(Duration.ofMillis(400));
        assertTrue("回来要能再盖上", overlay.isCovering());
        assertTrue(overlay.isAttached());

        TestPad.type(pad, "123456");
        Wait.until("重新盖上后输对密码也该放人", () -> !overlay.isShowing());
        assertTrue(unlocked);
    }

    /** 四种动效都要能进、能出；设置读的是落下时那一次的值。 */
    @Test
    public void everyMotionCanShowAndHide() {
        for (String motion : Motion.values()) {
            Prefs.setMotion(app, motion);
            overlay.show(10, () -> {
            });
            assertEquals("选中的动效没读出来", motion, motionOf(overlay));
            assertTrue("没盖住：" + motion, overlay.isCovering());
            assertTrue("动画没挂到内容上：" + motion, overlay.hasContentAnimation());

            overlay.setCovering(false);
            assertFalse("退场开始后就不该再挡：" + motion, overlay.isCovering());
            ShadowLooper.shadowMainLooper().idleFor(Duration.ofMillis(400));
            assertFalse("退场结束后窗口该摘掉：" + motion, overlay.isAttached());
            assertTrue("锁还在：" + motion, overlay.isShowing());

            overlay.dismiss();
            assertFalse(overlay.isShowing());
        }
    }

    @Test
    public void hiddenOverlayStopsRepaintingItsFooter() {
        show(10);
        ShadowLooper.shadowMainLooper().idleFor(Duration.ofMillis(1100));

        TextView footer = view(overlay).findViewById(R.id.lock_footer);
        String before = footer.getText().toString();
        overlay.setCovering(false);

        ShadowLooper.shadowMainLooper().idleFor(Duration.ofSeconds(2));

        assertEquals("藏起来后别再每秒重排页脚", before, footer.getText().toString());
    }

    @Test
    public void dismissRemovesIt() {
        show(10);
        overlay.dismiss();

        assertFalse(overlay.isShowing());
        assertNull(view(overlay));
    }

    @Test
    public void aSecondShowDoesNothing() {
        show(10);
        View first = view(overlay);

        overlay.show(20, () -> {
        });

        assertEquals("不该盖第二层", first, view(overlay));
    }

    // ---------- 问题3：锁一会儿自己解开 ----------

    @Test
    public void autoUnlockShowsACountdown() {
        show(10, 3);

        ShadowLooper.shadowMainLooper().idleFor(Duration.ofSeconds(2));
        String line = ((TextView) view(overlay).findViewById(R.id.lock_footer)).getText().toString();

        assertTrue("没写还剩多久：" + line, line.contains("后自己解开"));
        assertTrue("原来那句也不能丢：" + line, line.startsWith(Text.lockFooter(1))
                || line.startsWith(Text.lockFooter(2)));
    }

    /** 那行字的拼法：停了多久 · 还剩多久 */
    @Test
    public void footerLinePutsBothTogether() {
        assertEquals(Text.lockFooter(2) + " · 2 分 58 秒后自己解开",
                LockOverlay.footerLine(2, 3, 10_000L, 12_000L));
        assertEquals(Text.lockFooter(2), LockOverlay.footerLine(2, AutoUnlock.OFF, 10_000L, 12_000L));
        assertEquals(Text.lockFooter(60) + " · 45 秒后自己解开",
                LockOverlay.footerLine(60, 1, 10_000L, 10_000L + 15_000L));
    }

    @Test
    public void autoUnlockLetsYouOutAtTheDeadline() {
        show(10, 1);

        ShadowLooper.shadowMainLooper().idleFor(Duration.ofSeconds(59));
        assertTrue("还差 1 秒就放人了", overlay.isShowing());
        assertFalse(unlocked);

        ShadowLooper.shadowMainLooper().idleFor(Duration.ofSeconds(2));

        assertFalse("到点了还盖着", overlay.isShowing());
        assertTrue("自动解开没通知服务", unlocked);
    }

    @Test
    public void autoUnlockOffNeverLetsYouOut() {
        show(10, AutoUnlock.OFF);

        ShadowLooper.shadowMainLooper().idleFor(Duration.ofMinutes(10));

        assertTrue("没设自动解开，不该自己放人", overlay.isShowing());
        assertFalse(unlocked);
    }

    @Test
    public void dismissBeforeTheDeadlineIsNotAnAutoUnlock() {
        show(10, 1);
        overlay.dismiss();
        unlocked = false;

        ShadowLooper.shadowMainLooper().idleFor(Duration.ofMinutes(2));

        assertFalse(overlay.isShowing());
        assertFalse("手动关掉不该回头再调一次解锁回调", unlocked);
    }
}
