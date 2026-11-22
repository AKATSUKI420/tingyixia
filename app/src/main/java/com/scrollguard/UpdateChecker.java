package com.scrollguard;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Reads a tiny JSON manifest from GitHub Releases. No data is sent except the
 * GET itself; the app version is compared locally.
 */
public final class UpdateChecker {

    /**
     * Put update.json next to every release. The asset name must stay
     * update.json, because "latest/download" resolves by asset name.
     */
    public static final String MANIFEST_URL =
            "https://github.com/AKATSUKI420/tingyixia/releases/latest/download/update.json";

    public interface Fetcher {
        String fetch(String url) throws IOException;
    }

    public interface Callback {
        void onResult(UpdateInfo info, Throwable error);
    }

    static Fetcher fetcher = UpdateChecker::httpGet;

    private static final ExecutorService POOL =
            Executors.newSingleThreadExecutor(r -> new Thread(r, "update-check"));
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private UpdateChecker() {
    }

    public static void check(Context context, Callback callback) {
        final Context app = context.getApplicationContext();
        final long current = currentVersionCode(app);
        POOL.execute(() -> {
            UpdateInfo result = null;
            Throwable error = null;
            try {
                UpdateInfo parsed = UpdateInfo.parse(fetcher.fetch(MANIFEST_URL));
                if (parsed.isNewerThan(current)) {
                    result = parsed;
                }
            } catch (Throwable t) {
                error = t;
            }
            final UpdateInfo info = result;
            final Throwable failure = error;
            MAIN.post(() -> callback.onResult(info, failure));
        });
    }

    static long currentVersionCode(Context c) {
        try {
            PackageInfo pi = c.getPackageManager()
                    .getPackageInfo(c.getPackageName(), 0);
            if (Build.VERSION.SDK_INT >= 28) {
                return pi.getLongVersionCode();
            }
            return pi.versionCode;
        } catch (Exception e) {
            return 0L;
        }
    }

    static String httpGet(String location) throws IOException {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(location).openConnection();
            conn.setConnectTimeout(8_000);
            conn.setReadTimeout(15_000);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("User-Agent", "scrollguard-update/1");

            int status = conn.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP " + status);
            }
            try (InputStream in = conn.getInputStream();
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buf = new byte[4096];
                int n;
                int total = 0;
                while ((n = in.read(buf)) != -1) {
                    total += n;
                    if (total > 64 * 1024) {
                        throw new IOException("manifest too large");
                    }
                    out.write(buf, 0, n);
                }
                return out.toString(StandardCharsets.UTF_8.name());
            }
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }
}
