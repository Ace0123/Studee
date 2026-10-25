package com.studymate.service;

/** Thrown when a database operation fails. Unchecked so beginners don't need try/catch everywhere. */
public class DatabaseException extends RuntimeException {
    public DatabaseException(String message, Throwable cause) {
        super(message, cause);
    }
}
