package com.scrollguard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.content.ContentProvider;
import android.content.pm.ProviderInfo;
import android.net.Uri;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.File;

/** Guards the provider used to hand the update APK to the system installer. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class UpdateFileProviderTest {

    @Test
    public void uriPointsAtTheDownloadedFile() {
        Uri uri = UpdateFileProvider.uriFor(new File("/tmp/update.apk"));

        assertEquals("content", uri.getScheme());
        assertEquals("com.scrollguard.updates", uri.getAuthority());
        assertEquals("update.apk", uri.getLastPathSegment());
    }

    @Test
    public void providerIsRegisteredReadOnly() {
        ProviderInfo info = RuntimeEnvironment.getApplication()
                .getPackageManager()
                .resolveContentProvider(UpdateFileProvider.AUTHORITY, 0);

        assertNotNull(info);
        assertEquals(UpdateFileProvider.class.getName(), info.name);
        assertEquals(false, info.exported);
        assertEquals(true, info.grantUriPermissions);
    }

    @Test
    public void providerDeclaresTheApkType() {
        ContentProvider provider = new UpdateFileProvider();
        provider.attachInfo(RuntimeEnvironment.getApplication(), new ProviderInfo());

        assertEquals("application/vnd.android.package-archive",
                provider.getType(UpdateFileProvider.uriFor(new File("/tmp/update.apk"))));
    }
}
