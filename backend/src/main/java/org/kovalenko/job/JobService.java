package org.kovalenko.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kovalenko.job.analyzed.JobLogService;
import org.kovalenko.job.parser.LogParsingService;
import org.kovalenko.job.queue.AnalyzeTaskMessage;
import org.kovalenko.job.queue.JobProducer;
import org.kovalenko.job.queue.JobResultMessage;
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
    private final JobProducer jobProducer;

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
                .createdAt(Instant.now())
                .build();
        jobRepository.save(job);

        try {
            fileStorage.createJobDir(jobId);
            fileStorage.saveOriginal(jobId, file);

            int lineCount = parsingService.parseToJsonl(
                    fileStorage.originalPath(jobId),
                    fileStorage.parsedPath(jobId)
            );
            job.setLineCount(lineCount);
            jobRepository.save(job);

            AnalyzeTaskMessage message = new AnalyzeTaskMessage(
                    jobId,
                    fileStorage.parsedPath(jobId).toString()
            );
            jobProducer.publishAnalyzeTask(message);

            log.info("Job {} queued for analysis ({} lines parsed)", jobId, lineCount);
            return job;

        } catch (IOException e) {
            log.error("Failed to prepare job {}", jobId, e);
            job.setStatus(JobStatus.FAILED);
            job.setErrorMessage("Preparation failed: " + e.getMessage());
            job.setFinishedAt(Instant.now());
            jobRepository.save(job);
            throw new JobProcessingException("Failed to prepare job " + jobId, e);
        }
    }

    /**
     * Called by JobResultConsumer when the ML worker reports a status update.
     * Handles three statuses: STARTED, DONE, FAILED.
     */
    @Transactional
    public void applyResult(JobResultMessage message) {
        Job job = jobRepository.findById(message.jobId())
                .orElseThrow(() -> new JobNotFoundException(message.jobId()));

        switch (message.status()) {
            case "STARTED" -> {
                job.setStatus(JobStatus.PROCESSING);
                job.setStartedAt(Instant.now());
            }
            case "DONE" -> {
                job.setStatus(JobStatus.DONE);
                if (message.lineCount() != null) {
                    job.setLineCount(message.lineCount());
                }
                job.setFinishedAt(Instant.now());
                jobLogService.invalidate(message.jobId());
            }
            case "FAILED" -> {
                job.setStatus(JobStatus.FAILED);
                job.setErrorMessage(message.errorMessage());
                job.setFinishedAt(Instant.now());
                jobLogService.invalidate(message.jobId());
            }
            default -> {
                log.warn("Unknown status received for job {}: {}", message.jobId(), message.status());
                return;
            }
        }

        jobRepository.save(job);
        log.info("Job {} -> {}", message.jobId(), job.getStatus());
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