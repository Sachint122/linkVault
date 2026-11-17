package com.linkvault.app.ui.common;

import androidx.annotation.Nullable;

/** One-shot LiveData event wrapper (consumed on first observe). */
public class Event<T> {

    private final T content;
    private boolean handled = false;

    public Event(T content) {
        this.content = content;
    }

    @Nullable
    public T getContent() {
        if (handled) return null;
        handled = true;
        return content;
    }
}
