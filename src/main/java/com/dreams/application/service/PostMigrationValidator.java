package com.dreams.application.service;

import com.dreams.domain.model.MigrationAction;
import com.dreams.domain.model.MigrationOutcome;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Post-Migration Validation Service — part of the Migration Execution Module (MEM).
 * Evaluates whether executed migrations actually improved QoS by comparing
 * predicted improvement scores against observed outcomes.
 *
 * Implements a feedback loop: if a migration degrades QoS beyond a threshold,
 * it is flagged for potential rollback. Outcomes are tracked for parameter tuning.
 */
@ApplicationScoped
@Slf4j
public class PostMigrationValidator {

    @Inject
    ServiceHealthMonitor serviceHealthMonitor;

    @Inject
    InterDomainLatencyMonitor latencyMonitor;

    private final Map<String, MigrationAction> pendingValidations = new ConcurrentHashMap<>();
    private final Map<String, MigrationOutcome> outcomes = new ConcurrentHashMap<>();

    /** Validation window: how long to wait after migration before evaluating */
    private static final Duration VALIDATION_DELAY = Duration.ofSeconds(30);

    /** Threshold: if actual QoS degraded by more than this fraction, flag for rollback */
    private static final double DEGRADATION_THRESHOLD = -0.1;

    /**
     * Register a migration for post-execution validation.
     */
    public void registerMigration(MigrationAction action) {
        String key = action.microservice().getId() + "-" + action.createdAt();
        pendingValidations.put(key, action);
        log.info("Registered migration of {} for post-validation (predicted improvement: {})",
                action.microservice().getId(), action.improvementScore());
    }

    /**
     * Validate a completed migration by comparing predicted vs actual QoS.
     * Should be called after the validation delay has elapsed.
     */
    public MigrationOutcome validate(MigrationAction action) {
        String microserviceId = action.microservice().getId();
        double predictedImprovement = action.improvementScore();

        // Measure current state
        double currentLocalAffinity = 0.0;
        var microservice = serviceHealthMonitor.getMicroserviceById(microserviceId)
                .await().indefinitely();
        if (microservice != null) {
            currentLocalAffinity = microservice.getTotalLocalAffinity();
        }

        // Compare: positive delta means improvement
        // Simple heuristic: if the microservice now has higher local affinity, migration helped
        double actualImprovement = currentLocalAffinity;
        double ratio = predictedImprovement != 0 ? actualImprovement / predictedImprovement : 0;

        boolean successful = ratio > DEGRADATION_THRESHOLD;
        boolean needsRollback = ratio < DEGRADATION_THRESHOLD;

        MigrationOutcome outcome = new MigrationOutcome(
                microserviceId,
                action.targetK8sCluster().getId(),
                predictedImprovement,
                actualImprovement,
                ratio,
                successful,
                needsRollback,
                LocalDateTime.now()
        );

        String key = microserviceId + "-" + action.createdAt();
        outcomes.put(key, outcome);
        pendingValidations.remove(key);

        if (needsRollback) {
            log.warn("ROLLBACK RECOMMENDED: Migration of {} degraded QoS (predicted: {}, actual: {}, ratio: {})",
                    microserviceId, predictedImprovement, actualImprovement, ratio);
        } else if (successful) {
            log.info("Migration of {} validated successfully (predicted: {}, actual: {}, ratio: {})",
                    microserviceId, predictedImprovement, actualImprovement, ratio);
        }

        return outcome;
    }

    /**
     * Get all tracked migration outcomes for parameter tuning feedback.
     */
    public Map<String, MigrationOutcome> getOutcomes() {
        return outcomes;
    }

    /**
     * Get average prediction accuracy across all validated migrations.
     * Returns ratio of actual/predicted improvements, averaged.
     */
    public double getAveragePredictionAccuracy() {
        if (outcomes.isEmpty()) return 0.0;
        return outcomes.values().stream()
                .mapToDouble(MigrationOutcome::predictionRatio)
                .average()
                .orElse(0.0);
    }

    /**
     * Get count of migrations that required rollback.
     */
    public long getRollbackCount() {
        return outcomes.values().stream()
                .filter(MigrationOutcome::needsRollback)
                .count();
    }
}
