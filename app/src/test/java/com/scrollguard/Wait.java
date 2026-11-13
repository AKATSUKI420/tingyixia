package com.scrollguard;

import org.robolectric.shadows.ShadowLooper;

import java.time.Duration;
import java.util.function.BooleanSupplier;

/** 等后台线程把结果 post 回主线程。 */
final class Wait {

    private Wait() {
    }

    static void until(String what, BooleanSupplier cond) {
        for (int i = 0; i < 100; i++) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("被打断了：" + what, e);
            }
            ShadowLooper.shadowMainLooper().idleFor(Duration.ofMillis(10));
            if (cond.getAsBoolean()) {
                return;
            }
        }
        throw new AssertionError("等了 2 秒还没等到：" + what);
    }
}
