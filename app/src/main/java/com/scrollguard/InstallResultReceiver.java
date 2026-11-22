package com.scrollguard;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.util.Log;
import android.widget.Toast;

/**
 * Shows the result of the system install prompt, and — more importantly —
 * launches the prompt itself.
 *
 * <p>A PackageInstaller session is not silent. {@code commit()} comes back as
 * {@code STATUS_PENDING_USER_ACTION} together with the installer activity the
 * app is expected to start; without that the confirmation never appears and
 * 1.0.1 / 1.0.2 behaved exactly like "点了没反应".
 */
public final class InstallResultReceiver extends BroadcastReceiver {

    public static final String ACTION_INSTALL_RESULT =
            "com.scrollguard.action.INSTALL_RESULT";

    private static final String TAG = "InstallResult";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            return;
        }
        int status = intent.getIntExtra(
                PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);

        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT);
            if (confirm == null) {
                toast(context, R.string.update_install_failed);
                return;
            }
            confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                context.startActivity(confirm);
            } catch (Throwable t) {
                Log.w(TAG, "install confirmation cannot be shown", t);
                toast(context, R.string.update_install_failed);
            }
            return;
        }

        if (status == PackageInstaller.STATUS_SUCCESS) {
            toast(context, R.string.update_install_success);
            return;
        }

        Log.w(TAG, "install failed: status=" + status
                + " message=" + intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE));
        toast(context, R.string.update_install_failed);
    }

    private static void toast(Context context, int text) {
        Toast.makeText(context, text, Toast.LENGTH_LONG).show();
    }
}
