package com.ldm.application.port;

import com.ldm.domain.model.MigrationAction;

public interface MigrationService {

    void executeMigration(MigrationAction migrationAction);
}
