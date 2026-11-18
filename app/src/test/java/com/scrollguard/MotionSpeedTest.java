package com.scrollguard;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class MotionSpeedTest {

    @Test
    public void defaultIsNormal() {
        assertEquals(1f, MotionSpeed.DEFAULT, 0.001f);
    }

    @Test
    public void labelsAndValuesLineUp() {
        assertEquals(4, MotionSpeed.labels().length);
        assertArrayEquals(new float[]{0.6f, 1f, 1.5f, 2f}, MotionSpeed.values(), 0.001f);
        assertEquals("标准 · 1×", MotionSpeed.label(MotionSpeed.DEFAULT));
    }

    @Test
    public void unknownSpeedsSnapToTheNearestChoice() {
        assertEquals(1.5f, MotionSpeed.normalize(1.4f), 0.001f);
        assertEquals(0.6f, MotionSpeed.normalize(0.2f), 0.001f);
        assertEquals(2f, MotionSpeed.normalize(9f), 0.001f);
    }

    @Test
    public void indexMatchesTheChosenSpeed() {
        assertEquals(0, MotionSpeed.indexOf(0.6f));
        assertEquals(1, MotionSpeed.indexOf(1f));
        assertEquals(2, MotionSpeed.indexOf(1.5f));
        assertEquals(3, MotionSpeed.indexOf(2f));
    }

    @Test
    public void fastLabelsSaySo() {
        assertEquals("快 · 1.5×", MotionSpeed.label(1.5f));
        assertEquals("很快 · 2×", MotionSpeed.label(2f));
    }
}
