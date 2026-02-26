package com.jdeploy.service;

import com.jdeploy.artifact.ArtifactStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Service
public class ArtifactRetentionCleanupService {

    private static final Logger log = LoggerFactory.getLogger(ArtifactRetentionCleanupService.class);

    private final ArtifactStorage artifactStorage;
    private final Duration retentionGracePeriod;

    public ArtifactRetentionCleanupService(ArtifactStorage artifactStorage,
                                           @Value("${jdeploy.artifact.cleanup.retention-grace-period:PT0S}") Duration retentionGracePeriod) {
        this.artifactStorage = artifactStorage;
        this.retentionGracePeriod = retentionGracePeriod;
    }

    @Scheduled(fixedDelayString = "${jdeploy.artifact.cleanup.interval:PT15M}",
            initialDelayString = "${jdeploy.artifact.cleanup.initial-delay:PT1M}")
    public void cleanupExpiredArtifacts() {
        List<String> deleted = artifactStorage.expireOlderThan(retentionGracePeriod);
        if (!deleted.isEmpty()) {
            log.info("Expired {} artifact(s): {}", deleted.size(), deleted);
        }
    }
}
