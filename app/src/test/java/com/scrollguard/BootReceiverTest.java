package com.scrollguard;

import android.app.Application;
import android.content.Intent;
import android.content.pm.PackageManager;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

/**
 * M4：关过机之后，防护得自己回来。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class BootReceiverTest {

    private Application app;

    @Before
    public void setUp() {
        app = RuntimeEnvironment.getApplication();
        Prefs.wipe(app);
    }

    private void arm() {
        Prefs.setEnabled(app, true);
        Pin.save(app, "123456");
    }

    @Test
    public void bootBringsTheGuardBack() {
        arm();

        new BootReceiver().onReceive(app, new Intent(Intent.ACTION_BOOT_COMPLETED));

        Intent started = shadowOf(app).getNextStartedService();
        assertNotNull("开机后没把服务拉起来", started);
        assertEquals(GuardService.class.getName(), started.getComponent().getClassName());
    }

    @Test
    public void quickBootCountsToo() {
        arm();

        new BootReceiver().onReceive(app, new Intent("android.intent.action.QUICKBOOT_POWERON"));

        assertNotNull(shadowOf(app).getNextStartedService());
    }

    @Test
    public void offMeansOff() {
        Prefs.setEnabled(app, false);
        Pin.save(app, "123456");

        new BootReceiver().onReceive(app, new Intent(Intent.ACTION_BOOT_COMPLETED));

        assertNull("关着就别自己爬起来", shadowOf(app).getNextStartedService());
    }

    @Test
    public void noPinMeansNoGuard() {
        Prefs.setEnabled(app, true);

        new BootReceiver().onReceive(app, new Intent(Intent.ACTION_BOOT_COMPLETED));

        assertNull(shadowOf(app).getNextStartedService());
    }

    @Test
    public void otherBroadcastsAreIgnored() {
        arm();

        new BootReceiver().onReceive(app, new Intent("com.example.SOMETHING_ELSE"));

        assertNull(shadowOf(app).getNextStartedService());
    }

    @Test
    public void nullIntentIsSafe() {
        arm();

        new BootReceiver().onReceive(app, null);

        assertNull(shadowOf(app).getNextStartedService());
    }

    @Test
    public void manifestDeclaresBothReceiverAndPermission() throws Exception {
        PackageManager pm = app.getPackageManager();

        android.content.pm.ComponentInfo ci = pm.getReceiverInfo(
                new android.content.ComponentName(app, BootReceiver.class), 0);
        assertNotNull("清单里没有 BootReceiver", ci);
        assertTrue(ci.enabled);

        android.content.pm.PackageInfo pi =
                pm.getPackageInfo(app.getPackageName(), PackageManager.GET_PERMISSIONS);
        boolean declared = false;
        for (String p : pi.requestedPermissions) {
            declared |= "android.permission.RECEIVE_BOOT_COMPLETED".equals(p);
        }
        assertTrue("清单里得有开机广播权限", declared);
    }
}
