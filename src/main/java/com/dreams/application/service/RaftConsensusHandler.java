package com.dreams.application.service;

import com.dreams.application.port.ConsensusHandler;
import com.dreams.domain.model.MigrationAction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@Slf4j
@ApplicationScoped
public class RaftConsensusHandler implements ConsensusHandler {

    private final MigrationEligibilityEvaluator domainManager;

    @Override
    public Uni<Void> handle(MigrationAction migrationAction) {
        return this.domainManager.updateState(migrationAction);
    }

}

