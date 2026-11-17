package com.linkvault.app.domain.model;

/** A detected problem with a stored record — reported, never auto-deleted. */
public final class RecordIssue {

    public enum Kind {
        MALFORMED_JSON,
        SCHEMA_INVALID,
        NEWER_SCHEMA,
        UNREADABLE
    }

    private final Kind kind;
    private final String relativePath;
    private final String detail;

    public RecordIssue(Kind kind, String relativePath, String detail) {
        this.kind = kind;
        this.relativePath = relativePath;
        this.detail = detail;
    }

    public Kind getKind() { return kind; }
    public String getRelativePath() { return relativePath; }
    public String getDetail() { return detail; }

    @Override
    public String toString() {
        return kind + " @ " + relativePath + " (" + detail + ")";
    }
}
