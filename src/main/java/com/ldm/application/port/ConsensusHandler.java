package com.ldm.application.port;

import com.ldm.domain.model.MigrationAction;

public interface ConsensusHandler {

    void handle(MigrationAction migrationAction);
}
