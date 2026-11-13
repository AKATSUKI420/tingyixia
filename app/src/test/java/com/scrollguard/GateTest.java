package com.scrollguard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * M5 · 密码守住开关。对应 PRD 的 F1–F4。
 * 这里验的是规则本身，弹框由 MainActivity 负责。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class GateTest {

    private Context c() {
        return RuntimeEnvironment.getApplication();
    }

    /** 打开不需要密码 */
    @Test
    public void enableNeedsNoPin() {
        assertFalse(Prefs.enabled(c()));
        Gate.enable(c());
        assertTrue(Prefs.enabled(c()));
    }

    /** F1：密码不对，关不掉 */
    @Test
    public void wrongPinCannotDisable() {
        Gate.enable(c());
        Pin.save(c(), "246810");

        assertFalse(Gate.disable(c(), "000000"));
        assertTrue("防护还开着", Prefs.enabled(c()));

        assertFalse(Gate.disable(c(), ""));
        assertFalse(Gate.disable(c(), null));
        assertTrue(Prefs.enabled(c()));
    }

    /** F2：密码对了才关得掉 */
    @Test
    public void rightPinDisables() {
        Gate.enable(c());
        Pin.save(c(), "246810");

        assertTrue(Gate.disable(c(), "246810"));
        assertFalse(Prefs.enabled(c()));
    }

    /** F3：换密码要先验旧密码 */
    @Test
    public void changePinNeedsOldPin() {
        Pin.save(c(), "246810");

        assertFalse(Gate.changePin(c(), "999999", "333444"));
        assertTrue("旧密码没变", Pin.check(c(), "246810"));
        assertFalse(Pin.check(c(), "333444"));

        assertTrue(Gate.changePin(c(), "246810", "333444"));
        assertTrue(Pin.check(c(), "333444"));
        assertFalse(Pin.check(c(), "246810"));
    }

    /** 新密码不合规也不改 */
    @Test
    public void changeRejectsBadNewPin() {
        Pin.save(c(), "246810");
        assertFalse(Gate.changePin(c(), "246810", "12345"));
        assertFalse(Gate.changePin(c(), "246810", "abcdef"));
        assertTrue("还是老密码", Pin.check(c(), "246810"));
    }

    /** F4：忘掉密码＝清空，防护一起关掉 */
    @Test
    public void wipeClearsAll() {
        Gate.enable(c());
        Pin.save(c(), "246810");
        Prefs.addTodayMs(c(), 300000L);

        Gate.wipe(c());

        assertFalse(Prefs.enabled(c()));
        assertFalse(Prefs.hasPin(c()));
        assertEquals(0L, Prefs.todayMs(c()));
        assertFalse(Gate.disable(c(), "246810"));
    }

    /** 开关的规则不该被别的东西顺手改掉 */
    @Test
    public void disableKeepsOtherSettings() {
        Gate.enable(c());
        Pin.save(c(), "246810");
        Prefs.setLimitMin(c(), 30L);
        Prefs.setTarget(c(), Prefs.BILI, false);

        assertTrue(Gate.disable(c(), "246810"));

        assertEquals(30L, Prefs.limitMin(c()));
        assertFalse(Prefs.targets(c()).contains(Prefs.BILI));
        assertTrue("密码还在，还能再开", Pin.check(c(), "246810"));
    }
}
