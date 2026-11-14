package com.scrollguard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.Set;

/**
 * M4 · 本地数据。对应 PRD 的 E1、E3、E4 与 F4 的数据部分。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class PrefsTest {

    private Context c() {
        return RuntimeEnvironment.getApplication();
    }

    /** 全新装上时的默认值，和 PRD 写的一致 */
    @Test
    public void defaults() {
        assertEquals(10L, Prefs.limitMin(c()));
        assertFalse(Prefs.enabled(c()));
        assertFalse(Prefs.strict(c()));
        assertFalse("还没设密码", Prefs.hasPin(c()));
        assertEquals(0L, Prefs.todayMs(c()));
        assertEquals(0, Prefs.todayLocks(c()));

        Set<String> t = Prefs.targets(c());
        assertEquals(2, t.size());
        assertTrue(t.contains(Prefs.DOUYIN));
        assertTrue(t.contains(Prefs.BILI));
    }

    /** E1/E3：写进去的东西再读还在 */
    @Test
    public void valuesSurvive() {
        Prefs.setLimitMin(c(), 20L);
        Prefs.setEnabled(c(), true);
        Prefs.setStrict(c(), true);
        Prefs.addTodayMs(c(), 90000L);
        Prefs.bumpTodayLocks(c());

        assertEquals(20L, Prefs.limitMin(c()));
        assertTrue(Prefs.enabled(c()));
        assertTrue(Prefs.strict(c()));
        assertEquals(90000L, Prefs.todayMs(c()));
        assertEquals(1, Prefs.todayLocks(c()));
    }

    /** E3：今日时长是累加的，不是覆盖 */
    @Test
    public void todayAccumulates() {
        Prefs.addTodayMs(c(), 15000L);
        Prefs.addTodayMs(c(), 15000L);
        Prefs.addTodayMs(c(), 30000L);
        assertEquals(60000L, Prefs.todayMs(c()));

        Prefs.bumpTodayLocks(c());
        Prefs.bumpTodayLocks(c());
        assertEquals(2, Prefs.todayLocks(c()));
    }

    /** 负数、0 不该把今天的数写坏 */
    @Test
    public void nonPositiveIsIgnored() {
        Prefs.addTodayMs(c(), 10000L);
        Prefs.addTodayMs(c(), 0L);
        Prefs.addTodayMs(c(), -5000L);
        assertEquals(10000L, Prefs.todayMs(c()));
    }

    /** D6：关掉某个 App 的开关（这里只验数据层） */
    @Test
    public void targetsToggle() {
        Prefs.setTarget(c(), Prefs.BILI, false);
        assertFalse(Prefs.targets(c()).contains(Prefs.BILI));
        assertTrue(Prefs.targets(c()).contains(Prefs.DOUYIN));

        Prefs.setTarget(c(), Prefs.BILI, true);
        assertTrue(Prefs.targets(c()).contains(Prefs.BILI));

        Prefs.setTarget(c(), Prefs.DOUYIN, false);
        Prefs.setTarget(c(), Prefs.BILI, false);
        assertTrue("两个都关掉是允许的", Prefs.targets(c()).isEmpty());
    }

    /** E4：跨天靠日键分开——换了日子就是另一个键，自然从 0 开始 */
    @Test
    public void dayKeySplitsDays() {
        java.util.Calendar cal = java.util.Calendar.getInstance(
                java.util.TimeZone.getTimeZone("Asia/Shanghai"));
        cal.clear();
        cal.set(2026, java.util.Calendar.NOVEMBER, 14, 8, 0, 0);
        long today = cal.getTimeInMillis();
        long tomorrow = today + 24 * 3600_000L;

        String k1 = Prefs.dayKey(today);
        String k2 = Prefs.dayKey(tomorrow);
        assertNotEquals(k1, k2);
        assertEquals(8, k1.length());
        assertEquals("20261114", k1);
        assertEquals("20261115", k2);

        Prefs.addTodayMs(c(), 42000L);
        SharedPreferences sp = c().getSharedPreferences("guard", Context.MODE_PRIVATE);
        String key = "d" + Prefs.dayKey(System.currentTimeMillis()) + "_ms";
        assertEquals("今天的数就存在今天这个键上", 42000L, sp.getLong(key, 0L));
    }

    /** F4：忘掉密码＝全清 */
    @Test
    public void wipeClearsEverything() {
        Prefs.setLimitMin(c(), 30L);
        Prefs.setEnabled(c(), true);
        Prefs.setTarget(c(), Prefs.BILI, false);
        Prefs.addTodayMs(c(), 120000L);
        Prefs.bumpTodayLocks(c());
        Pin.save(c(), "123456");

        Prefs.wipe(c());

        assertFalse(Prefs.hasPin(c()));
        assertFalse(Prefs.enabled(c()));
        assertEquals(10L, Prefs.limitMin(c()));
        assertEquals(0L, Prefs.todayMs(c()));
        assertEquals(0, Prefs.todayLocks(c()));
        assertEquals("清完回到默认两个都看着", 2, Prefs.targets(c()).size());
        assertTrue("密码也没了", Prefs.pinHash(c()).isEmpty());
    }

    /** 数据真的落在这个 App 自己的那份文件里，不是内存 */
    @Test
    public void storedInOwnFile() {
        Prefs.setLimitMin(c(), 45L);
        SharedPreferences sp = c().getSharedPreferences("guard", Context.MODE_PRIVATE);
        assertEquals(45L, sp.getLong("limit_min", 0L));
    }

    /** 时长可以填成任意整数分钟，但会被夹在 1–600 里，读出来也不越界 */
    @Test
    public void limitIsClampedToRange() {
        Prefs.setLimitMin(c(), 37L);
        assertEquals(37L, Prefs.limitMin(c()));

        Prefs.setLimitMin(c(), 0L);
        assertEquals("0 分钟等于不设防，拉到下限", 1L, Prefs.limitMin(c()));

        Prefs.setLimitMin(c(), 99999L);
        assertEquals("太大就拉回上限", 600L, Prefs.limitMin(c()));
    }

    /** 自动解开默认关；填多少存多少，越界夹回 0–600 */
    @Test
    public void autoUnlockIsClampedToRange() {
        assertEquals("默认不自动解开", 0, Prefs.autoUnlockMin(c()));

        Prefs.setAutoUnlockMin(c(), 5L);
        assertEquals(5, Prefs.autoUnlockMin(c()));

        Prefs.setAutoUnlockMin(c(), -3L);
        assertEquals("漏下去的负数当关", 0, Prefs.autoUnlockMin(c()));

        Prefs.setAutoUnlockMin(c(), 99999L);
        assertEquals("太大就拉回上限", AutoUnlock.MAX, Prefs.autoUnlockMin(c()));

        SharedPreferences sp = c().getSharedPreferences("guard", Context.MODE_PRIVATE);
        assertEquals(600L, sp.getLong("auto_unlock_min", -1L));
    }

    /** 自动解开设置不会污染别的设置 */
    @Test
    public void autoUnlockDoesNotTouchOtherSettings() {
        Prefs.setLimitMin(c(), 15L);
        Prefs.setStrict(c(), true);
        Prefs.setAutoUnlockMin(c(), 7L);

        assertEquals(15L, Prefs.limitMin(c()));
        assertTrue(Prefs.strict(c()));
        assertEquals(7, Prefs.autoUnlockMin(c()));
    }
}
