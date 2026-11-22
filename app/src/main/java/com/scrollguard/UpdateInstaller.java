package com.scrollguard;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** Hands the verified APK to the system installer; the user still confirms. */
public final class UpdateInstaller {

    private UpdateInstaller() {
    }

    public static void install(Context context, File apk) throws IOException {
        PackageManager pm = context.getPackageManager();
        if (pm == null) {
            throw new IOException("no package manager");
        }
        if (BuildCheck.needsUnknownSources(pm)) {
            throw new SecurityException("unknown sources");
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

    /** Kept separate so the API 26 call is not inlined into old devices. */
    private static final class BuildCheck {
        static boolean needsUnknownSources(PackageManager pm) {
            return android.os.Build.VERSION.SDK_INT >= 26
                    && !pm.canRequestPackageInstalls();
        }
    }
}
