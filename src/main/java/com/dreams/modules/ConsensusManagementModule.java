package com.dreams.modules;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

/**
 * Consensus Management Module (CMM) — Proposal Manager, Consensus Voting Engine,
 * Leader Coordinator, Consensus Fault Recovery, and Consensus Log Service.
 * Manages the Raft-based distributed consensus for migration decisions.
 *
 * The core consensus logic is implemented in:
 * - {@link com.dreams.infrastructure.adapter.in.ratis.LDMStateMachine} (Raft state machine)
 * - {@link com.dreams.infrastructure.adapter.in.ratis.RaftLeaderChangeHandler} (leader election)
 * - {@link com.dreams.infrastructure.adapter.out.pekko.VotingCoordinator} (vote collection)
 * - {@link com.dreams.infrastructure.adapter.out.pekko.QoSImprovementSuggester} (proposal scheduling)
 */
@ApplicationScoped
@Slf4j
public class ConsensusManagementModule {
    // Consensus logic is distributed across Ratis and Pekko actors.
    // This facade provides documentation and a coordination point.
    // Direct actor interaction is managed by ActorSystemManager and RaftServerManager.
}
