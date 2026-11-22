package com.scrollguard;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** 五种遮罩动效的取值、标签和回退。 */
public class MotionTest {

    @Test
    public void defaultsToCenter() {
        assertEquals(Motion.CENTER, Motion.DEFAULT);
        assertEquals(Motion.CENTER, Motion.normalize(null));
        assertEquals(Motion.CENTER, Motion.normalize(""));
        assertEquals(Motion.CENTER, Motion.normalize("nonsense"));
    }

    @Test
    public void keepsTheFiveKnownValues() {
        for (String v : Motion.values()) {
            assertEquals(v, Motion.normalize(v));
        }
    }

    @Test
    public void labelsLineUpWithValues() {
        String[] labels = Motion.labels();
        String[] values = Motion.values();
        assertEquals(5, labels.length);
        assertEquals(5, values.length);
        assertEquals(Motion.LABEL_FADE, labels[0]);
        assertEquals(Motion.LABEL_SHEET, labels[1]);
        assertEquals(Motion.LABEL_STAGGER, labels[2]);
        assertEquals(Motion.LABEL_SPRING, labels[3]);
        assertEquals(Motion.LABEL_CENTER, labels[4]);
        assertEquals(Motion.FADE, values[0]);
        assertEquals(Motion.SHEET, values[1]);
        assertEquals(Motion.STAGGER, values[2]);
        assertEquals(Motion.SPRING, values[3]);
        assertEquals(Motion.CENTER, values[4]);
    }

    @Test
    public void indexOfMatchesLabels() {
        for (int i = 0; i < Motion.values().length; i++) {
            assertEquals(i, Motion.indexOf(Motion.values()[i]));
        }
        assertEquals("不认识的值当默认", Motion.indexOf(Motion.DEFAULT), Motion.indexOf("nonsense"));
    }

    @Test
    public void everyLabelIsReadable() {
        for (String v : Motion.values()) {
            assertTrue(Motion.label(v).length() > 0);
        }
    }
}
