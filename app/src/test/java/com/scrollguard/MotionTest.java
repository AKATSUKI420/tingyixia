package com.scrollguard;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** 四种遮罩动效的取值、标签和回退。 */
public class MotionTest {

    @Test
    public void defaultsToFade() {
        assertEquals(Motion.FADE, Motion.DEFAULT);
        assertEquals(Motion.FADE, Motion.normalize(null));
        assertEquals(Motion.FADE, Motion.normalize(""));
        assertEquals(Motion.FADE, Motion.normalize("nonsense"));
    }

    @Test
    public void keepsTheFourKnownValues() {
        for (String v : Motion.values()) {
            assertEquals(v, Motion.normalize(v));
        }
    }

    @Test
    public void labelsLineUpWithValues() {
        String[] labels = Motion.labels();
        String[] values = Motion.values();
        assertEquals(4, labels.length);
        assertEquals(4, values.length);
        assertEquals(Motion.LABEL_FADE, labels[0]);
        assertEquals(Motion.LABEL_SHEET, labels[1]);
        assertEquals(Motion.LABEL_STAGGER, labels[2]);
        assertEquals(Motion.LABEL_SPRING, labels[3]);
        assertEquals(Motion.FADE, values[0]);
        assertEquals(Motion.SHEET, values[1]);
        assertEquals(Motion.STAGGER, values[2]);
        assertEquals(Motion.SPRING, values[3]);
    }

    @Test
    public void indexOfMatchesLabels() {
        for (int i = 0; i < Motion.values().length; i++) {
            assertEquals(i, Motion.indexOf(Motion.values()[i]));
        }
        assertEquals(0, Motion.indexOf("nonsense"));
    }

    @Test
    public void everyLabelIsReadable() {
        for (String v : Motion.values()) {
            assertTrue(Motion.label(v).length() > 0);
        }
    }
}
