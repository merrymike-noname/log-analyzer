package org.kovalenko.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.kovalenko.job.analyzed.AnalyzedLogEntry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Configuration
public class CacheConfig {

    @Bean
    public Cache<UUID, List<AnalyzedLogEntry>> analyzedLogsCache() {
        return Caffeine.newBuilder()
                .expireAfterAccess(Duration.ofMinutes(10))
                .maximumSize(50)
                .build();
    }
}