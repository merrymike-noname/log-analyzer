package org.kovalenko.job.storage;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kovalenko.job.StorageProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class JobFileStorage {

    public static final String ORIGINAL_FILE = "original.log";
    public static final String PARSED_FILE = "parsed.jsonl";
    public static final String ANALYZED_FILE = "analyzed.jsonl";

    private final StorageProperties properties;

    @PostConstruct
    void init() throws IOException {
        Path root = Path.of(properties.dataDir());
        Files.createDirectories(root);
        log.info("Job storage initialized at {}", root.toAbsolutePath());
    }

    public Path jobDir(UUID jobId) {
        return Path.of(properties.dataDir(), jobId.toString());
    }

    public Path createJobDir(UUID jobId) throws IOException {
        Path dir = jobDir(jobId);
        Files.createDirectories(dir);
        return dir;
    }

    public Path originalPath(UUID jobId) {
        return jobDir(jobId).resolve(ORIGINAL_FILE);
    }

    public Path parsedPath(UUID jobId) {
        return jobDir(jobId).resolve(PARSED_FILE);
    }

    public Path analyzedPath(UUID jobId) {
        return jobDir(jobId).resolve(ANALYZED_FILE);
    }

    public long saveOriginal(UUID jobId, MultipartFile file) throws IOException {
        Path target = originalPath(jobId);
        file.transferTo(target);
        return Files.size(target);
    }
}