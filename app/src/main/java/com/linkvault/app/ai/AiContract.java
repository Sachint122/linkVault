package com.linkvault.app.ai;

/** Shared constants for the on-device AI pipeline. */
public final class AiContract {

    private AiContract() {}

    /** Analyzer output contract version (bump when prompt/fields change). */
    public static final int AI_VERSION = 1;

    /** Per-record attempt cap before a job is marked failed. */
    public static final int MAX_ATTEMPTS = 3;

    /** Internal queue file (app-internal storage, never in the vault folder). */
    public static final String QUEUE_FILE = "ai_queue.json";

    /** Model file name inside {@code filesDir} (see AI_MODEL_SETUP.md). */
    public static final String MODEL_FILE_NAME = "ai_model.litertlm";

    /**
     * Time budget for one worker run. When exceeded the worker returns
     * Result.retry() and continues later — keeps the job well under the
     * system's long-job limits.
     */
    public static final long JOB_BUDGET_MS = 8 * 60 * 1000L;

    /** Hard cap for one generation call (hung native calls are abandoned). */
    public static final long GEN_TIMEOUT_MS = 90_000L;

    /** Output token cap for the JSON reply. */
    public static final int MAX_OUTPUT_TOKENS = 320;

    /**
     * Output token cap for writing-assistant replies (sidebar). Higher than
     * the JSON cap: Expand/Translate can legitimately run long, and
     * WritingValidator.sanitize() enforces the final char limit anyway.
     */
    public static final int MAX_WRITE_TOKENS = 640;

    /** Overlay window tag for the AI Sidebar (used by WindowManager). */
    public static final String SIDEBAR_WINDOW_TAG = "linkvault-ai-sidebar";

    /** Record-level tag cap (mirrors LinkCodec write-side limit). */
    public static final int MAX_RECORD_TAGS = 20;

    /** AI-produced tags per analysis. */
    public static final int MAX_AI_TAGS = 5;

    /** Summary cap enforced before writing. */
    public static final int MAX_SUMMARY_CHARS = 200;

    /** Category name cap enforced before writing. */
    public static final int MAX_CATEGORY_CHARS = 24;
}
