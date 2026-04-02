package com.dreams.application.port;

import com.dreams.domain.model.MigrationAction;

public interface MigrationService {

    void executeMigration(MigrationAction migrationAction);
}
