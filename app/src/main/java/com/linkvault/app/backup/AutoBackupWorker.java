package com.linkvault.app.backup;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.linkvault.app.App;
import com.linkvault.app.data.saf.DocumentTreeClient;

import java.io.OutputStream;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Periodic auto backup (D-12): writes a timestamped zip into
 * <vault>/backups/ and keeps the newest 7 files. Disabled by default —
 * the worker exits immediately until the user enables it in Settings.
 */
public class AutoBackupWorker extends Worker {

    public static final String PREFS = "lv_prefs";
    public static final String KEY_ENABLED = "auto_backup_enabled";
    public static final String KEY_LAST_RUN = "auto_backup_last_run";
    public static final String WORK_NAME = "auto_backup_periodic";

    private static final String BACKUP_DIR = "backups";
    private static final String FILE_PREFIX = "linkvault_auto_";
    private static final int KEEP = 7;

    private static final DateTimeFormatter TS =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").withZone(ZoneOffset.UTC);

    public AutoBackupWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context ctx = getApplicationContext();
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!prefs.getBoolean(KEY_ENABLED, false)) {
            return Result.success(); // Disabled — nothing to do.
        }
        Uri treeUri = ((App) ctx.getApplicationContext())
                .getContainer().permissionManager().getTreeUri();
        if (treeUri == null) {
            return Result.failure(); // No folder selected yet.
        }
        try {
            var container = ((App) ctx.getApplicationContext()).getContainer();
            DocumentTreeClient tree = new DocumentTreeClient(ctx, treeUri);
            String parent = tree.ensureDirectory(BACKUP_DIR);

            String name = FILE_PREFIX + TS.format(Instant.now()) + ".zip";
            Uri outUri = tree.createFile(parent, name, "application/zip");
            try (OutputStream out = ctx.getContentResolver().openOutputStream(outUri)) {
                if (out == null) throw new IllegalStateException("openOutputStream null");
                container.backupEngine().exportBackup(out);
            }

            prune(tree, parent);

            prefs.edit().putLong(KEY_LAST_RUN, System.currentTimeMillis()).apply();
            return Result.success();
        } catch (Exception e) {
            return Result.retry();
        }
    }

    /** Keep only the newest KEEP auto-backup zips (timestamped names sort lexicographically). */
    private static void prune(DocumentTreeClient tree, String parentDocId) {
        List<DocumentTreeClient.Child> backups = new ArrayList<>();
        for (DocumentTreeClient.Child c : tree.listChildren(parentDocId)) {
            if (c.displayName != null && c.displayName.startsWith(FILE_PREFIX)) {
                backups.add(c);
            }
        }
        Collections.sort(backups, (a, b) -> b.displayName.compareTo(a.displayName));
        for (int i = KEEP; i < backups.size(); i++) {
            tree.delete(backups.get(i).documentId);
        }
    }
}
