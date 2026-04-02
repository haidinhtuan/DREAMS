package com.ldm.infrastructure.adapter.in.projection;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ldm.application.service.LdmStateService;
import com.ldm.domain.model.K8sCluster;
import com.ldm.domain.model.Microservice;
import com.ldm.domain.model.MigrationAction;
import com.ldm.domain.model.testdata.ClusterData;
import com.ldm.infrastructure.adapter.in.pekko.ClusterStateActor;
import com.ldm.infrastructure.adapter.in.websocket.DashboardWebSocket;
import com.ldm.infrastructure.config.LdmConfig;
import com.ldm.infrastructure.persistence.entity.LdmState;
import com.ldm.shared.constants.MessageTypeEnum;
import io.r2dbc.spi.Result;
import io.r2dbc.spi.Row;
import jakarta.json.Json;
import jakarta.json.JsonObjectBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pekko.Done;
import org.apache.pekko.persistence.query.typed.EventEnvelope;
import org.apache.pekko.projection.r2dbc.javadsl.R2dbcHandler;
import org.apache.pekko.projection.r2dbc.javadsl.R2dbcSession;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@RequiredArgsConstructor
public class ClusterStateProjectionR2dbcHandler extends R2dbcHandler<EventEnvelope<ClusterStateActor.Event>> {

    private final LdmConfig ldmConfig;
    private final ObjectMapper objectMapper;

    private final LdmStateService ldmStateService;

    private final DashboardWebSocket dashboardWebSocket;

    private static final AtomicLong migrationsAppliedCount = new AtomicLong(0L);
    private static volatile String lastMigratedMicroservice = "None";

