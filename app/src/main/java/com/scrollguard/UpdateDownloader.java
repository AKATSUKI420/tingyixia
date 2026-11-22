package com.scrollguard;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Downloads the APK to cache, then verifies both the published SHA-256 and the
 * signing certificate before handing it to the system installer.
 */
public final class UpdateDownloader {

    public static final int ERROR_NETWORK = 1;
    public static final int ERROR_HASH = 2;
    public static final int ERROR_SIGNATURE = 3;
    public static final int ERROR_STORAGE = 4;

    public interface Listener {
        void onProgress(int percent);

        void onDone(File apk);

        void onError(int code);
    }

    private static final int MAX_BYTES = 200 * 1024 * 1024;
    private static final ExecutorService POOL =
            Executors.newSingleThreadExecutor(r -> new Thread(r, "update-download"));
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private UpdateDownloader() {
    }

    public static void download(Context context, UpdateInfo info, Listener listener) {
        final Context app = context.getApplicationContext();
        POOL.execute(() -> {
            try {
                File apk = downloadBlocking(app, info, listener);
                MAIN.post(() -> listener.onDone(apk));
            } catch (DownloadError e) {
                MAIN.post(() -> listener.onError(e.code));
            } catch (Throwable t) {
                MAIN.post(() -> listener.onError(ERROR_NETWORK));
            }
        });
    }

    private static File downloadBlocking(Context c, UpdateInfo info, Listener listener)
            throws DownloadError {
        File part = new File(c.getCacheDir(), "update.apk.part");
        File apk = new File(c.getCacheDir(), "update.apk");
        delete(part);
        delete(apk);

        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(info.apkUrl).openConnection();
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(20_000);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", "scrollguard-update/1");

            int status = conn.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                throw new DownloadError(ERROR_NETWORK);
            }
            int total = conn.getContentLength();
            if (total > MAX_BYTES) {
                throw new DownloadError(ERROR_STORAGE);
            }

            long copied = 0;
            int lastPercent = -1;
            try (InputStream in = conn.getInputStream();
                 OutputStream out = new FileOutputStream(part)) {
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = in.read(buf)) != -1) {
                    copied += n;
                    if (copied > MAX_BYTES) {
                        throw new DownloadError(ERROR_STORAGE);
                    }
                    out.write(buf, 0, n);
                    if (total > 0) {
                        int percent = (int) Math.min(100, copied * 100 / total);
                        if (percent != lastPercent) {
                            lastPercent = percent;
                            final int p = percent;
                            MAIN.post(() -> listener.onProgress(p));
                        }
                    }
                }
            }

            if (total > 0 && copied != total) {
                throw new DownloadError(ERROR_NETWORK);
            }
            if (!info.sha256.equals(sha256(part))) {
                throw new DownloadError(ERROR_HASH);
            }
            if (!signerMatches(c, part)) {
                throw new DownloadError(ERROR_SIGNATURE);
            }
            if (!part.renameTo(apk)) {
                copy(part, apk);
                delete(part);
            }
            return apk;
        } catch (DownloadError e) {
            delete(part);
            throw e;
        } catch (IOException e) {
            delete(part);
            throw new DownloadError(ERROR_NETWORK);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static final class DownloadError extends Exception {
        final int code;

        DownloadError(int code) {
            this.code = code;
        }
    }

    static String sha256(File file) {
        try (InputStream in = new FileInputStream(file)) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) != -1) {
                md.update(buf, 0, n);
            }
            return hex(md.digest());
        } catch (Exception e) {
            return "";
        }
    }

    static String sha256(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return hex(md.digest(bytes));
        } catch (Exception e) {
            return "";
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format(Locale.ROOT, "%02x", b & 0xff));
        }
        return sb.toString();
    }

    static boolean signerMatches(Context c, File apk) {
        try {
            PackageManager pm = c.getPackageManager();
            PackageInfo current;
            PackageInfo candidate;
            if (Build.VERSION.SDK_INT >= 28) {
                current = pm.getPackageInfo(c.getPackageName(),
                        PackageManager.GET_SIGNING_CERTIFICATES);
                candidate = pm.getPackageArchiveInfo(apk.getAbsolutePath(),
                        PackageManager.GET_SIGNING_CERTIFICATES);
                if (candidate == null || candidate.signingInfo == null) {
                    return false;
                }
                Signature[] a = current.signingInfo == null
                        ? null : current.signingInfo.getApkContentsSigners();
                Signature[] b = candidate.signingInfo.getApkContentsSigners();
                return sameFirst(a, b);
            }
            current = pm.getPackageInfo(c.getPackageName(), PackageManager.GET_SIGNATURES);
            candidate = pm.getPackageArchiveInfo(apk.getAbsolutePath(),
                    PackageManager.GET_SIGNATURES);
            return candidate != null && sameFirst(current.signatures, candidate.signatures);
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean sameFirst(Signature[] a, Signature[] b) {
        if (a == null || b == null || a.length == 0 || b.length == 0) {
            return false;
        }
        return MessageDigest.isEqual(a[0].toByteArray(), b[0].toByteArray());
    }

    private static void copy(File from, File to) throws IOException {
        try (InputStream in = new FileInputStream(from);
             OutputStream out = new FileOutputStream(to)) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
        }
    }

    private static void delete(File f) {
        if (f != null && f.exists()) {
            //noinspection ResultOfMethodCallIgnored
            f.delete();
        }
    }
}
