package com.scrollguard;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * 文案。改动文字时这里会先红，避免界面和 PRD 对不上。
 */
public class TextTest {

    @Test
    public void mmss() {
        assertEquals("0 分 00 秒", Text.mmss(0));
        assertEquals("0 分 05 秒", Text.mmss(5000));
        assertEquals("1 分 02 秒", Text.mmss(62000));
        assertEquals("10 分 00 秒", Text.mmss(600000));
        assertEquals("负数按 0 算", "0 分 00 秒", Text.mmss(-1));
    }

    @Test
    public void dur() {
        assertEquals("45 秒", Text.dur(45000));
        assertEquals("0 秒", Text.dur(0));
        assertEquals("1 分钟", Text.dur(60000));
        assertEquals("59 分钟", Text.dur(59 * 60000L));
        assertEquals("1 小时 1 分", Text.dur(61 * 60000L));
        assertEquals("2 小时 0 分", Text.dur(120 * 60000L));
    }

    @Test
    public void limit() {
        assertEquals("10 分钟", Text.limit(10));
        assertEquals("45 分钟", Text.limit(45));
        assertEquals("1 小时 10 分", Text.limit(70));
        assertEquals("12 小时", Text.limit(720));
    }

    @Test
    public void lockTitle() {
        assertEquals("已经刷了 10 分钟", Text.lockTitle(10));
        assertEquals("已经刷了 10 分钟", Text.lockTitle(10));
    }

    /** 遮罩底部那行，三段文案在 PRD 里是定死的 */
    @Test
    public void lockFooter() {
        assertEquals("先坐一会儿", Text.lockFooter(0));
        assertEquals("先坐一会儿", Text.lockFooter(19));
        assertEquals("停下来了 20 秒", Text.lockFooter(20));
        assertEquals("停下来了 59 秒", Text.lockFooter(59));
        assertEquals("停下来了 1 分 0 秒", Text.lockFooter(60));
        assertEquals("停下来了 2 分 5 秒", Text.lockFooter(125));
    }
}
