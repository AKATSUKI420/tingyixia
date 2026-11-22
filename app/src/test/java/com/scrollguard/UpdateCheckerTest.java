package com.scrollguard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.concurrent.atomic.AtomicReference;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class UpdateCheckerTest {

    @Before
    public void setUp() {
        UpdateChecker.fetcher = UpdateChecker::httpGet;
    }

    @After
    public void tearDown() {
        UpdateChecker.fetcher = UpdateChecker::httpGet;
    }

    @Test
    public void reportsANewerManifest() {
        UpdateChecker.fetcher = url -> "{"
                + "\"versionCode\":99,"
                + "\"versionName\":\"9.9.9\","
                + "\"apkUrl\":\"https://example.com/a.apk\","
                + "\"sha256\":\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\""
                + "}";

        AtomicReference<UpdateInfo> got = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        UpdateChecker.check(RuntimeEnvironment.getApplication(), (info, t) -> {
            got.set(info);
            error.set(t);
        });

        Wait.until("检查结果回来", () -> got.get() != null || error.get() != null);
        assertNull(error.get());
        assertNotNull(got.get());
        assertEquals(99, got.get().versionCode);
    }

    @Test
    public void sameOrOlderVersionIsNotOffered() {
        UpdateChecker.fetcher = url -> "{"
                + "\"versionCode\":10,"
                + "\"versionName\":\"0.0.9 测试版\","
                + "\"apkUrl\":\"https://example.com/a.apk\","
                + "\"sha256\":\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\""
                + "}";

        AtomicReference<UpdateInfo> got = new AtomicReference<>();
        AtomicReference<Boolean> called = new AtomicReference<>(false);
        UpdateChecker.check(RuntimeEnvironment.getApplication(), (info, t) -> {
            got.set(info);
            called.set(true);
        });

        Wait.until("检查结果回来", called::get);
        assertNull(got.get());
    }

    @Test
    public void badManifestIsAnError() {
        UpdateChecker.fetcher = url -> "not json";

        AtomicReference<Throwable> error = new AtomicReference<>();
        UpdateChecker.check(RuntimeEnvironment.getApplication(), (info, t) -> error.set(t));

        Wait.until("错误回来", () -> error.get() != null);
        assertNotNull(error.get());
    }
}
