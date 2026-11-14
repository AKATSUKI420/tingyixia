package com.scrollguard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * 问题3 · 锁屏后多久自动解开。纯算术，不需要 Android 运行时。
 */
public class AutoUnlockTest {

    private static final long MIN = 60000L;

    @Test
    public void zeroMeansNever() {
        assertEquals(-1L, AutoUnlock.windowMs(0));
        assertEquals(-1L, AutoUnlock.remainingMs(0, 1000L, 2000L));
        assertFalse(AutoUnlock.due(0, 1000L, 1000L + 24 * 60 * MIN));
        assertEquals("不自动解开", AutoUnlock.label(0));
    }

    @Test
    public void negativeIsAlsoOff() {
        assertEquals(-1L, AutoUnlock.windowMs(-5));
        assertFalse(AutoUnlock.due(-5, 0L, 9999999L));
        assertEquals("不自动解开", AutoUnlock.label(-5));
    }

    @Test
    public void windowIsMinutes() {
        assertEquals(5 * MIN, AutoUnlock.windowMs(5));
        assertEquals(1 * MIN, AutoUnlock.windowMs(1));
    }

    @Test
    public void remainingCountsDown() {
        long shownAt = 10_000L;
        assertEquals(5 * MIN, AutoUnlock.remainingMs(5, shownAt, shownAt));
        assertEquals(4 * MIN, AutoUnlock.remainingMs(5, shownAt, shownAt + MIN));
        assertEquals(1L, AutoUnlock.remainingMs(5, shownAt, shownAt + 5 * MIN - 1));
    }

    @Test
    public void remainingStopsAtZero() {
        long shownAt = 10_000L;
        assertEquals(0L, AutoUnlock.remainingMs(1, shownAt, shownAt + MIN));
        assertEquals(0L, AutoUnlock.remainingMs(1, shownAt, shownAt + 10 * MIN));
    }

    @Test
    public void dueExactlyAtTheMinute() {
        long shownAt = 10_000L;
        assertFalse(AutoUnlock.due(1, shownAt, shownAt + MIN - 1));
        assertTrue(AutoUnlock.due(1, shownAt, shownAt + MIN));
        assertTrue(AutoUnlock.due(1, shownAt, shownAt + 9 * MIN));
    }

    @Test
    public void clockGoingBackwardsIsNotDue() {
        long shownAt = 50_000L;
        assertFalse(AutoUnlock.due(5, shownAt, shownAt - 3 * MIN));
    }

    @Test
    public void labelReadsLikeAThing() {
        assertEquals("5 分钟后自动解开", AutoUnlock.label(5));
        assertEquals("1 分钟后自动解开", AutoUnlock.label(1));
    }

    @Test
    public void countdownReadsLikeAThing() {
        assertEquals("5 分 00 秒后自己解开", AutoUnlock.countdown(5 * MIN));
        assertEquals("1 分 05 秒后自己解开", AutoUnlock.countdown(65_000L));
        assertEquals("45 秒后自己解开", AutoUnlock.countdown(44_500L));
        assertEquals("1 秒后自己解开", AutoUnlock.countdown(1L));
        assertEquals("0 秒后自己解开", AutoUnlock.countdown(0L));
    }

    @Test
    public void windowIsCappedAtMax() {
        assertEquals(AutoUnlock.MAX * MIN, AutoUnlock.windowMs(AutoUnlock.MAX));
        assertEquals(AutoUnlock.MAX * MIN, AutoUnlock.windowMs(AutoUnlock.MAX + 1000));
    }
}
