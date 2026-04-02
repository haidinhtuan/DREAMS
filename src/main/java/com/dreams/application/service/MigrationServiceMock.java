package com.dreams.application.service;

import com.dreams.application.port.MigrationService;
import com.dreams.domain.model.MigrationAction;
import io.quarkus.arc.lookup.LookupIfProperty;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
@LookupIfProperty(name = "ldm.migration.mode", stringValue = "experiment")
public class MigrationServiceMock implements MigrationService {

    @Override
    public void executeMigration(MigrationAction migrationAction){
        // Real migration should be executed here
        log.warn("++++++++<EXECUTING THE MIGRATION>++++++++< for: {} ", migrationAction);
    }
}
