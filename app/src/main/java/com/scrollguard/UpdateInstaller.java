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
 * <p>Two routes: a PackageInstaller session (silent hand-off, the system shows
 * its own confirm dialog), and if that is refused — some ROMs reject sessions
 * even when "install unknown apps" is on — an {@code ACTION_VIEW} intent to the
 * system package installer, which checks the same permission itself.
 */
public final class UpdateInstaller {

    private static final String TAG = "UpdateInstaller";

    private UpdateInstaller() {
    }

    public static void install(Context context, File apk) throws IOException {
        try {
            installViaSession(context, apk);
        } catch (SecurityException e) {
            Log.w(TAG, "install session refused, falling back to installer intent", e);
            openSystemInstaller(context, apk);
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
