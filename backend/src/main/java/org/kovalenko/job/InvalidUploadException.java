package org.kovalenko.job;

public class InvalidUploadException extends RuntimeException {
    public InvalidUploadException(String message) {
        super(message);
    }
}