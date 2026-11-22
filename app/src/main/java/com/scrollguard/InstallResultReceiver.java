package com.scrollguard;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.widget.Toast;

/** Shows the result of the system install prompt. */
public final class InstallResultReceiver extends BroadcastReceiver {

    public static final String ACTION_INSTALL_RESULT =
            "com.scrollguard.action.INSTALL_RESULT";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            return;
        }
        int status = intent.getIntExtra(
                PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        if (status == PackageInstaller.STATUS_SUCCESS) {
            Toast.makeText(context, R.string.update_install_success, Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(context, R.string.update_install_failed, Toast.LENGTH_LONG).show();
        }
    }
}
