package com.dreams.application.port;

import com.dreams.domain.model.MigrationAction;
import io.smallrye.mutiny.Uni;

public interface ConsensusHandler {

    Uni<Void> handle(MigrationAction migrationAction);
}
