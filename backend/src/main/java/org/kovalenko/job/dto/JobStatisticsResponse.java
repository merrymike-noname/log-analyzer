package org.kovalenko.job.dto;

import org.kovalenko.job.Severity;

import java.util.List;
import java.util.Map;

public record JobStatisticsResponse(
        int totalEntries,
        int uniqueTemplates,
        Map<Severity, Integer> severityBreakdown,
        List<TemplateStat> topTemplates,
        ScoreDistribution scoreDistribution
) {
    public record TemplateStat(String templateId, String template, int count) {}

    public record ScoreDistribution(
            Double min,
            Double median,
            Double p95,
            Double p99,
            Double max
    ) {}
}