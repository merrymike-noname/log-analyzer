package org.kovalenko.job.parser;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses single line of Windows CBS log into structured entry.
 * Expected format: "yyyy-MM-dd HH:mm:ss, Level   Component   Content"
 */
@Slf4j
@Component
public class CbsLogParser {

    private static final Pattern LINE_PATTERN = Pattern.compile(
            "^(\\d{4}-\\d{2}-\\d{2})\\s+(\\d{2}:\\d{2}:\\d{2}),\\s+(\\S+)\\s+(\\S+)\\s+(.*)$"
    );

    public Optional<ParsedLogEntry> parseLine(String line, long lineId) {
        if (line == null || line.isBlank()) {
            return Optional.empty();
        }

        Matcher matcher = LINE_PATTERN.matcher(line);
        if (!matcher.matches()) {
            return Optional.empty();
        }

        String timestamp = matcher.group(1) + "T" + matcher.group(2);
        String level = matcher.group(3);
        String component = matcher.group(4);
        String content = matcher.group(5);

        return Optional.of(new ParsedLogEntry(lineId, timestamp, level, component, content));
    }
}