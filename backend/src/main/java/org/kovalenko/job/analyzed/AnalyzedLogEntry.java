package org.kovalenko.job.analyzed;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.kovalenko.job.Severity;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AnalyzedLogEntry(
        long lineId,
        String timestamp,
        String level,
        String component,
        String content,
        String templateId,
        String template,
        Double anomalyScore,
        Severity severity
) {
}