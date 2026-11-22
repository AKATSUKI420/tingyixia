package com.scrollguard;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;

/**
 * The small manifest that the release page publishes next to the APK.
 * It is deliberately strict: the updater only accepts HTTPS and a SHA-256.
 */
public final class UpdateInfo {

    public final int versionCode;
    public final String versionName;
    public final String apkUrl;
    public final String sha256;
    public final String notes;

    private UpdateInfo(int versionCode, String versionName, String apkUrl,
                       String sha256, String notes) {
        this.versionCode = versionCode;
        this.versionName = versionName;
        this.apkUrl = apkUrl;
        this.sha256 = sha256;
        this.notes = notes;
    }

    public boolean isNewerThan(long currentCode) {
        return versionCode > currentCode;
    }

    public static UpdateInfo parse(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("empty manifest");
        }
        final JSONObject o;
        try {
            o = new JSONObject(raw);
        } catch (JSONException e) {
            throw new IllegalArgumentException("bad json", e);
        }

        int code;
        try {
            code = o.getInt("versionCode");
        } catch (JSONException e) {
            throw new IllegalArgumentException("missing versionCode", e);
        }
        if (code <= 0) {
            throw new IllegalArgumentException("bad versionCode");
        }

        String name = trim(o.optString("versionName", ""));
        if (name.isEmpty()) {
            throw new IllegalArgumentException("missing versionName");
        }

        String url = trim(o.optString("apkUrl", ""));
        if (!url.startsWith("https://")) {
            throw new IllegalArgumentException("apkUrl must be https");
        }

        String hash = trim(o.optString("sha256", "")).toLowerCase(Locale.ROOT);
        if (!hash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("bad sha256");
        }

        String notes = trim(o.optString("notes", ""));
        if (notes.length() > 2000) {
            notes = notes.substring(0, 2000);
        }
        return new UpdateInfo(code, name, url, hash, notes);
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }
}
