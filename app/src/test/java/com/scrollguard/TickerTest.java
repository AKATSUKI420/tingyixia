package com.scrollguard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * M3 · 计时口径。对应 PRD 第 10 节 D 组（v1.1 起：切走不清零）。
 * 纯逻辑，不需要 Android 运行时。
 */
public class TickerTest {

    private static final long MIN = 60000L;

    private static Ticker walk(Ticker t, int seconds, boolean counting, long limitMs) {
        for (int i = 0; i < seconds; i++) {
            t.advance(1000L, counting, limitMs);
        }
        return t;
    }

    /** D1：竖着刷，计时一直在涨 */
    @Test
    public void d1_countingAccumulates() {
        Ticker t = walk(new Ticker(), 60, true, 10 * MIN);
        assertEquals(60000L, t.countedMs());
    }

    /** D2（v1.1）：切走再回来不清零，攒下的时间接着算 */
    @Test
    public void d2_leavingDoesNotReset() {
        Ticker t = walk(new Ticker(), 30, true, 10 * MIN);
        assertEquals(30000L, t.countedMs());

        walk(t, 5, false, 10 * MIN);
        assertEquals("切走 5 秒不该清零，也不该涨", 30000L, t.countedMs());

        walk(t, 3, true, 10 * MIN);
        assertEquals("回来接着攒", 33000L, t.countedMs());
    }

    /** 切走很久也不清零：视频停了、App 换了、屏幕黑了，攒的都还在 */
    @Test
    public void longLeaveKeepsTheCount() {
        Ticker t = walk(new Ticker(), 30, true, 10 * MIN);
        walk(t, 600, false, 10 * MIN);
        assertEquals("走开十分钟回来还是那 30 秒", 30000L, t.countedMs());
    }

    /** D3：横过来不算 */
    @Test
    public void d3_landscapeDoesNotCount() {
        assertFalse(Ticker.counts(false, true, true, true));
        assertTrue(Ticker.counts(true, true, true, true));
    }

    /** D4：熄屏不算 */
    @Test
    public void d4_screenOffDoesNotCount() {
        assertFalse(Ticker.counts(true, false, true, true));
    }

    /** D6：把那个 App 的开关关掉就不算 */
    @Test
    public void d6_targetOffDoesNotCount() {
        assertFalse(Ticker.counts(true, true, false, true));
    }

    /** D9：横屏长视频（横着拿）不算 */
    @Test
    public void d9_landscapeLongVideoDoesNotCount() {
        assertFalse(Ticker.counts(false, true, true, true));
        // 而且它确实不会累积
        Ticker t = new Ticker();
        long due = 0;
        for (int i = 0; i < 600; i++) {
            if (t.advance(1000L, Ticker.counts(false, true, true, true), 10 * MIN)) {
                due++;
            }
        }
        assertEquals(0L, t.countedMs());
        assertEquals(0, due);
    }

    /** D7：攒满设定时长的那一刻返回 true，且只在那一下 */
    @Test
    public void d7_locksExactlyAtLimit() {
        Ticker t = new Ticker();
        long limit = 10 * MIN;
        int locks = 0;
        int firstLockAt = -1;
        for (int i = 1; i <= 60 * 11; i++) {
            boolean due = t.advance(1000L, true, limit);
            if (due) {
                locks++;
                if (firstLockAt < 0) {
                    firstLockAt = i;
                }
            }
        }
        assertEquals("只该触发一次（锁上之后不再算）", 1, locks);
        assertEquals("第 600 秒刚好满 10 分钟", 600, firstLockAt);
    }

    /** D8：解锁后从零开始，不是接着算 */
    @Test
    public void d8_afterUnlockCountsFromZero() {
        Ticker t = new Ticker();
        for (int i = 0; i < 600; i++) {
            t.advance(1000L, true, 10 * MIN);
        }
        assertTrue(t.countedMs() >= 10 * MIN);

        t.resetCounting();
        assertEquals(0L, t.countedMs());

        t.advance(1000L, true, 10 * MIN);
        assertEquals(1000L, t.countedMs());
    }

