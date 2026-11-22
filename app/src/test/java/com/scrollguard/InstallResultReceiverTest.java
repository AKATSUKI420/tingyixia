package com.scrollguard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Intent;
import android.content.pm.PackageInstaller;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowToast;

/**
 * Guards the hand-off between a PackageInstaller session and the system
 * confirmation screen. 1.0.1 / 1.0.2 never started that screen, so an in-app
 * update ended with a toast and no install at all.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class InstallResultReceiverTest {

    @Test
    public void pendingUserActionLaunchesTheConfirmation() {
        Application app = RuntimeEnvironment.getApplication();
        Intent confirm = new Intent(Intent.ACTION_INSTALL_PACKAGE)
                .setClassName("com.android.packageinstaller", "Confirm");

        Intent result = new Intent(InstallResultReceiver.ACTION_INSTALL_RESULT)
                .putExtra(PackageInstaller.EXTRA_STATUS,
                        PackageInstaller.STATUS_PENDING_USER_ACTION)
                .putExtra(Intent.EXTRA_INTENT, confirm);

        new InstallResultReceiver().onReceive(app, result);

        Intent started = Shadows.shadowOf(app).getNextStartedActivity();
        assertNotNull("没把系统确认页拉起来", started);
        assertEquals(Intent.ACTION_INSTALL_PACKAGE, started.getAction());
        assertTrue("确认页要在新任务里起",
                (started.getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK) != 0);
    }

    @Test
    public void failureSaysItDidNotFinish() {
        Application app = RuntimeEnvironment.getApplication();
        Intent result = new Intent(InstallResultReceiver.ACTION_INSTALL_RESULT)
                .putExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);

        new InstallResultReceiver().onReceive(app, result);

        assertEquals(app.getString(R.string.update_install_failed),
                ShadowToast.getTextOfLatestToast());
    }
}
