package com.scrollguard;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * Serves the downloaded update APK to the system installer.
 *
 * <p>Installing with an {@code ACTION_VIEW} intent needs a content:// URI on
 * Android 7+, and the app has no AndroidX, so this is a minimal read-only
 * provider limited to {@code cache/update.apk}.
 */
public final class UpdateFileProvider extends ContentProvider {

    public static final String AUTHORITY = "com.scrollguard.updates";
    public static final String FILE_NAME = "update.apk";

    public static Uri uriFor(File apk) {
        return new Uri.Builder()
                .scheme("content")
                .authority(AUTHORITY)
                .appendPath(apk.getName())
                .build();
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) {
            throw new FileNotFoundException("read-only provider");
        }
        String name = uri.getLastPathSegment();
        if (name == null || !name.equals(FILE_NAME)) {
            throw new FileNotFoundException("unknown file: " + name);
        }
        File apk = new File(getContext().getCacheDir(), FILE_NAME);
        if (!apk.isFile()) {
            throw new FileNotFoundException(apk.getAbsolutePath());
        }
        return ParcelFileDescriptor.open(apk, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public String getType(Uri uri) {
        return "application/vnd.android.package-archive";
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
