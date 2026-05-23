package org.kovalenko.job.analyzed;

import com.github.benmanes.caffeine.cache.Cache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kovalenko.job.JobProcessingException;
import org.kovalenko.job.Severity;
import org.kovalenko.job.dto.LogFilterRequest;
import org.kovalenko.job.storage.JobFileStorage;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobExportService {

    private final Cache<UUID, List<AnalyzedLogEntry>> cache;
    private final AnalyzedLogReader reader;
    private final JobFileStorage fileStorage;

    public StreamingResponseBody buildCsvStream(UUID jobId, LogFilterRequest filter, char delimiter) {
        List<AnalyzedLogEntry> filtered = applyFilters(loadCached(jobId), filter);

        return outputStream -> {
            try (Writer writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)) {
                CsvWriter csv = new CsvWriter(writer, delimiter);
                csv.writeHeader();
                for (AnalyzedLogEntry entry : filtered) {
                    csv.writeEntry(entry);
                }
                writer.flush();
            } catch (IOException e) {
                log.warn("CSV export interrupted for job {}: {}", jobId, e.getMessage());
            }
        };
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

    private List<AnalyzedLogEntry> applyFilters(List<AnalyzedLogEntry> all, LogFilterRequest filter) {
        return all.stream()
                .filter(e -> matchesSeverity(e, filter.severity()))
                .filter(e -> matchesComponent(e, filter.component()))
                .filter(e -> matchesSearch(e, filter.search()))
                .sorted(buildComparator(filter))
                .toList();
    }

    private boolean matchesSeverity(AnalyzedLogEntry entry, java.util.Set<Severity> filter) {
        if (filter == null || filter.isEmpty()) return true;
        return entry.severity() != null && filter.contains(entry.severity());
    }

    private boolean matchesComponent(AnalyzedLogEntry entry, String component) {
        if (component == null || component.isBlank()) return true;
        return component.equalsIgnoreCase(entry.component());
    }

    private boolean matchesSearch(AnalyzedLogEntry entry, String search) {
        if (search == null || search.isBlank()) return true;
        String q = search.toLowerCase();
        return entry.content() != null && entry.content().toLowerCase().contains(q);
    }

    private Comparator<AnalyzedLogEntry> buildComparator(LogFilterRequest filter) {
        Comparator<AnalyzedLogEntry> comparator = switch (filter.sortBy() == null ? "lineId" : filter.sortBy()) {
            case "severity" -> Comparator.comparing(
                    (Function<AnalyzedLogEntry, Integer>) e -> e.severity() == null ? -1 : e.severity().ordinal()
            );
            case "anomalyScore" -> Comparator.comparing(
                    AnalyzedLogEntry::anomalyScore, Comparator.nullsFirst(Comparator.naturalOrder())
            );
            case "timestamp" -> Comparator.comparing(
                    AnalyzedLogEntry::timestamp, Comparator.nullsFirst(Comparator.naturalOrder())
            );
            default -> Comparator.comparingLong(AnalyzedLogEntry::lineId);
        };
        return filter.isDescending() ? comparator.reversed() : comparator;
    }
}