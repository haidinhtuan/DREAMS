package com.ldm.infrastructure.adapter.in.rest;

import com.ldm.infrastructure.adapter.in.ratis.RaftLeaderChangeHandler;
import com.ldm.infrastructure.config.ActorSystemManager;
import com.ldm.shared.util.ApplicationUtils;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.ratis.protocol.ClientId;
import org.apache.ratis.protocol.RaftGroupId;
import org.apache.ratis.protocol.RaftPeerId;
import org.apache.ratis.protocol.TransferLeadershipRequest;

@Path("/api/ratis")
@Produces("application/json")
@Consumes("application/json")
@Slf4j
@RequiredArgsConstructor
public class RaftActionResource {

    private final ActorSystemManager actorSystemManager;

    @GET
    @Path("/trigger-leader-change/{raftPeerId}")
    public Uni<Response> triggerLeaderChange(String raftPeerId) {
        ClientId clientId = ClientId.randomId(); // Generate a new Client ID

        return Uni.createFrom()
                .completionStage(actorSystemManager.getMigrationMachine().getLDMStateMachine().getServer())
                .onItem().transformToUni(server -> {
                    RaftPeerId serverId = server.getId();
                    RaftGroupId groupId = actorSystemManager.getMigrationMachine().getLDMStateMachine().getGroupId();

                    TransferLeadershipRequest request;
                    if (raftPeerId != null) {
                        request = ApplicationUtils.createTransferLeadershipRequest(serverId, groupId, RaftPeerId.valueOf(raftPeerId));
                    } else {
                        request = ApplicationUtils.createTransferLeadershipRequest(serverId, groupId, null);
                    }

                    return Uni.createFrom().completionStage(RaftLeaderChangeHandler.safeTransferLeadershipAsync(server, request))
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
}
