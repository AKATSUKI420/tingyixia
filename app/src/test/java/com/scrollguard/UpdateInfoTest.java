package com.scrollguard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class UpdateInfoTest {

    private static final String HASH =
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    @Test
    public void parsesAValidManifest() {
        UpdateInfo info = UpdateInfo.parse("{"
                + "\"versionCode\":11,"
                + "\"versionName\":\"0.0.10 测试版\","
                + "\"apkUrl\":\"https://example.com/tingyixia.apk\","
                + "\"sha256\":\"" + HASH + "\","
                + "\"notes\":\"修好了触摸\""
                + "}");

        assertEquals(11, info.versionCode);
        assertEquals("0.0.10 测试版", info.versionName);
        assertEquals("https://example.com/tingyixia.apk", info.apkUrl);
        assertEquals(HASH, info.sha256);
        assertEquals("修好了触摸", info.notes);
        assertTrue(info.isNewerThan(10));
        assertFalse(info.isNewerThan(11));
    }

    @Test
    public void notesAreOptional() {
        UpdateInfo info = UpdateInfo.parse("{"
                + "\"versionCode\":11,"
                + "\"versionName\":\"0.0.10\","
                + "\"apkUrl\":\"https://example.com/a.apk\","
                + "\"sha256\":\"" + HASH + "\""
                + "}");
        assertEquals("", info.notes);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsHttp() {
        UpdateInfo.parse("{"
                + "\"versionCode\":11,"
                + "\"versionName\":\"0.0.10\","
                + "\"apkUrl\":\"http://example.com/a.apk\","
                + "\"sha256\":\"" + HASH + "\""
                + "}");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsBadHash() {
        UpdateInfo.parse("{"
                + "\"versionCode\":11,"
                + "\"versionName\":\"0.0.10\","
                + "\"apkUrl\":\"https://example.com/a.apk\","
                + "\"sha256\":\"abc\""
                + "}");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsMissingVersionCode() {
        UpdateInfo.parse("{"
                + "\"versionName\":\"0.0.10\","
                + "\"apkUrl\":\"https://example.com/a.apk\","
                + "\"sha256\":\"" + HASH + "\""
                + "}");
    }
}
