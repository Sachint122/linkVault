package com.linkvault.app.data.share;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import androidx.annotation.Nullable;

/**
 * Metadata extractor for incoming shared file URIs (FR-01, FR-06).
 */
public final class SharedFilePayload {

    public final Uri uri;
    public final String displayName;
    public final String mimeType;
    public final long sizeBytes;

    public SharedFilePayload(Uri uri, String displayName, String mimeType, long sizeBytes) {
        this.uri = uri;
        this.displayName = displayName;
        this.mimeType = mimeType == null ? "application/octet-stream" : mimeType;
        this.sizeBytes = sizeBytes;
    }

    public static SharedFilePayload fromUri(ContentResolver resolver, Uri uri, @Nullable String intentMime) {
        String name = "shared_file";
        long size = 0;
        String mime = intentMime != null ? intentMime : resolver.getType(uri);
        if (mime == null || mime.equals("*/*")) mime = "application/octet-stream";

        try (Cursor cursor = resolver.query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (nameIdx >= 0) {
                    String n = cursor.getString(nameIdx);
                    if (n != null && !n.isEmpty()) name = n;
                }
                int sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (sizeIdx >= 0) {
                    size = cursor.getLong(sizeIdx);
                }
            }
        } catch (Exception ignored) {}

        // If name still lacks extension and mime is known, try last path segment
        if (name.equals("shared_file") && uri.getLastPathSegment() != null) {
            name = uri.getLastPathSegment();
        }

        return new SharedFilePayload(uri, name, mime, size);
    }
}
