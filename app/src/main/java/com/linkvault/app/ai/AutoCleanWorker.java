package com.linkvault.app.ai;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.linkvault.app.App;
import com.linkvault.app.data.saf.DocumentTreeClient;
import com.linkvault.app.data.saf.StoragePermissionManager;
import com.linkvault.app.domain.model.AutoRule;
import com.linkvault.app.domain.model.Category;
import com.linkvault.app.domain.model.FileRecord;
import com.linkvault.app.domain.model.LinkRecord;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Daily housekeeping (D-19): the "AI keeps the app clean" pass.
 *
 * <b>Hard invariant: links and files are never deleted.</b> The worker only
 * (1) removes non-system categories that have held nothing for
 * {@link AutoCleanPlanner#GRACE_DAYS} days (and their empty on-disk
 * folders, including orphans left by manual deletes), and (2) prunes
 * redundant tags from link records (folder-name duplicates, singular/plural
 * pairs, blanks). Auto-categorize rules, Uncategorized, and categories with
 * any record or child folder are always kept.
 */
public class AutoCleanWorker extends Worker {

    public static final String PREFS = "lv_prefs";
    public static final String KEY_LAST_RUN = "auto_clean_last_run";
    public static final String KEY_LAST_STATS = "auto_clean_last_stats";
    public static final String WORK_NAME = "auto_clean_periodic";

    private static final String CATEGORIES_DIR = "user/categories";

    public AutoCleanWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context ctx = getApplicationContext();
        App app = (App) ctx.getApplicationContext();
        if (app.getContainer().permissionManager().getAccessState()
                != StoragePermissionManager.AccessState.GRANTED) {
            return Result.success(); // No folder yet — nothing to clean.
        }
        try {
            var c = app.getContainer();

            List<LinkRecord> links = c.recordRepository().getAllLinks();
            List<FileRecord> files = c.fileRepository().getAllFiles();
            List<Category> categories = c.categoryRepository().getAll();
            List<AutoRule> rules = c.autoCatRepository().getAll();

            // --- plan: which categories may go (pure logic, never records)
            Set<String> occupied = new HashSet<>();
            for (LinkRecord r : links) {
                if (r.getCategoryId() != null) occupied.add(r.getCategoryId());
            }
            for (FileRecord f : files) {
                if (f.getCategoryId() != null) occupied.add(f.getCategoryId());
            }
            Set<String> ruleTargets = new HashSet<>();
            for (AutoRule r : rules) {
                if (r.categoryId != null) ruleTargets.add(r.categoryId);
            }
            List<String> doomed = AutoCleanPlanner.staleEmptyCategories(
                    categories, occupied, ruleTargets, Instant.now(),
                    AutoCleanPlanner.GRACE_DAYS);

            int foldersRemoved = 0;
            for (String id : doomed) {
                // Recheck right before writing — a concurrent save may have
                // landed in the folder after the snapshot above.
                if (c.recordRepository().countIn(id) > 0) continue;
                c.categoryRepository().delete(id);
                foldersRemoved++;
            }

            // Empty on-disk folders: the ones just deleted + orphans left
            // behind by earlier manual category deletes. Never touches files.
            Set<String> remainingIds = new HashSet<>();
            for (Category cat : c.categoryRepository().getAll()) remainingIds.add(cat.getId());
            int dirsRemoved = removeEmptyCategoryDirs(app, remainingIds);

            // --- plan: redundant tags on links (never deletes the link)
            Map<String, String> names = new HashMap<>();
            for (Category cat : categories) names.put(cat.getId(), cat.getName());
            int tagsRemoved = 0;
            for (LinkRecord r : links) {
                List<String> pruned = AutoCleanPlanner.pruneTags(
                        r.getTags(), names.get(r.getCategoryId()));
                if (pruned == null) continue;
                int before = r.getTags() == null ? 0 : r.getTags().size();
                c.recordRepository().saveTags(r.getId(), pruned);
                tagsRemoved += before - pruned.size();
            }

            if (foldersRemoved > 0 || dirsRemoved > 0 || tagsRemoved > 0) {
                c.indexRepository().invalidate();
                AiUpdateNotifier.fire(null);
            }

            SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            prefs.edit()
                    .putLong(KEY_LAST_RUN, System.currentTimeMillis())
                    .putString(KEY_LAST_STATS, foldersRemoved + " folder(s), "
                            + tagsRemoved + " tag(s) cleaned")
                    .apply();
            return Result.success();
        } catch (Exception e) {
            return Result.retry(); // Next periodic run (or backoff) retries.
        }
    }

    /**
     * Deletes {@code user/categories/<id>} directories that are empty and
     * have no category record (orphans / just-removed). Best-effort: a
     * non-empty directory is always left alone.
     */
    private static int removeEmptyCategoryDirs(App app, Set<String> remainingIds) {
        try {
            android.net.Uri treeUri = app.getContainer().permissionManager().getTreeUri();
            if (treeUri == null) return 0;
            DocumentTreeClient tree = new DocumentTreeClient(app, treeUri);
            String parentDocId = tree.resolveDocumentId(CATEGORIES_DIR);
            if (parentDocId == null) return 0;
            int removed = 0;
            for (DocumentTreeClient.Child ch : tree.listChildren(parentDocId)) {
                if (!DocumentTreeClient.MIME_DIR.equals(ch.mimeType)) continue;
                if (ch.displayName == null || remainingIds.contains(ch.displayName)) continue;
                List<DocumentTreeClient.Child> kids = tree.listChildren(ch.documentId);
                if (kids != null && kids.isEmpty() && tree.delete(ch.documentId)) {
                    removed++;
                }
            }
            return removed;
        } catch (RuntimeException e) {
            return 0; // best-effort sweep
        }
    }
}
