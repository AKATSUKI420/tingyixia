package com.scrollguard;

import static org.junit.Assert.assertEquals;

import android.content.pm.PackageInfo;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * Guards the packaged version numbers. The release APK is checked again by
 * static-check.ps1; this catches an accidental rollback in the manifest merge
 * before anything is built.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class VersionTest {

    @Test
    public void versionIsBumpedForThisRelease() throws Exception {
        PackageInfo info = RuntimeEnvironment.getApplication()
                .getPackageManager()
                .getPackageInfo("com.scrollguard", 0);

        assertEquals("0.0.5 测试版", info.versionName);
        assertEquals(6L, info.getLongVersionCode());
    }
}
