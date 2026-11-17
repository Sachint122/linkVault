package com.linkvault.app.data.saf;

/** Storage-layer failure with a machine-readable reason for the UI. */
public class StorageException extends Exception {

    public enum Reason {
        NO_PERMISSION,
        FOLDER_MISSING,
        IO_ERROR,
        VALIDATION_FAILED,
        PROVIDER_UNSUPPORTED,
        NOT_FOUND,
        ALREADY_EXISTS,
        INVALID_NAME
    }

    private final Reason reason;

    public StorageException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public StorageException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason getReason() { return reason; }
}
