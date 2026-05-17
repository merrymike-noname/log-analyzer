package org.kovalenko.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kovalenko.job.analyzed.JobLogService;
import org.kovalenko.job.parser.LogParsingService;
import org.kovalenko.job.storage.JobFileStorage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobService {

    private final JobRepository jobRepository;
    private final JobFileStorage fileStorage;
    private final LogParsingService parsingService;
    private final JobLogService jobLogService;

    @Transactional
    public Job create(UUID userId, MultipartFile file) {
        validateUpload(file);

        UUID jobId = UUID.randomUUID();
        Job job = Job.builder()
                .id(jobId)
                .userId(userId)
                .originalFilename(file.getOriginalFilename())
                .fileSizeBytes(file.getSize())
                .status(JobStatus.QUEUED)
                .build();
        jobRepository.save(job);

        try {
            fileStorage.createJobDir(jobId);
            fileStorage.saveOriginal(jobId, file);

            job.setStatus(JobStatus.PROCESSING);
            job.setStartedAt(Instant.now());
            jobRepository.save(job);

            int lineCount = parsingService.parseToJsonl(
                    fileStorage.originalPath(jobId),
                    fileStorage.parsedPath(jobId)
            );

            // TEMPORARY: copy parsed as analyzed until ML worker is integrated
            Files.copy(fileStorage.parsedPath(jobId), fileStorage.analyzedPath(jobId));

            job.setLineCount(lineCount);
            job.setStatus(JobStatus.DONE);
            job.setFinishedAt(Instant.now());
            jobRepository.save(job);

            log.info("Job {} processed: {} lines parsed", jobId, lineCount);
            return job;

        } catch (IOException e) {
            log.error("Failed to process job {}", jobId, e);
            job.setStatus(JobStatus.FAILED);
            job.setErrorMessage("Processing failed: " + e.getMessage());
            job.setFinishedAt(Instant.now());
            jobRepository.save(job);
            throw new JobProcessingException("Failed to process job " + jobId, e);
        }
    }

    @Transactional(readOnly = true)
    public Job getByIdForUser(UUID jobId, UUID userId) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new JobNotFoundException(jobId));
        if (!job.getUserId().equals(userId)) {
            throw new JobNotFoundException(jobId);
        }
        return job;
    }

    @Transactional(readOnly = true)
    public Page<Job> findByUser(UUID userId, Pageable pageable) {
        return jobRepository.findByUserId(userId, pageable);
    }

    @Transactional
    public void delete(UUID jobId, UUID userId) {
        Job job = getByIdForUser(jobId, userId);
        jobRepository.delete(job);
        jobLogService.invalidate(jobId);
        try {
            deleteRecursively(fileStorage.jobDir(jobId));
        } catch (IOException e) {
            log.warn("Failed to delete job directory for {}", jobId, e);
        }
    }

    private void validateUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidUploadException("File is empty");
        }
        String name = file.getOriginalFilename();
        if (name == null || !(name.toLowerCase().endsWith(".log") || name.toLowerCase().endsWith(".txt"))) {
            throw new InvalidUploadException("Only .log or .txt files are accepted");
        }
    }

    private void deleteRecursively(java.nio.file.Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        try (Stream<java.nio.file.Path> walk = Files.walk(dir)) {
            walk.sorted(java.util.Comparator.reverseOrder())
                    .forEach(p -> {
                        try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                    });
        }
    }
}