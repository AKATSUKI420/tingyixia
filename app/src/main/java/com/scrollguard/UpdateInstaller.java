package com.scrollguard;

import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Hands the verified APK to the system installer; the user still confirms.
 *
 * <p>The first route is a plain {@code ACTION_VIEW} intent at the downloaded
 * file: the system package installer owns the "install unknown apps" prompt on
 * every ROM, and on MIUI / HyperOS it is also what makes the app show up in
 * that per-app list at all. Walking the user to the list ourselves tends to
 * land on a page where "停一下" is still missing, because the app has never
 * tried to install anything.
 *
 * <p>Only when no installer activity resolves do we fall back to a
 * PackageInstaller session. That route is not silent: the system answers with
 * {@code STATUS_PENDING_USER_ACTION} and waits for the app to launch the
 * confirmation intent (see {@link InstallResultReceiver}), otherwise the
 * install dies without a trace.
 */
public final class UpdateInstaller {

    private static final String TAG = "UpdateInstaller";

    private UpdateInstaller() {
    }

    public static void install(Context context, File apk) throws IOException {
        try {
            openSystemInstaller(context, apk);
        } catch (IOException e) {
            Log.w(TAG, "no system installer, trying a package session", e);
            installViaSession(context, apk);
        }
    }

    private static void installViaSession(Context context, File apk) throws IOException {
        PackageManager pm = context.getPackageManager();
        if (pm == null) {
            throw new IOException("no package manager");
        }

        PackageInstaller installer = pm.getPackageInstaller();
        if (installer == null) {
            throw new IOException("no package installer");
        }

        PackageInstaller.SessionParams params =
                new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(context.getPackageName());

        int sessionId = installer.createSession(params);
        try (PackageInstaller.Session session = installer.openSession(sessionId);
             InputStream in = new FileInputStream(apk);
             OutputStream out = session.openWrite("base.apk", 0, apk.length())) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
            session.fsync(out);

            Intent result = new Intent(context, InstallResultReceiver.class);
            result.setAction(InstallResultReceiver.ACTION_INSTALL_RESULT);
            PendingIntent pending = PendingIntent.getBroadcast(
                    context, sessionId, result,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            session.commit(pending.getIntentSender());
        }
    }

    /** Opens the system installer on the downloaded file; the user taps 安装 there. */
    private static void openSystemInstaller(Context context, File apk) throws IOException {
        Uri uri = UpdateFileProvider.uriFor(apk);
        Intent intent = new Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            throw new IOException("no system installer", e);
        }
    }
}
