package com.dreams.application.service;

import com.dreams.application.port.MigrationService;
import com.dreams.domain.model.MigrationAction;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
public class MigrationServiceMock implements MigrationService {

    @Override
    public void executeMigration(MigrationAction migrationAction){
        // Real migration should be executed here
        log.warn("++++++++<EXECUTING THE MIGRATION>++++++++< for: {} ", migrationAction);
    }
}
