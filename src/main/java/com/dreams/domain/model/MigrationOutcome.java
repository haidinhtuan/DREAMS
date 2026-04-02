package com.dreams.domain.model;

import java.time.LocalDateTime;

/**
 * Records the outcome of a migration for feedback loop analysis.
 * Compares predicted QoS improvement against observed results.
 */
public record MigrationOutcome(
        String microserviceId,
        String targetClusterId,
        double predictedImprovement,
        double actualImprovement,
        double predictionRatio,
        boolean successful,
        boolean needsRollback,
        LocalDateTime validatedAt
) {}
