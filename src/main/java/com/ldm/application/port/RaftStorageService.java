package com.ldm.application.port;

import com.ldm.domain.model.MigrationAction;
import io.smallrye.mutiny.Multi;

public interface RaftStorageService {
    Multi<MigrationAction> getApprovedMigrationActions();
}
