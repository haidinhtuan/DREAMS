package com.ldm.application.service;

import com.ldm.application.port.MigrationService;
import com.ldm.domain.model.MigrationAction;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
public class TestMigrationServiceMock implements MigrationService {
    @Override
    public void executeMigration(MigrationAction migrationAction){
        // Real migration should be executed here
        log.warn("++++++++<EXECUTING THE MIGRATION>++++++++< for: {} ", migrationAction);
    }
}
