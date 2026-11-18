package com.linkvault.app.ai;

import androidx.annotation.Nullable;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Tiny pub/sub so worker-side writes refresh the browse UI live (the worker
 * runs in the same process). Listeners must be cheap — they are called on
 * the worker thread.
 */
public final class AiUpdateNotifier {

    public interface Listener {
        void onAiUpdated(@Nullable String recordId);
    }

    private static final CopyOnWriteArrayList<Listener> LISTENERS = new CopyOnWriteArrayList<>();

    private AiUpdateNotifier() {}

    public static void add(Listener listener) {
        if (listener != null) LISTENERS.add(listener);
    }

    public static void remove(Listener listener) {
        LISTENERS.remove(listener);
    }

    public static void fire(@Nullable String recordId) {
        for (Listener l : LISTENERS) {
            try {
                l.onAiUpdated(recordId);
            } catch (RuntimeException ignored) {
                // one bad listener must not break the pipeline
            }
        }
    }
}
