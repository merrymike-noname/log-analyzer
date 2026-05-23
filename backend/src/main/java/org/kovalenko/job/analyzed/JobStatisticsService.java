package org.kovalenko.job.analyzed;

import com.github.benmanes.caffeine.cache.Cache;
import lombok.RequiredArgsConstructor;
import org.kovalenko.job.JobProcessingException;
import org.kovalenko.job.Severity;
import org.kovalenko.job.dto.JobStatisticsResponse;
import org.kovalenko.job.dto.JobStatisticsResponse.ScoreDistribution;
import org.kovalenko.job.dto.JobStatisticsResponse.TemplateStat;
import org.kovalenko.job.storage.JobFileStorage;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JobStatisticsService {

    private static final int TOP_TEMPLATES_LIMIT = 5;

    private final Cache<UUID, List<AnalyzedLogEntry>> cache;
    private final AnalyzedLogReader reader;
    private final JobFileStorage fileStorage;

    public JobStatisticsResponse compute(UUID jobId) {
        List<AnalyzedLogEntry> entries = loadCached(jobId);

        if (entries.isEmpty()) {
            return new JobStatisticsResponse(
                    0, 0,
                    new EnumMap<>(Severity.class),
                    List.of(),
                    new ScoreDistribution(null, null, null, null, null)
            );
        }

        return new JobStatisticsResponse(
                entries.size(),
                countUniqueTemplates(entries),
                severityBreakdown(entries),
                topTemplates(entries),
                scoreDistribution(entries)
        );
    }

    private List<AnalyzedLogEntry> loadCached(UUID jobId) {
        return cache.get(jobId, id -> {
            Path file = fileStorage.analyzedPath(id);
            if (!Files.exists(file)) {
                return List.of();
            }
            try {
                return reader.readAll(file);
            } catch (IOException e) {
                throw new JobProcessingException("Failed to read analyzed logs for job " + id, e);
            }
        });
    }

    private int countUniqueTemplates(List<AnalyzedLogEntry> entries) {
        return (int) entries.stream()
                .map(AnalyzedLogEntry::templateId)
                .filter(t -> t != null)
                .distinct()
                .count();
    }

    private Map<Severity, Integer> severityBreakdown(List<AnalyzedLogEntry> entries) {
        Map<Severity, Integer> result = new EnumMap<>(Severity.class);
        for (Severity s : Severity.values()) {
            result.put(s, 0);
        }
        for (AnalyzedLogEntry e : entries) {
            if (e.severity() != null) {
                result.merge(e.severity(), 1, Integer::sum);
            }
        }
        return result;
    }

    private List<TemplateStat> topTemplates(List<AnalyzedLogEntry> entries) {
        Map<String, int[]> counts = new HashMap<>();
        Map<String, String> templates = new HashMap<>();

        for (AnalyzedLogEntry e : entries) {
            if (e.templateId() == null) continue;
            counts.computeIfAbsent(e.templateId(), k -> new int[1])[0]++;
            templates.putIfAbsent(e.templateId(), e.template());
        }

        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, int[]>comparingByValue(
                        Comparator.comparingInt(arr -> -arr[0])
                ))
                .limit(TOP_TEMPLATES_LIMIT)
                .map(entry -> new TemplateStat(
                        entry.getKey(),
                        templates.get(entry.getKey()),
                        entry.getValue()[0]
                ))
                .toList();
    }

    private ScoreDistribution scoreDistribution(List<AnalyzedLogEntry> entries) {
        double[] scores = entries.stream()
                .map(AnalyzedLogEntry::anomalyScore)
                .filter(s -> s != null)
                .mapToDouble(Double::doubleValue)
                .sorted()
                .toArray();

        if (scores.length == 0) {
            return new ScoreDistribution(null, null, null, null, null);
        }

        return new ScoreDistribution(
                round(scores[0]),
                round(scores[scores.length / 2]),
                round(scores[(int) (scores.length * 0.95)]),
                round(scores[(int) (scores.length * 0.99)]),
                round(scores[scores.length - 1])
        );
    }

    private double round(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }
}