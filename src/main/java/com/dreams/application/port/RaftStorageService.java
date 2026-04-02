package com.dreams.application.port;

import com.dreams.domain.model.MigrationAction;
import io.smallrye.mutiny.Multi;

public interface RaftStorageService {
    Multi<MigrationAction> getApprovedMigrationActions();
}
