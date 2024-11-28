package com.ldm.application.service;

import com.ldm.application.port.ConsensusHandler;
import com.ldm.domain.model.MigrationAction;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@Slf4j
@ApplicationScoped
public class DefaultConsensusHandler implements ConsensusHandler {

    private final DomainManager domainManager;

    @Override
    public void handle(MigrationAction migrationAction) {
        this.domainManager.updateState(migrationAction);
    }

}

