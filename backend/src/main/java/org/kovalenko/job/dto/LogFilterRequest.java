package org.kovalenko.job.dto;

import org.kovalenko.job.Severity;

import java.util.Set;

public record LogFilterRequest(
        Set<Severity> severity,
        String component,
        String search,
        String sortBy,
        String sortDir,
        int page,
        int size
) {
    public boolean isDescending() {
        return "desc".equalsIgnoreCase(sortDir);
    }
}