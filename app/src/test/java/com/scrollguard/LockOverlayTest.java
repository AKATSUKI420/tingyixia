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
     * 磨砂玻璃浮层：窗口半透明合成 + 让系统在背后做模糊，
     * 但 alpha 仍保持 1，免得丢触摸目标。
     */
    @Test
    public void windowIsTranslucentAndAsksForBlur() {
        show(10);

        WindowManager.LayoutParams lp = windowParams(overlay);
        assertEquals(PixelFormat.TRANSLUCENT, lp.format);
        assertTrue("该向系统要背后模糊",
                (lp.flags & WindowManager.LayoutParams.FLAG_BLUR_BEHIND) != 0);
        assertEquals(0, lp.windowAnimations);
        assertEquals("窗口 alpha 仍要是 1，不然会丢触摸目标",
                1f, lp.alpha, 0.001f);
    }

    @Test
    public void contentAnimatesEvenWhenSystemAnimationsAreOff() {
        android.provider.Settings.Global.putFloat(app.getContentResolver(),
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 0f);

        show(10);
        WindowManager.LayoutParams lp = windowParams(overlay);
        View content = view(overlay).findViewById(R.id.lock_content);
        assertNotNull("遮罩内容层不见了", content);
        assertEquals("窗口必须始终完全不透明", 1f, lp.alpha, 0.001f);
        assertEquals("第一帧内容还没出现", 0f, content.getAlpha(), 0.01f);

        ShadowLooper.shadowMainLooper().idleFor(Duration.ofMillis(80));
        assertTrue("内容应该正在进场，而不是一帧跳完：" + content.getAlpha(),
                content.getAlpha() > 0f && content.getAlpha() < 1f);
        assertEquals("窗口不能跟着变半透明", 1f, lp.alpha, 0.001f);

        ShadowLooper.shadowMainLooper().idleFor(Duration.ofMillis(500));
        assertEquals("进场结束内容应该完全显示", 1f, content.getAlpha(), 0.01f);
        assertEquals("窗口始终保持不透明", 1f, lp.alpha, 0.001f);
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
    public void centerMotionGrowsFromTheMiddleAndShrinksBack() {
        Prefs.setMotion(app, Motion.CENTER);
        show(10);

        View content = view(overlay).findViewById(R.id.lock_content);
        assertNotNull("遮罩内容层不见了", content);
        assertEquals("应该从缩小状态开始", 0.55f, content.getScaleX(), 0.02f);

        ShadowLooper.shadowMainLooper().idleFor(Duration.ofMillis(500));
        assertEquals("进场后应该恢复到原大小", 1f, content.getScaleX(), 0.01f);

        overlay.setCovering(false);
        ShadowLooper.shadowMainLooper().idleFor(Duration.ofMillis(90));
        assertTrue("退出应该往中心缩：" + content.getScaleX(), content.getScaleX() < 1f);

        ShadowLooper.shadowMainLooper().idleFor(Duration.ofMillis(500));
        assertFalse("缩完该从 WindowManager 摘掉", overlay.isAttached());
    }

    @Test
    public void showsLayerWithTheReason() {
        show(10);

        assertTrue(overlay.isShowing());
        TextView title = view(overlay).findViewById(R.id.lock_title);
        assertEquals("请输入密码解锁", title.getText().toString());
        TextView time = view(overlay).findViewById(R.id.lock_time);
        assertEquals("10:00", time.getText().toString());
        assertNotNull(view(overlay).findViewById(R.id.pad));
    }

    @Test
    public void lockScreenUsesMinimalWhiteLayout() {
        show(10);
        View v = view(overlay);

        TextView time = v.findViewById(R.id.lock_time);
        assertNotNull("锁屏该有连刷时长", time);
        assertEquals(Ui.sp(app, 60), time.getTextSize(), 1f);

        TextView title = v.findViewById(R.id.lock_title);
        assertEquals(Ui.sp(app, 20), title.getTextSize(), 1f);
        assertEquals("锁屏标题该是正文黑", app.getColor(R.color.text),
                title.getCurrentTextColor());

        TextView kicker = v.findViewById(R.id.lock_kicker);
        assertEquals(Ui.sp(app, 11), kicker.getTextSize(), 1f);

        TextView footer = v.findViewById(R.id.lock_footer);
        assertEquals(Ui.sp(app, 13), footer.getTextSize(), 1f);

        assertNotNull("锁屏该有忘记密码", v.findViewById(R.id.lock_forgot));
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

    @Test
    public void quickRecoverKeepsTheWindowTouchable() {
        show(10);
        assertTrue(overlay.isCovering());

        overlay.setCovering(false);
        // 不等退场动画跑完就回来：某些系统在窗口被设过
        // NOT_TOUCHABLE 之后，不会再给同一个窗口恢复输入通道。
        overlay.setCovering(true);
        ShadowLooper.shadowMainLooper().idleFor(Duration.ofMillis(400));

        assertTrue("重新盖上后要能点", overlay.isCovering());
        WindowManager.LayoutParams lp = windowParams(overlay);
        assertEquals("触摸和焦点标记都要清掉",
                0, lp.flags & (WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE));
        assertEquals("窗口必须完全不透明才有点击目标", 1f, lp.alpha, 0.001f);
    }

    @Test
    public void hidingNeverMakesTheWindowUntouchable() {
        show(10);
        overlay.setCovering(false);

        WindowManager.LayoutParams lp = windowParams(overlay);
        assertEquals("退场期间也不能设成不可触摸/不可聚焦",
                0, lp.flags & (WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE));
        assertEquals("退场期间窗口也必须保持完全不透明", 1f, lp.alpha, 0.001f);
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
