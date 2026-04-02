package com.dreams.modules;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

/**
 * Recovery and Fault Tolerance Module (RFM) — spans all layers.
 * Ensures system resilience through state persistence, crash recovery,
 * and periodic checkpointing.
 *
 * Components:
 * - State Persistence Engine: logs migration actions in fault-tolerant store
 *   (implemented via Apache Pekko event sourcing + R2DBC journal)
 * - Crash Recovery Manager: resumes interrupted processes after crashes
 *   (implemented via Pekko persistence recovery and Raft log replay)
 * - Checkpoint Manager: saves periodic checkpoints for faster recovery
 *   (implemented via Pekko snapshot store)
 *
 * The RFM spans all layers of the LDM architecture, providing fault tolerance
 * guarantees for consensus (CLR), migration state (MSR), and domain state (DSR).
 */
@ApplicationScoped
@Slf4j
public class RecoveryFaultToleranceModule {
    // Fault tolerance is provided by:
    // 1. Apache Pekko event sourcing (event_journal + snapshot tables)
    // 2. Apache Ratis replicated log (raft-storage directory)
    // 3. PostgreSQL for durable state persistence
    // These are configured in application.conf and application.yaml.
}
