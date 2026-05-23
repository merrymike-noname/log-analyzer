package org.kovalenko.job;

import lombok.RequiredArgsConstructor;
import org.kovalenko.common.dto.PageResponse;
import org.kovalenko.job.analyzed.AnalyzedLogEntry;
import org.kovalenko.job.analyzed.JobLogService;
import org.kovalenko.job.analyzed.JobStatisticsService;
import org.kovalenko.job.dto.JobResponse;
import org.kovalenko.job.dto.JobStatisticsResponse;
import org.kovalenko.job.dto.LogFilterRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
public class JobController {

    private final JobService jobService;
    private final JobLogService jobLogService;
    private final JobStatisticsService jobStatisticsService;

    @PostMapping
    public ResponseEntity<JobResponse> upload(
            @AuthenticationPrincipal UUID userId,
            @RequestParam("file") MultipartFile file) {
        Job job = jobService.create(userId, file);
        return ResponseEntity.ok(JobResponse.from(job));
    }

    @GetMapping
    public ResponseEntity<PageResponse<JobResponse>> list(
            @AuthenticationPrincipal UUID userId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<Job> jobs = jobService.findByUser(userId, pageable);
        return ResponseEntity.ok(PageResponse.from(jobs, JobResponse::from));
    }

    @GetMapping("/{id}")
    public ResponseEntity<JobResponse> get(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID id) {
        Job job = jobService.getByIdForUser(id, userId);
        return ResponseEntity.ok(JobResponse.from(job));
    }

    @GetMapping("/{id}/logs")
    public ResponseEntity<PageResponse<AnalyzedLogEntry>> logs(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID id,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String component,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false, defaultValue = "asc") String sortDir,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {

        jobService.getByIdForUser(id, userId); // ownership check

        Set<Severity> severities = parseSeverity(severity);
        LogFilterRequest filter = new LogFilterRequest(severities, component, search, sortBy, sortDir, page, size);
        return ResponseEntity.ok(jobLogService.query(id, filter));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID id) {
        jobService.delete(id, userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/statistics")
    public ResponseEntity<JobStatisticsResponse> statistics(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID id) {
        jobService.getByIdForUser(id, userId);
        return ResponseEntity.ok(jobStatisticsService.compute(id));
    }

    private Set<Severity> parseSeverity(String raw) {
        if (raw == null || raw.isBlank()) return Set.of();
        return java.util.Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> Severity.valueOf(s.toUpperCase()))
                .collect(Collectors.toSet());
    }
}