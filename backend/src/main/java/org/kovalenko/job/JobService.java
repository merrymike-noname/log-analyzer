package org.kovalenko.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kovalenko.job.parser.LogParsingService;
import org.kovalenko.job.storage.JobFileStorage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobService {

    private final JobRepository jobRepository;
    private final JobFileStorage fileStorage;
    private final LogParsingService parsingService;

    /**
     * Temporary implementation without queue: saves file, parses it synchronously,
     * marks the job as DONE. Will be replaced when RabbitMQ integration is added.
     */
    @Transactional
    public Job create(UUID userId, MultipartFile file) {
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
}