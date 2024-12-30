package com.ldm.infrastructure.adapter.in.projection;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ldm.domain.model.MigrationAction;
import com.ldm.domain.model.testdata.ClusterData;
import com.ldm.infrastructure.adapter.in.pekko.ClusterStateActor;
import com.ldm.infrastructure.config.LdmConfig;
import io.r2dbc.spi.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pekko.Done;
import org.apache.pekko.persistence.query.typed.EventEnvelope;
import org.apache.pekko.projection.r2dbc.javadsl.R2dbcHandler;
import org.apache.pekko.projection.r2dbc.javadsl.R2dbcSession;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Slf4j
@RequiredArgsConstructor
public class ClusterStateProjectionR2dbcHandler extends R2dbcHandler<EventEnvelope<ClusterStateActor.Event>> {

    private final LdmConfig ldmConfig;
    private final ObjectMapper objectMapper;

    @Override
    public CompletionStage<Done> process(R2dbcSession session, EventEnvelope<ClusterStateActor.Event> envelope) {
        ClusterStateActor.Event event = envelope.event();
        log.debug(this.getClass().getSimpleName()+" - Handling event: {}", event);

        if (event instanceof ClusterStateActor.ClusterInitPerformed clusterInitPerformedEvent) {
            return handleClusterInit(session, clusterInitPerformedEvent);
        } else if (event instanceof ClusterStateActor.MigrationPerformed migrationPerformedEvent) {
            return handleMigration(session, migrationPerformedEvent);
        } else {
            log.warn("Unhandled event type: {}", event.getClass().getName());
            return CompletableFuture.completedFuture(Done.done());
        }
    }

    private CompletionStage<Done> handleClusterInit(R2dbcSession session, ClusterStateActor.ClusterInitPerformed event) {
        log.debug("Init Cluster Projection: {}", event);
        ClusterData clusterData = event.clusterData();
        String insertSql = """
                INSERT INTO ldm_state
                (ldm_id, microservice_id, k8s_cluster_id, k8s_cluster_location, improvement_score, microservice_affinities, last_update, created_at)
                VALUES ($1, $2, $3, $4, $5, $6::jsonb, $7, $8)
                """;

        List<Mono<Integer>> insertions = clusterData.getMicroservices().stream()
                .map(microservice -> {
                    try {
                        String affinitiesJson = objectMapper.writeValueAsString(microservice.getAffinities());
                        return Mono.from(session.createStatement(insertSql)
                                        .bind("$1", ldmConfig.id())
                                        .bind("$2", microservice.getId())
                                        .bind("$3", clusterData.getClusterId())
                                        .bind("$4", clusterData.getLocation())
                                        .bind("$5", 0.0)
                                        .bind("$6", affinitiesJson)
                                        .bind("$7", LocalDateTime.now())
                                        .bind("$8", LocalDateTime.now())
                                        .execute())
                                .flatMap(result -> Mono.from(result.getRowsUpdated()));
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException("Error serializing affinities to JSON", e);
                    }
                })
                .toList();

        return Mono.when(insertions)
                .then(Mono.just(Done.done()))
                .toFuture();
    }

    private CompletionStage<Done> handleMigration(R2dbcSession session, ClusterStateActor.MigrationPerformed event) {
        log.debug("Migrated Microservice Projection: {}", event);
        MigrationAction migrationAction = event.migrationAction();
        String updateSql = """
                UPDATE ldm_state
                SET k8s_cluster_id = $1,
                    k8s_cluster_location = $2,
                    improvement_score = $3,
                    microservice_affinities = $4::jsonb,
                    last_update = $5
                WHERE ldm_id = $6 AND microservice_id = $7
                """;

        try {
            String affinitiesJson = objectMapper.writeValueAsString(migrationAction.microservice().getAffinities());
            Publisher<? extends Result> execution = session.createStatement(updateSql)
                    .bind("$1", migrationAction.targetK8sCluster().getId())
                    .bind("$2", migrationAction.targetK8sCluster().getLocation())
                    .bind("$3", migrationAction.improvementScore())
                    .bind("$4", affinitiesJson)
                    .bind("$5", migrationAction.createdAt())
                    .bind("$6", ldmConfig.id())
                    .bind("$7", migrationAction.microservice().getId())
                    .execute();

            return Mono.from(execution)
                    .flatMap(result -> Mono.from(result.getRowsUpdated()))
                    .then(Mono.just(Done.done()))
                    .toFuture();
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error serializing affinities to JSON", e);
        }
    }
}
