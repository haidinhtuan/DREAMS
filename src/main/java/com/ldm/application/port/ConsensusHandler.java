package com.ldm.application.port;

import com.ldm.domain.model.MigrationAction;
import io.smallrye.mutiny.Uni;

public interface ConsensusHandler {

    Uni<Void> handle(MigrationAction migrationAction);
}
