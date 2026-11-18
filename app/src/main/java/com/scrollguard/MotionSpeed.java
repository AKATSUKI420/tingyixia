package com.scrollguard;

/** 遮罩动画快慢：同一套动效按倍率缩放时长。 */
final class MotionSpeed {

    static final float SLOW = 0.6f;
    static final float NORMAL = 1f;
    static final float FAST = 1.5f;
    static final float TURBO = 2f;
    static final float DEFAULT = NORMAL;

    private static final float[] VALUES = {SLOW, NORMAL, FAST, TURBO};
    private static final String[] LABELS = {"慢 · 0.6×", "标准 · 1×", "快 · 1.5×", "很快 · 2×"};

    private MotionSpeed() {
    }

    static float normalize(float v) {
        float best = DEFAULT;
        float gap = Float.MAX_VALUE;
        for (float x : VALUES) {
            float d = Math.abs(v - x);
            if (d < gap) {
                gap = d;
                best = x;
            }
        }
        return best;
    }

    static String label(float v) {
        return LABELS[indexOf(v)];
    }

    static String[] labels() {
        return LABELS.clone();
    }

    static float[] values() {
        return VALUES.clone();
    }

    static int indexOf(float v) {
        float n = normalize(v);
        for (int i = 0; i < VALUES.length; i++) {
            if (VALUES[i] == n) {
                return i;
            }
        }
        return 1;
    }
}
