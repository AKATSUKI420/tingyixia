package com.scrollguard;

import android.app.Application;
import android.view.KeyEvent;
import android.view.View;
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
        unlocked = false;
        overlay.show(limitMin, () -> unlocked = true);
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

    @Test
    public void showsLayerWithTheReason() {
        show(10);

        assertTrue(overlay.isShowing());
        TextView title = view(overlay).findViewById(R.id.lock_title);
        assertEquals(Text.lockTitle(10), title.getText().toString());
        assertNotNull(view(overlay).findViewById(R.id.pad));
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
}
