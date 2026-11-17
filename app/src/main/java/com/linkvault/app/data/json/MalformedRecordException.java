package com.linkvault.app.data.json;

import com.linkvault.app.domain.model.RecordIssue;

/** A stored JSON file could not be parsed or failed schema validation. */
public class MalformedRecordException extends Exception {

    public enum Kind { MALFORMED_JSON, SCHEMA_INVALID, NEWER_SCHEMA, UNREADABLE }

    private final Kind kind;
    private final String relativePath;

    public MalformedRecordException(Kind kind, String relativePath, String message) {
        super(message);
        this.kind = kind;
        this.relativePath = relativePath;
    }

    public MalformedRecordException(Kind kind, String relativePath, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
        this.relativePath = relativePath;
    }

    public Kind getKind() { return kind; }
    public String getRelativePath() { return relativePath; }

    public RecordIssue toIssue() {
        RecordIssue.Kind k = switch (kind) {
            case MALFORMED_JSON -> RecordIssue.Kind.MALFORMED_JSON;
            case SCHEMA_INVALID -> RecordIssue.Kind.SCHEMA_INVALID;
            case NEWER_SCHEMA -> RecordIssue.Kind.NEWER_SCHEMA;
            case UNREADABLE -> RecordIssue.Kind.UNREADABLE;
        };
        return new RecordIssue(k, relativePath, getMessage());
    }
}
