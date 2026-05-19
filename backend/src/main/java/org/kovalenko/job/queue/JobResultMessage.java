package org.kovalenko.job.queue;

import java.util.UUID;

public record JobResultMessage(
        UUID jobId,
        String status,           // "DONE" or "FAILED"
        String errorMessage,     // nullable, present for FAILED
        Integer lineCount        // nullable, present for DONE
) {
}