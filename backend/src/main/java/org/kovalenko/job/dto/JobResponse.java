package org.kovalenko.job.dto;

import org.kovalenko.job.Job;
import org.kovalenko.job.JobStatus;

import java.time.Instant;
import java.util.UUID;

public record JobResponse(
        UUID id,
        String originalFilename,
        long fileSizeBytes,
        Integer lineCount,
        JobStatus status,
        String errorMessage,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt
) {
    public static JobResponse from(Job job) {
        return new JobResponse(
                job.getId(),
                job.getOriginalFilename(),
                job.getFileSizeBytes(),
                job.getLineCount(),
                job.getStatus(),
                job.getErrorMessage(),
                job.getCreatedAt(),
                job.getStartedAt(),
                job.getFinishedAt()
        );
    }
}