    @Override
    public CompletionStage<Done> process(R2dbcSession session, EventEnvelope<ClusterStateActor.Event> envelope) {
        ClusterStateActor.Event event = envelope.event();
        log.debug(this.getClass().getSimpleName() + " - Handling event: {}", event);

        if (event instanceof ClusterStateActor.ClusterInitPerformed clusterInitPerformedEvent) {
            return handleClusterInit(session, clusterInitPerformedEvent);
        } else if (event instanceof ClusterStateActor.MigrationPerformed migrationPerformedEvent) {
            return handleMigration(session, migrationPerformedEvent).toFuture();
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

    private Mono<Done> handleMigration(R2dbcSession session, ClusterStateActor.MigrationPerformed event) {
        log.debug("Migrated Microservice Projection: {}", event);
        migrationsAppliedCount.incrementAndGet();

        MigrationAction migrationAction = event.migrationAction();

        Mono<Integer> updateStateMono = Mono.from(updateLDMState(session, migrationAction))
                .flatMap(result -> Mono.from(result.getRowsUpdated()))
                .doOnNext(rowsUpdated -> log.debug("Rows updated: {}", rowsUpdated));

        Mono<List<Integer>> updateAffinitiesMono = Flux.merge(updateMicroserviceAffinities(session, migrationAction))
                .collectList()
                .doOnNext(updatedMicroservices -> log.debug("Updated microservice rows: {}", updatedMicroservices.size()));

        Mono<Done> broadcastMono = Mono.defer(() -> fetchAndBroadcastLdmStateReactive(session, migrationAction));

        return updateStateMono
                .then(updateAffinitiesMono)
                .then(broadcastMono)
                .thenReturn(Done.done());
    }

    public Publisher<? extends Result> updateLDMState(R2dbcSession session, MigrationAction migrationAction) {
        String updateMicroserviceSql = """
                UPDATE ldm_state
                SET k8s_cluster_id = $1,
                    k8s_cluster_location = $2,
                    improvement_score = $3,
                    microservice_affinities = $4::json,
                    last_update = $5,
                    ldm_id = $6
                WHERE microservice_id = $7
                """;

        String affinitiesJson = null;
        try {
            affinitiesJson = objectMapper.writeValueAsString(migrationAction.microservice().getAffinities());
        } catch (JsonProcessingException e) {
            return Mono.error(e);
        }

        return session.createStatement(updateMicroserviceSql)
                .bind("$1", migrationAction.targetK8sCluster().getId())
                .bind("$2", migrationAction.targetK8sCluster().getLocation())
                .bind("$3", migrationAction.improvementScore())
                .bind("$4", affinitiesJson)
                .bind("$5", migrationAction.createdAt())
                .bind("$6", ldmConfig.id())
                .bind("$7", migrationAction.microservice().getId())
                .execute();
    }

    public List<Mono<Integer>> updateMicroserviceAffinities(R2dbcSession session, MigrationAction migrationAction) {
        List<Mono<Integer>> updateMicroserviceAffinityList = new ArrayList<>();

        migrationAction.microservice().getAffinities().forEach((microservice, affinityValue) -> {
            String selectMicroserviceAffinitiesSql = """
                    SELECT microservice_affinities
                    FROM ldm_state
                    WHERE ldm_id = $1 AND microservice_id = $2
                    """;

            updateMicroserviceAffinityList.add(
                    Mono.from(session.createStatement(selectMicroserviceAffinitiesSql)
                                    .bind("$1", ldmConfig.id())
                                    .bind("$2", microservice.getId())
                                    .execute())
                            .flatMap(result -> Mono.from(result.map((row, rowMetadata) -> row.get("microservice_affinities", String.class))))
                            .flatMap(currentAffinitiesJson -> {
                                try {
                                    // Parse current affinities JSON
                                    ObjectNode affinitiesNode = (ObjectNode) objectMapper.readTree(currentAffinitiesJson);

                                    // Identify and update keys that match the migrated microservice
                                    Iterator<String> fieldNames = affinitiesNode.fieldNames();
                                    List<String> keysToUpdate = new ArrayList<>();

                                    while (fieldNames.hasNext()) {
                                        String key = fieldNames.next();
                                        if (key.contains("id='" + migrationAction.microservice().getId() + "'")) {
                                            keysToUpdate.add(key);
                                        }
                                    }

                                    for (String key : keysToUpdate) {
                                        // Parse the `key` to extract microservice data
                                        String[] keyParts = key.split(", ");
                                        String id = keyParts[0].split("=")[1].replace("'", "");
                                        String name = keyParts[1].split("=")[1].replace("'", "");
                                        String clusterId = keyParts[3].split("=")[1].replace("'", "");
                                        String clusterLocation = keyParts[3].substring(keyParts[3].indexOf("(") + 1, keyParts[3].indexOf(")")).replace("'", "");

                                        // Construct updated Microservice object
                                        Microservice updatedMicroservice = new Microservice(
                                                id,
                                                name,
                                                false,
                                                new K8sCluster(
                                                        migrationAction.targetK8sCluster().getId(),
                                                        migrationAction.targetK8sCluster().getLocation()
                                                ),
                                                null,
                                                null,
                                                0.0,
                                                0.0
                                        );

                                        // Replace the old key with the updated Microservice's `toString()`
                                        double value = affinitiesNode.get(key).asDouble();
                                        affinitiesNode.remove(key);
                                        affinitiesNode.putPOJO(updatedMicroservice.toString(), value);
                                    }

                                    // Convert the updated JSON node back to a string
                                    String updatedAffinityJson = objectMapper.writeValueAsString(affinitiesNode);

                                    // Update the database with the modified JSON
                                    String updateAffinitiesSql = """
                                            UPDATE ldm_state
                                            SET microservice_affinities = $1::jsonb,
                                                last_update = $2
                                            WHERE ldm_id = $3 AND microservice_id = $4
                                            """;

                                    return Mono.from(session.createStatement(updateAffinitiesSql)
                                                    .bind("$1", updatedAffinityJson)
                                                    .bind("$2", migrationAction.createdAt())
                                                    .bind("$3", ldmConfig.id())
                                                    .bind("$4", microservice.getId())
                                                    .execute())
                                            .flatMap(result -> Mono.from(result.getRowsUpdated()));

                                } catch (Exception e) {
                                    return Mono.error(new RuntimeException("Error processing microservice affinities JSON", e));
                                }
                            })
            );
        });
        return updateMicroserviceAffinityList;
    }

    private Mono<Done> fetchAndBroadcastLdmStateReactive(R2dbcSession session, MigrationAction migrationAction) {
        String query = """
                SELECT ldm_id, microservice_id, k8s_cluster_id, k8s_cluster_location, improvement_score, microservice_affinities, last_update, created_at
                FROM ldm_state
                """;

        return Flux.from(session.createStatement(query).execute())
                .flatMap(result -> result.map((row, rowMetadata) -> mapRowToLdmState(row)))
                .collectList()
                .flatMap(ldmStateList -> {
                    // Convert the list to JSON for broadcasting
                    JsonObjectBuilder graphJsonObjectBuilder = this.ldmStateService.getJsonObjectBuilder(ldmStateList);
                    JsonObjectBuilder migrationsAppliedJsonObjectBuilder = Json.createObjectBuilder();
                    migrationsAppliedJsonObjectBuilder.add("lastMigratedMicroservice", migrationAction.microservice().getId()+" -> " + migrationAction.targetK8sCluster().getLocation());
                    migrationsAppliedJsonObjectBuilder.add("migrationsAppliedCount", migrationsAppliedCount.get());

                    lastMigratedMicroservice = migrationAction.microservice().getId()+" -> " + migrationAction.targetK8sCluster().getLocation();

                    dashboardWebSocket.broadcast(MessageTypeEnum.MIGRATION_APPLIED, migrationsAppliedJsonObjectBuilder.build());
                    dashboardWebSocket.broadcast(MessageTypeEnum.GRAPH_DATA, graphJsonObjectBuilder.build());
                    return Mono.just(Done.done());
                });
    }

    private LdmState mapRowToLdmState(Row row) {
        try {
            LdmState ldmState = new LdmState();
            ldmState.setLdmId(row.get("ldm_id", String.class));
            ldmState.setMicroserviceId(row.get("microservice_id", String.class));
            ldmState.setK8sClusterId(row.get("k8s_cluster_id", String.class));
            ldmState.setK8sClusterLocation(row.get("k8s_cluster_location", String.class));
            ldmState.setImprovementScore(row.get("improvement_score", Double.class));
            String microservice_affinities = row.get("microservice_affinities", String.class);
            Map<String, Double> affinityMap = null;

            affinityMap = objectMapper.readValue(microservice_affinities, new TypeReference<>() {
            });

            ldmState.setMicroserviceAffinities(affinityMap);
            ldmState.setLastUpdate(row.get("last_update", LocalDateTime.class));
            ldmState.setCreatedAt(row.get("created_at", LocalDateTime.class));
            return ldmState;
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse microservice_affinities JSON", e);
        }
    }
}
