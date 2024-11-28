package com.ldm.infrastructure.adapter.in.rest;

import com.ldm.application.port.RaftStorageService;
import com.ldm.domain.model.MigrationAction;
import io.smallrye.mutiny.Multi;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api/migrations")
public class MigrationActionResource {

    @Inject
    RaftStorageService raftStorageService;  // Service to interact with Raft storage

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Multi<MigrationAction> getAllMigrationActions() {
            return raftStorageService.getApprovedMigrationActions();
    }
}
