package com.scrollguard;

import android.widget.TextView;

import org.robolectric.shadows.ShadowLooper;

import java.time.Duration;

/** 测试里替真人点九宫格。 */
final class TestPad {

    private TestPad() {
    }

    static void tap(PinPadView pad, char c) {
        int id;
        switch (c) {
            case '0': id = R.id.k0; break;
            case '1': id = R.id.k1; break;
            case '2': id = R.id.k2; break;
            case '3': id = R.id.k3; break;
            case '4': id = R.id.k4; break;
            case '5': id = R.id.k5; break;
            case '6': id = R.id.k6; break;
            case '7': id = R.id.k7; break;
            case '8': id = R.id.k8; break;
            case '9': id = R.id.k9; break;
            default: throw new IllegalArgumentException("不是数字: " + c);
        }
        pad.findViewById(id).performClick();
    }

    /** 连点六下，再把 90ms 后那次回调跑出来 */
    static void type(PinPadView pad, String pin) {
        for (char c : pin.toCharArray()) {
            tap(pad, c);
        }
        ShadowLooper.shadowMainLooper().idleFor(Duration.ofMillis(120));
    }

    static String message(PinPadView pad) {
        TextView t = pad.findViewById(R.id.pin_msg);
        return t.getText() == null ? "" : t.getText().toString();
    }

    static int dots(PinPadView pad) {
        return ((android.view.ViewGroup) pad.findViewById(R.id.pin_dots)).getChildCount();
    }
}
