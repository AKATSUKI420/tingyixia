package com.scrollguard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * M5 · 6 位密码本身。对应 PRD 的 F 组。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class PinTest {

    private Context c() {
        return RuntimeEnvironment.getApplication();
    }

    @Test
    public void wellFormedRules() {
        assertTrue(Pin.wellFormed("123456"));
        assertTrue(Pin.wellFormed("000000"));

        assertFalse("太短", Pin.wellFormed("12345"));
        assertFalse("太长", Pin.wellFormed("1234567"));
        assertFalse("有字母", Pin.wellFormed("12a456"));
        assertFalse("有空格", Pin.wellFormed("12 456"));
        assertFalse("null", Pin.wellFormed(null));
        assertFalse("空串", Pin.wellFormed(""));
    }

    @Test
    public void saveThenCheck() {
        Pin.save(c(), "246810");
        assertTrue(Prefs.hasPin(c()));
        assertTrue(Pin.check(c(), "246810"));
        assertFalse(Pin.check(c(), "246811"));
        assertFalse(Pin.check(c(), "024681"));
        assertFalse(Pin.check(c(), ""));
        assertFalse(Pin.check(c(), null));
    }

    /** 没设过密码的时候，什么都验不过 */
    @Test
    public void noPinNoPass() {
        assertFalse(Prefs.hasPin(c()));
        assertFalse(Pin.check(c(), "123456"));
    }

    /** 只存哈希，不存原文 */
    @Test
    public void neverStoresPlaintext() {
        Pin.save(c(), "135790");
        assertNotEquals("135790", Prefs.pinHash(c()));
        assertFalse(Prefs.pinHash(c()).contains("135790"));
        assertFalse(Prefs.pinSalt(c()).contains("135790"));
    }

    /** 同一个密码存两次，盐不同、哈希也不同（防彩虹表） */
    @Test
    public void saltIsRandom() {
        Pin.save(c(), "112233");
        String h1 = Prefs.pinHash(c());
        String s1 = Prefs.pinSalt(c());

        Pin.save(c(), "112233");
        String h2 = Prefs.pinHash(c());
        String s2 = Prefs.pinSalt(c());

        assertNotEquals(s1, s2);
        assertNotEquals(h1, h2);
        assertTrue("新的那份照样验得过", Pin.check(c(), "112233"));
    }

    /** 盐被写坏成不是 base64 的时候，应该判不过而不是崩 */
    @Test
    public void corruptSaltFailsClosed() {
        Prefs.setPin(c(), "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=", "not-base64!!!");
        assertFalse(Pin.check(c(), "123456"));
    }

    /** 哈希丢了也应该判不过 */
    @Test
    public void missingHashFailsClosed() {
        Prefs.setPin(c(), "", "");
        assertFalse(Pin.check(c(), "123456"));
    }

    /** 改密码之后，旧的不能再用 */
    @Test
    public void changeMakesOldPinInvalid() {
        Pin.save(c(), "654321");
        assertTrue(Pin.check(c(), "654321"));

        Pin.save(c(), "111222");
        assertFalse(Pin.check(c(), "654321"));
        assertTrue(Pin.check(c(), "111222"));
        assertEquals("改了密码不影响别的设置", 10L, Prefs.limitMin(c()));
    }
}
