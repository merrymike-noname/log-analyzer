package org.kovalenko.job.queue;

import java.util.UUID;

public record AnalyzeTaskMessage(
        UUID jobId,
        String parsedPath
) {
}