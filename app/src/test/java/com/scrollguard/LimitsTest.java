package com.scrollguard;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * 「自己填分钟数」的解析规则。纯逻辑，不需要 Android 运行时。
 */
public class LimitsTest {

    @Test
    public void takesAnyWholeMinuteInRange() {
        assertEquals(1, Limits.parse("1", Limits.MIN, Limits.MAX));
        assertEquals(7, Limits.parse("7", Limits.MIN, Limits.MAX));
        assertEquals(90, Limits.parse("90", Limits.MIN, Limits.MAX));
        assertEquals(600, Limits.parse("600", Limits.MIN, Limits.MAX));
    }

    @Test
    public void trimsSpaces() {
        assertEquals(15, Limits.parse("  15 ", Limits.MIN, Limits.MAX));
    }

    @Test
    public void acceptsFullWidthDigits() {
        assertEquals(25, Limits.parse("２５", Limits.MIN, Limits.MAX));
    }

    @Test
    public void rejectsJunkAndEmpty() {
        assertEquals(-1, Limits.parse("", Limits.MIN, Limits.MAX));
        assertEquals(-1, Limits.parse("   ", Limits.MIN, Limits.MAX));
        assertEquals(-1, Limits.parse("abc", Limits.MIN, Limits.MAX));
        assertEquals(-1, Limits.parse("1.5", Limits.MIN, Limits.MAX));
        assertEquals(-1, Limits.parse("-5", Limits.MIN, Limits.MAX));
        assertEquals(-1, Limits.parse(null, Limits.MIN, Limits.MAX));
    }

    @Test
    public void rejectsOutOfRange() {
        assertEquals(-1, Limits.parse("0", Limits.MIN, Limits.MAX));
        assertEquals(-1, Limits.parse("721", Limits.MIN, Limits.MAX));
        assertEquals(720, Limits.parse("720", Limits.MIN, Limits.MAX));
    }

    @Test
    public void autoUnlockAcceptsZeroAsOff() {
        assertEquals(0, Limits.parse("0", Limits.AUTO_OFF, AutoUnlock.MAX));
        assertEquals(30, Limits.parse("30", Limits.AUTO_OFF, AutoUnlock.MAX));
        assertEquals(-1, Limits.parse("721", Limits.AUTO_OFF, AutoUnlock.MAX));
    }

    @Test
    public void clampPullsOldValuesBackIn() {
        assertEquals(1, Limits.clamp(0, Limits.MIN, Limits.MAX));
        assertEquals(1, Limits.clamp(-30, Limits.MIN, Limits.MAX));
        assertEquals(720, Limits.clamp(99999, Limits.MIN, Limits.MAX));
        assertEquals(12, Limits.clamp(12, Limits.MIN, Limits.MAX));
        assertEquals(0, Limits.clamp(-1, Limits.AUTO_OFF, Limits.MAX));
    }
}
