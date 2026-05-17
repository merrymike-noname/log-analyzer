package org.kovalenko.job;

public class JobProcessingException extends RuntimeException {
    public JobProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}