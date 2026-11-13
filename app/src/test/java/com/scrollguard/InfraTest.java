package com.scrollguard;

import static org.junit.Assert.assertNotNull;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * 只为确认测试环境本身是通的：Robolectric 能起一个 Android 运行时、能读到资源。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class InfraTest {

    @Test
    public void runtimeIsUp() {
        assertNotNull(RuntimeEnvironment.getApplication());
    }

    @Test
    public void resourcesAreAvailable() {
        String name = RuntimeEnvironment.getApplication().getString(R.string.app_name);
        org.junit.Assert.assertEquals("停一下", name);
    }
}
