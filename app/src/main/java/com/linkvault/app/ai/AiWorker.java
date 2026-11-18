package com.linkvault.app.ai;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.linkvault.app.App;
import com.linkvault.app.data.repo.RecordRepository;
import com.linkvault.app.data.saf.StorageException;
import com.linkvault.app.di.AppContainer;
import com.linkvault.app.domain.model.Category;
import com.linkvault.app.domain.model.LinkRecord;
import com.linkvault.app.domain.model.VaultItem;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * The single sequential AI job. Loop: claim item → rule baseline → optional
 * LLM enrichment (model present) → validated merge → protection rules →
 * persist → live-UI notification. Never throws out of doWork (a retry with
 * backoff covers infrastructure hiccups; per-item attempt caps stop hot
 * loops).
 */
public class AiWorker extends Worker {

    public AiWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            App app = (App) getApplicationContext();
            AIQueueManager queue = app.getContainer().aiQueueManager();
            LocalAiManager ai = app.getContainer().localAiManager();
            long deadline = android.os.SystemClock.elapsedRealtime() + AiContract.JOB_BUDGET_MS;
            for (;;) {
                if (android.os.SystemClock.elapsedRealtime() > deadline) {
                    // Budget hit: items stay queued, WorkManager retries later.
                    return Result.retry();
                }
                String id = queue.beginItem();
                if (id == null) return Result.success();
                boolean ok = false;
                String reason = null;
                try {
                    ok = processOne(app, ai, id);
                } catch (Exception e) {
                    reason = String.valueOf(e.getMessage());
                }
                queue.finishItem(id, ok, reason);
            }
        } catch (Throwable t) {
            return Result.retry();
        }
    }

    private boolean processOne(App app, LocalAiManager ai, String id) throws StorageException {
        AppContainer c = app.getContainer();
        RecordRepository records = c.recordRepository();

        final LinkRecord rec;
        try {
            rec = records.findById(id);
        } catch (StorageException e) {
            if (e.getReason() == StorageException.Reason.NOT_FOUND) {
                return true; // record deleted/moved-away meanwhile → drop the job
            }
            throw e; // transient read error → counted as an attempt
        }

        // Dedupe: already analyzed (e.g. restored with analysis, or re-enqueued).
        if (LinkRecord.AI_STATUS_DONE.equals(rec.getAiStatus())) return true;

        LinkRecord working = rec;
        if (working.getAiStatus() == null) {
            // Marker so backups/UI see the record is queued (no updatedAt bump).
            working = working.withAiApplied(
                    working.getSummary(), working.getTags(), working.getCategoryId(),
                    LinkRecord.AI_STATUS_PENDING, working.getAiSource(),
                    working.getAiConfidence(), working.getAiAnalyzedAt(), working.getAiVersion());
            try {
                records.saveAi(working);
            } catch (StorageException ignored) {
                // best-effort marker
            }
        }

        // 1) Rule baseline — always, works with zero model installed.
        AiResult rules = RuleBasedAnalyzer.analyze(working.getUrl(), working.getTitle());
        AiResult result = rules;
        String source = LinkRecord.AI_SOURCE_RULE;

        // 2) LLM enrichment when a model is present — routed through the
        //    shared InferenceQueue so link AI and the writing sidebar never
        //    run two inferences at once (spec §6).
        if (ai.isModelReady()) {
            String raw = c.inferenceQueue().generateSync(
                    AiPrompt.SYSTEM,
                    AiPrompt.userPrompt(working.getUrl(), working.getTitle(),
                            working.getTags(), categoryNameOf(c, working.getCategoryId())),
                    AiContract.MAX_OUTPUT_TOKENS,
                    AiContract.GEN_TIMEOUT_MS);
            if (raw != null) {
                AiResult llm = AiResultValidator.parse(raw);
                if (llm != null) {
                    result = AiResult.merge(rules, llm);
                    source = LinkRecord.AI_SOURCE_LOCAL_AI;
                }
                // Unusable reply → rules stand; same prompt is deterministic,
                // so no retry loop (spec: controlled retries only).
            }
        }

        // 3) Apply with protection rules (never clobber user data).
        String categoryId = working.getCategoryId();
        if (Category.UNGROUPED_ID.equals(categoryId) && result.category != null) {
            String resolved = resolveOrCreateCategory(c, result.category);
            if (resolved != null) categoryId = resolved;
        }
        String summary = working.getSummary() != null
                ? working.getSummary() : result.summary; // fill-if-null, never overwrite
        List<String> tags = unionTags(working.getTags(), result.tags); // additive only

        LinkRecord applied = working.withAiApplied(
                summary, tags, categoryId,
                LinkRecord.AI_STATUS_DONE, source, result.confidence,
                Instant.now().toString(), AiContract.AI_VERSION);
        records.saveAi(applied);

        // 4) Live UI: refresh the cached item + notify listeners.
        c.indexRepository().replaceItem(new VaultItem(applied));
        AiUpdateNotifier.fire(applied.getId());
        return true;
    }

    @Nullable
    private String categoryNameOf(AppContainer c, String categoryId) {
        try {
            Category cat = c.categoryRepository().findById(categoryId);
            return cat == null ? null : cat.getName();
        } catch (Exception e) {
            return null;
        }
    }

    /** Match an existing category by name (case-insensitive) or create it. */
    @Nullable
    private String resolveOrCreateCategory(AppContainer c, String rawName) {
        String name = AiResultValidator.sanitizeCategory(rawName);
        if (name == null) return null;
        try {
            for (Category cat : c.categoryRepository().getAll()) {
                if (cat.getName().equalsIgnoreCase(name)) return cat.getId();
            }
        } catch (Exception e) {
            return null;
        }
        try {
            return c.categoryRepository().create(name).getId();
        } catch (Exception e) {
            // Race (created concurrently) or invalid name — re-check by name.
            try {
                for (Category cat : c.categoryRepository().getAll()) {
                    if (cat.getName().equalsIgnoreCase(name)) return cat.getId();
                }
            } catch (Exception ignored) {
                // leave ungrouped; never fail the job over a category
            }
            return null;
        }
    }

    /** Base tags first, AI tags appended (case-insensitive dedupe), cap 20. */
    private static List<String> unionTags(List<String> base, List<String> add) {
        List<String> out = new ArrayList<>(base);
        for (String t : add) {
            if (out.size() >= AiContract.MAX_RECORD_TAGS) break;
            boolean dup = false;
            for (String e : out) {
                if (e.equalsIgnoreCase(t)) {
                    dup = true;
                    break;
                }
            }
            if (!dup) out.add(t);
        }
        return out;
    }
}
