package org.kovalenko.job.parser;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
@Service
@RequiredArgsConstructor
public class LogParsingService {

    private final CbsLogParser parser;
    private final ObjectMapper objectMapper;

    /**
     * Streams source file line by line, parses each line and writes JSONL to target.
     * Returns the number of successfully parsed entries.
     */
    public int parseToJsonl(Path source, Path target) throws IOException {
        int parsedCount = 0;
        int skippedCount = 0;
        long lineId = 0;

        try (BufferedReader reader = Files.newBufferedReader(source, StandardCharsets.UTF_8);
             BufferedWriter writer = Files.newBufferedWriter(target, StandardCharsets.UTF_8)) {

            String line;
            while ((line = reader.readLine()) != null) {
                lineId++;
                var entryOpt = parser.parseLine(line, lineId);

                if (entryOpt.isEmpty()) {
                    skippedCount++;
                    continue;
                }

                writer.write(objectMapper.writeValueAsString(entryOpt.get()));
                writer.newLine();
                parsedCount++;
            }
        }

        log.info("Parsed {} lines, skipped {} from {}", parsedCount, skippedCount, source);
        return parsedCount;
    }
}