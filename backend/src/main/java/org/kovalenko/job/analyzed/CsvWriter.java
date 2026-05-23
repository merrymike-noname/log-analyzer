package org.kovalenko.job.analyzed;

import org.kovalenko.job.Severity;

import java.io.IOException;
import java.io.Writer;

/**
 * Minimal RFC 4180-compliant CSV writer for analyzed log entries.
 * Stateless utility; writes via the provided Writer for streaming.
 */
final class CsvWriter {

    private static final String[] HEADER = {
            "lineId", "timestamp", "level", "component",
            "severity", "anomalyScore", "templateId", "template", "content"
    };

    private final Writer writer;
    private final char delimiter;

    CsvWriter(Writer writer, char delimiter) {
        this.writer = writer;
        this.delimiter = delimiter;
    }

    void writeHeader() throws IOException {
        writeRow(HEADER);
    }

    void writeEntry(AnalyzedLogEntry entry) throws IOException {
        writeRow(new String[]{
                String.valueOf(entry.lineId()),
                nullToEmpty(entry.timestamp()),
                nullToEmpty(entry.level()),
                nullToEmpty(entry.component()),
                severity(entry.severity()),
                score(entry.anomalyScore()),
                nullToEmpty(entry.templateId()),
                nullToEmpty(entry.template()),
                nullToEmpty(entry.content()),
        });
    }

    private void writeRow(String[] values) throws IOException {
        for (int i = 0; i < values.length; i++) {
            if (i > 0) writer.write(delimiter);
            writer.write(escape(values[i]));
        }
        writer.write("\r\n");
    }

    /**
     * RFC 4180: wrap in quotes if value contains delimiter, quote, CR or LF.
     * Inner quotes are doubled.
     */
    private String escape(String value) {
        if (value == null || value.isEmpty()) return "";
        boolean needsQuoting = value.indexOf(delimiter) >= 0
                || value.indexOf('"') >= 0
                || value.indexOf('\n') >= 0
                || value.indexOf('\r') >= 0;
        if (!needsQuoting) return value;
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private String severity(Severity s) {
        return s == null ? "" : s.name();
    }

    private String score(Double s) {
        return s == null ? "" : String.format(java.util.Locale.US, "%.4f", s);
    }
}