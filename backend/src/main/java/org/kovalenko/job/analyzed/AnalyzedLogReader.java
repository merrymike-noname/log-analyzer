package org.kovalenko.job.analyzed;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AnalyzedLogReader {

    private final ObjectMapper objectMapper;

    public List<AnalyzedLogEntry> readAll(Path file) throws IOException {
        List<AnalyzedLogEntry> entries = new ArrayList<>();

        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                try {
                    entries.add(objectMapper.readValue(line, AnalyzedLogEntry.class));
                } catch (IOException e) {
                    log.warn("Failed to parse JSONL line, skipping: {}", e.getMessage());
                }
            }
        }

        return entries;
    }
}