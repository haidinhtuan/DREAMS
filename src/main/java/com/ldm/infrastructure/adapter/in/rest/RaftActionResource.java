package com.ldm.infrastructure.adapter.in.rest;

import com.ldm.infrastructure.adapter.in.ratis.RaftStateMachine;
import com.ldm.infrastructure.config.RaftServerManager;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.ratis.protocol.ClientId;
import org.apache.ratis.protocol.RaftGroupId;
import org.apache.ratis.protocol.RaftPeerId;
import org.apache.ratis.protocol.TransferLeadershipRequest;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

@Path("/api/ratis")
@Produces("application/json")
@Consumes("application/json")
@Slf4j
@RequiredArgsConstructor
public class RaftActionResource {

    private final RaftStateMachine raftStateMachine;

    private final RaftServerManager raftServerManager;

    @GET
    @Path("/trigger-leader-change")
    public Uni<Response> triggerLeaderChange() {
        ClientId clientId = ClientId.randomId(); // Generate a new Client ID

        return Uni.createFrom()
                .completionStage(raftStateMachine.getServer())
                .onItem().transformToUni(server -> {
                    RaftPeerId serverId = server.getId();
                    RaftGroupId groupId = raftStateMachine.getGroupId();
                    long callId = System.currentTimeMillis();
                    long timeoutMs = 5000; // Configured timeout

                    TransferLeadershipRequest request = new TransferLeadershipRequest(clientId, serverId, groupId, callId, null, timeoutMs);

                    return Uni.createFrom().completionStage(safeTransferLeadershipAsync(request))
                            .onItem().transform(unused -> Response.accepted()
                                    .entity("{\"message\":\"Leadership transfer initiated\"}")
                                    .build());
                })
                .onFailure().recoverWithItem(e -> {
                    log.error("Error during leadership transfer process: ", e);
                    return Response.serverError()
                            .entity("{\"error\":\"" + e.getMessage() + "\"}")
                            .build();
                });

    }

    private CompletableFuture<Void> safeTransferLeadershipAsync(TransferLeadershipRequest request) {
        try {
            return raftServerManager.getServer()
                    .transferLeadershipAsync(request) // Returns CompletableFuture<RaftClientReply>
                    .thenApply(reply -> {
                        // Optionally process the reply if needed
                        log.info("Leadership transfer response received: {}", reply);
                        return null; // Transform to CompletableFuture<Void>
                    });
        } catch (IOException e) {
            log.error("IOException during transferLeadershipAsync: ", e);
            CompletableFuture<Void> failedFuture = new CompletableFuture<>();
            failedFuture.completeExceptionally(new RuntimeException("Failed to initiate leadership transfer", e));
            return failedFuture;
        }
    }
}
