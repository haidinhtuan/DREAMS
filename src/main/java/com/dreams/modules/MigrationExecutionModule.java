package com.dreams.modules;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

/**
 * Migration Execution Module (MEM) — Migration Orchestrator, Rollback Manager,
 * and Health Assurance Validator.
 * Handles the actual execution of approved migration decisions.
 *
 * The execution logic is implemented in:
 * - {@link com.dreams.infrastructure.adapter.out.pekko.MigrationExecutor} (Raft commit + leader change)
 * - {@link com.dreams.application.service.TestMigrationServiceMock} (simulated migration for evaluation)
 */
@ApplicationScoped
@Slf4j
public class MigrationExecutionModule {
    // Migration execution is handled by MigrationExecutor and the Raft commit flow.
    // In production, this would integrate with Kubernetes APIs for actual container migration.
    // Currently uses TestMigrationServiceMock for evaluation purposes.
}