    /** 严格模式：没声音又没滑动就不算数 */
    @Test
    public void strictModeNeedsActivity() {
        assertFalse(Ticker.active(true, false, false));
        assertTrue(Ticker.active(true, true, false));
        assertTrue(Ticker.active(true, false, true));
        assertTrue("非严格模式一律算有动静", Ticker.active(false, false, false));
    }

    /** 落盘：攒够 15 秒才该写一次 */
    @Test
    public void pendingAccumulatesThenDrains() {
        Ticker t = walk(new Ticker(), 20, true, 10 * MIN);
        assertEquals(20000L, t.pendingMs());

        long drained = t.takePending();
        assertEquals(20000L, drained);
        assertEquals(0L, t.pendingMs());
        assertEquals("取走落盘的量不影响连续时长", 20000L, t.countedMs());
    }

    /** 落盘的量不会因为离开而丢：走开时已经攒下的时间照样算进今天 */
    @Test
    public void pendingSurvivesLeaving() {
        Ticker t = walk(new Ticker(), 20, true, 10 * MIN);
        walk(t, 5, false, 10 * MIN);
        assertEquals(20000L, t.countedMs());
        assertEquals(20000L, t.pendingMs());
    }

    /** 休眠/MainLooper 卡顿回来的大跳，按 1 秒算，不能一口吃成 10 分钟 */
    @Test
    public void clampHandlesJumps() {
        assertEquals(1000L, Ticker.clamp(0L));
        assertEquals(1000L, Ticker.clamp(-5000L));
        assertEquals(1000L, Ticker.clamp(60000L));
        assertEquals(1000L, Ticker.clamp(Ticker.MAX_STEP_MS + 1));
        assertEquals(2500L, Ticker.clamp(2500L));
    }

    /** 一次大跳不会直接触发拦截 */
    @Test
    public void bigJumpNeverLocksByItself() {
        Ticker t = new Ticker();
        boolean due = t.advance(Ticker.clamp(3600000L), true, 10 * MIN);
        assertFalse(due);
        assertEquals(1000L, t.countedMs());
    }

    /** 拦过一次之后不该每秒都喊拦，直到清零 */
    @Test
    public void firesOnceUntilReset() {
        Ticker u = new Ticker();
        int fires = 0;
        for (int i = 0; i < 400; i++) {
            if (u.advance(1000L, true, 60_000L)) {
                fires++;
            }
        }
        assertEquals("60 秒之后还有 340 秒，但只该喊一次", 1, fires);

        u.resetCounting();
        int again = 0;
        for (int i = 0; i < 120; i++) {
            if (u.advance(1000L, true, 60_000L)) {
                again++;
            }
        }
        assertEquals("清零之后是新一轮", 1, again);
    }

    /** v1.1：切走不会重新武装——拦过一次之后，只有解锁（resetCounting）才算新一轮 */
    @Test
    public void leavingDoesNotRearm() {
        Ticker t = new Ticker();
        int fires = 0;
        for (int i = 0; i < 70; i++) {
            if (t.advance(1000L, true, 60_000L)) {
                fires++;
            }
        }
        assertEquals(1, fires);

        for (int i = 0; i < 5; i++) {
            t.advance(1000L, false, 60_000L);
        }
        assertEquals("切走不该清掉攒下的时间", 70_000L, t.countedMs());

        for (int i = 0; i < 70; i++) {
            if (t.advance(1000L, true, 60_000L)) {
                fires++;
            }
        }
        assertEquals("没解锁就不该再拦一次", 1, fires);

        t.resetCounting();
        int again = 0;
        for (int i = 0; i < 70; i++) {
            if (t.advance(1000L, true, 60_000L)) {
                again++;
            }
        }
        assertEquals("解锁之后才是新一轮", 1, again);
    }
}
