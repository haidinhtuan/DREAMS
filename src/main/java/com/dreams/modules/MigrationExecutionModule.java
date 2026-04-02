package com.dreams.modules;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

/**
 * Migration Execution Module (MEM) — Migration Orchestrator, Rollback Manager,
 * and Health Assurance Validator.
 * Handles the actual execution of approved migration decisions.
 *
 * The execution logic is implemented in:
 * - {@link com.dreams.infrastructure.adapter.out.pekko.MigrationOrchestrator} (Raft commit + leader change)
 * - {@link com.dreams.application.service.MigrationServiceMock} (simulated migration for evaluation)
 */
@ApplicationScoped
@Slf4j
public class MigrationExecutionModule {
    // Migration execution is handled by MigrationOrchestrator and the Raft commit flow.
    // In production, this would integrate with Kubernetes APIs for actual container migration.
    // Currently uses MigrationServiceMock for evaluation purposes.
}
