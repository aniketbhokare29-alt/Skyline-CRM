package com.saivandan.crm;

/** Thrown when an insert violates a uniqueness rule (mapped to HTTP 409). */
public class DuplicateException extends RuntimeException {
    public DuplicateException(String message, Throwable cause) {
        super(message, cause);
    }
}
