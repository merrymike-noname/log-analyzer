package org.kovalenko.job.parser;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"lineId", "timestamp", "level", "component", "content"})
public record ParsedLogEntry(
        long lineId,
        String timestamp,
        String level,
        String component,
        String content
) {
}