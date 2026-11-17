package com.linkvault.app.data.saf;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.UriPermission;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;

import androidx.annotation.Nullable;

/**
 * Owns the persisted SAF tree-URI grant (FR-07). The tree URI string lives
 * in SharedPreferences — never a raw filesystem path. Access state is
 * re-checked at startup and before storage operations.
 */
public class StoragePermissionManager {

    private static final String PREFS = "linkvault_storage";
    private static final String KEY_TREE_URI = "tree_uri";
    private static final int GRANT_FLAGS =
            Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;

    public enum AccessState { NO_FOLDER, GRANTED, REVOKED }

    private final Context context;
    private final SharedPreferences prefs;

    public StoragePermissionManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    @Nullable
    public Uri getTreeUri() {
        String raw = prefs.getString(KEY_TREE_URI, null);
        return raw == null ? null : Uri.parse(raw);
    }

    /**
     * Persists a tree URI picked via ACTION_OPEN_DOCUMENT_TREE.
     * Releases any previous grant first; uses commit() so the grant survives
     * immediate process death. Returns false if the persistable grant fails.
     */
    public boolean onTreePicked(Uri treeUri) {
        Uri old = getTreeUri();
        if (old != null && !old.equals(treeUri)) {
            releaseGrant(old);
        }
        try {
            context.getContentResolver().takePersistableUriPermission(treeUri, GRANT_FLAGS);
        } catch (SecurityException e) {
            return false;
        }
        // commit() (not apply): success must not be reported before durable.
        return prefs.edit().putString(KEY_TREE_URI, treeUri.toString()).commit();
    }

    public AccessState getAccessState() {
        Uri uri = getTreeUri();
        if (uri == null) return AccessState.NO_FOLDER;
        for (UriPermission p : context.getContentResolver().getPersistedUriPermissions()) {
            if (uri.equals(p.getUri()) && p.isReadPermission() && p.isWritePermission()) {
                return AccessState.GRANTED;
            }
        }
        return AccessState.REVOKED;
    }

    /**
     * Confirms the folder itself still exists/answers (grant can be present
     * while the directory was deleted on some providers).
     */
    public boolean probeFolderAlive() {
        Uri treeUri = getTreeUri();
        if (treeUri == null || getAccessState() != AccessState.GRANTED) return false;
        try {
            String docId = DocumentsContract.getTreeDocumentId(treeUri);
            Uri docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId);
            try (Cursor c = context.getContentResolver().query(
                    docUri, new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID},
                    null, null, null)) {
                return c != null && c.moveToFirst();
            }
        } catch (Exception e) {
            return false;
        }
    }

    /** Full validation used at startup and before storage batches. */
    public boolean validate() {
        return getAccessState() == AccessState.GRANTED && probeFolderAlive();
    }

    /** Drops stored URI + releases the grant (used on explicit reselect). */
    public void clear() {
        Uri old = getTreeUri();
        if (old != null) releaseGrant(old);
        prefs.edit().remove(KEY_TREE_URI).commit();
    }

    private void releaseGrant(Uri uri) {
        try {
            context.getContentResolver().releasePersistableUriPermission(uri, GRANT_FLAGS);
        } catch (SecurityException ignored) {
            // Already released or never granted — nothing to do.
        }
    }
}
