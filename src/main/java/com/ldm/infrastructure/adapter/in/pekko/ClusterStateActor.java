package com.ldm.infrastructure.adapter.in.pekko;

import com.ldm.domain.model.K8sCluster;
import com.ldm.domain.model.Microservice;
import com.ldm.domain.model.MigrationAction;
import com.ldm.domain.model.testdata.ClusterData;
import com.ldm.shared.constants.LDMConstants;
import lombok.extern.slf4j.Slf4j;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.persistence.typed.PersistenceId;
import org.apache.pekko.persistence.typed.javadsl.CommandHandler;
import org.apache.pekko.persistence.typed.javadsl.EventHandler;
import org.apache.pekko.persistence.typed.javadsl.EventSourcedBehavior;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Slf4j
public class ClusterStateActor
        extends EventSourcedBehavior<ClusterStateActor.Command, ClusterStateActor.Event, ClusterStateActor.State> {

    public interface Command {
    }

    public interface Event {
    }

    public static class State {
        private final Map<String, Microservice> microserviceClusterMap;
        private final long versionNumber; // Track the version of the state

        private final LocalDateTime lastEventCreatedAt;

        private final String lastSuggester;

        public State() {
            this.microserviceClusterMap = new HashMap<>();
            this.versionNumber = 0L;
            this.lastEventCreatedAt = null;
            this.lastSuggester = null;
        }

        public State(Map<String, Microservice> microserviceClusterMap, long versionNumber, LocalDateTime lastEventCreatedAt, String eventSuggester) {
            this.microserviceClusterMap = new HashMap<>(microserviceClusterMap);
            this.versionNumber = versionNumber;
            this.lastEventCreatedAt = lastEventCreatedAt;
            this.lastSuggester = eventSuggester;
        }

//        public State apply(String ldmId, MigrationPerformed event) {
//            Map<String, Microservice> updatedMap = new HashMap<>(microserviceClusterMap);
//
//            if (this.microserviceClusterMap.get(event.migrationAction.microservice().getId()) == null
//                    && event.migrationAction.targetK8sCluster().getId().equalsIgnoreCase(ldmId)) {
//                // Microservice is not on the current cluster, but should be migrated to this cluster
//                updatedMap.put(event.migrationAction.microservice().getId(), event.migrationAction.microservice());
//            } else if (this.microserviceClusterMap.get(event.migrationAction.microservice().getId()) != null
//                    && event.migrationAction.microservice().getK8sCluster().getId().equalsIgnoreCase(ldmId)
//                    && !event.migrationAction.targetK8sCluster().getId().equalsIgnoreCase(ldmId)) {
//                // Microservice is on the current cluster, but has been moved to another cluster
//                this.microserviceClusterMap.remove(event.migrationAction.microservice().getId());
//            } // Else do nothing if the cluster is not affected
//
//            // Update affinities
//            for (Map.Entry<String, Microservice> entry : updatedMap.entrySet()) {
//                Microservice microservice = entry.getValue();
//
//                if (microservice.getAffinities().containsKey(event.migrationAction.microservice())) {
//                    // Create a new instance of K8sCluster with updated values only when needed
//                    K8sCluster updatedCluster = new K8sCluster(
//                            event.migrationAction.targetK8sCluster().getId(),
//                            event.migrationAction.targetK8sCluster().getLocation()
//                    );
//
//                    // Create a new affinities map with updated cluster information
//                    Map<Microservice, Double> updatedAffinities = new HashMap<>(microservice.getAffinities());
//                    updatedAffinities.forEach((affinityMicroservice, value) -> {
//                        if (affinityMicroservice.getId().equalsIgnoreCase(event.migrationAction.microservice().getId())) {
//                            // Replace affinityMicroservice with a new instance having updated cluster info
//                            Microservice updatedAffinityMicroservice = new Microservice(
//                                    affinityMicroservice.getId(),
//                                    affinityMicroservice.getName(),
//                                    affinityMicroservice.isNonMigratable(),
//                                    updatedCluster,
//                                    affinityMicroservice.getAffinities(),
//                                    affinityMicroservice.getDataExchangedWithServices(),
//                                    affinityMicroservice.getCpuUsage(),
//                                    affinityMicroservice.getMemoryUsage()
//                            );
//                            updatedAffinities.remove(affinityMicroservice);
//                            updatedAffinities.put(updatedAffinityMicroservice, value);
//                        }
//                    });
//
//                    // Create a new microservice with updated affinities and replace it in the map
//                    Microservice updatedMicroservice = new Microservice(
//                            microservice.getId(),
//                            microservice.getName(),
//                            microservice.isNonMigratable(),
//                            microservice.getK8sCluster(),
//                            updatedAffinities,
//                            microservice.getDataExchangedWithServices(),
//                            microservice.getCpuUsage(),
//                            microservice.getMemoryUsage()
//                    );
//                    updatedMap.put(entry.getKey(), updatedMicroservice);
//                }
//            }
//            return new State(updatedMap, versionNumber + 1, event.migrationAction.suggestedAt(), event.migrationAction.suggesterId()); // Increment version on each event
//        }

        public State apply(String ldmId, MigrationPerformed event) {
            Map<String, Microservice> updatedMap = new HashMap<>(microserviceClusterMap);

            // Handle the microservice migration
            if (this.microserviceClusterMap.get(event.migrationAction.microservice().getId()) == null
                    && event.migrationAction.targetK8sCluster().getId().equalsIgnoreCase(ldmId)) {
                // Microservice is not on the current cluster, but should be migrated to this cluster
                updatedMap.put(event.migrationAction.microservice().getId(), event.migrationAction.microservice());
            } else if (this.microserviceClusterMap.get(event.migrationAction.microservice().getId()) != null
                    && event.migrationAction.microservice().getK8sCluster().getId().equalsIgnoreCase(ldmId)
                    && !event.migrationAction.targetK8sCluster().getId().equalsIgnoreCase(ldmId)) {
                // Microservice is on the current cluster, but has been moved to another cluster
                this.microserviceClusterMap.remove(event.migrationAction.microservice().getId());
            }

            // Update affinities
//            for (Map.Entry<String, Microservice> entry : updatedMap.entrySet()) {
//                Microservice microservice = entry.getValue();
//
//                if (microservice.getAffinities().containsKey(event.migrationAction.microservice())) {
//                    // Prepare updates for the affinities
//                    Map<Microservice, Double> updatedAffinities = new HashMap<>();
//                    for (Map.Entry<Microservice, Double> affinityEntry : microservice.getAffinities().entrySet()) {
//                        Microservice affinityMicroservice = affinityEntry.getKey();
//                        Double affinityValue = affinityEntry.getValue();
//
//                        if (affinityMicroservice.getId().equalsIgnoreCase(event.migrationAction.microservice().getId())) {
//                            // Create a new instance of the affinity microservice with updated cluster info
//                            Microservice updatedAffinityMicroservice = new Microservice(
//                                    affinityMicroservice.getId(),
//                                    affinityMicroservice.getName(),
//                                    affinityMicroservice.isNonMigratable(),
//                                    new K8sCluster(
//                                            event.migrationAction.targetK8sCluster().getId(),
//                                            event.migrationAction.targetK8sCluster().getLocation()
//                                    ),
//                                    affinityMicroservice.getAffinities(),
//                                    affinityMicroservice.getDataExchangedWithServices(),
//                                    affinityMicroservice.getCpuUsage(),
//                                    affinityMicroservice.getMemoryUsage()
//                            );
//                            updatedAffinities.put(updatedAffinityMicroservice, affinityValue);
//                        } else {
//                            // Retain unchanged affinities
//                            updatedAffinities.put(affinityMicroservice, affinityValue);
//                        }
//                    }
//
//                    // Create a new microservice with updated affinities and replace it in the map
//                    Microservice updatedMicroservice = new Microservice(
//                            microservice.getId(),
//                            microservice.getName(),
//                            microservice.isNonMigratable(),
//                            microservice.getK8sCluster(),
//                            updatedAffinities,
//                            microservice.getDataExchangedWithServices(),
//                            microservice.getCpuUsage(),
//                            microservice.getMemoryUsage()
//                    );
//                    updatedMap.put(entry.getKey(), updatedMicroservice);
//                }
//            }

            return new State(updatedMap, versionNumber + 1, event.migrationAction.suggestedAt(), event.migrationAction.suggesterId()); // Increment version on each event
        }


        public State apply(ClusterInitPerformed clusterInitPerformed) {
            ClusterData clusterData = clusterInitPerformed.clusterData;
            K8sCluster k8sCluster = new K8sCluster(clusterData.getClusterId(), clusterData.getLocation());

            // Copy the existing map
            Map<String, Microservice> updatedMap = new HashMap<>(microserviceClusterMap);

            // Add or update microservices from clusterData
            clusterData.getMicroservices().forEach(microservice -> {
                microservice.setName(microservice.getId()); // Set name
                microservice.setK8sCluster(k8sCluster);     // Set cluster
                updatedMap.put(microservice.getId(), microservice); // Add/update map
            });

            // Return the new State object
            return new State(updatedMap, versionNumber + 1, LocalDateTime.now(), clusterData.getClusterId());
        }


        public long getVersionNumber() {
            return versionNumber;
        }

        @Override
        public String toString() {
            return "State{" +
                    "microserviceClusterMap=" + microserviceClusterMap +
                    ", versionNumber=" + versionNumber +
                    '}';
        }
    }

    // Commands
    public record PerformMigrationAction(MigrationAction migrationAction) implements Command {
    }

    public record InitCluster(ClusterData clusterData) implements Command {
    }

    public static final class GetState implements Command {
    }

    // Events
    public record MigrationPerformed(MigrationAction migrationAction) implements Event {
    }

    public record ClusterInitPerformed(ClusterData clusterData) implements Event {
    }

    // Actor Creation
    public static Behavior<Command> create(String persistenceId) {
        return new ClusterStateActor(PersistenceId.ofUniqueId(persistenceId));
    }

    private ClusterStateActor(PersistenceId persistenceId) {
        super(persistenceId);
    }

    @Override
    public State emptyState() {
        return new State();
    }

    @Override
    public CommandHandler<Command, Event, State> commandHandler() {
        return newCommandHandlerBuilder()
                .forAnyState()
                .onCommand(InitCluster.class, (state, command) -> {
                    ClusterInitPerformed event = new ClusterInitPerformed(command.clusterData);

                    return Effect().persist(event).thenRun(updatedState -> log.info("Cluster Init performed: " + event));
                })
                .onCommand(PerformMigrationAction.class, (state, command) -> {
                    MigrationPerformed event = new MigrationPerformed(
                            command.migrationAction);
                    return Effect().persist(event).thenRun(updatedState ->
                            log.info("Migration performed: " + event)
                    );
                })
                .onCommand(GetState.class, (state, command) -> {
                    log.info("Current State: " + state);
                    return Effect().none();
                })
                .build();
    }

    @Override
    public EventHandler<State, Event> eventHandler() {
        return newEventHandlerBuilder()
                .forAnyState()
                .onEvent(ClusterInitPerformed.class, (state, event) -> {
                    log.debug("Applying event: " + event +
                            ", Current Version: " + state.getVersionNumber());

                    return state.apply(event);
                })
                .onEvent(MigrationPerformed.class, (state, event) -> {
                    log.debug("Applying event: " + event +
                            ", Current Version: " + state.getVersionNumber());

                    String id = persistenceId().id();
                    String prefix = LDMConstants.EVENT_TAG_CLUSTER_STATE + "-";

                    // Extract the part after the prefix
                    String ldmId = "";
                    if (id.startsWith(prefix)) {
                        ldmId = id.substring(prefix.length());
                        log.debug("Extracted value: " + ldmId);
                    } else {
                        log.warn("The string does not start with the expected prefix.");
                    }

                    return state.apply(ldmId, event);
                })
                .build();
    }

    @Override
    public Set<String> tagsFor(Event event) {
        Set<String> tags = new HashSet<>();
        log.debug("Tagging the following event: {}", event);
        log.debug("Tagging persisted event with: {}", persistenceId().id());
        tags.add(LDMConstants.EVENT_TAG_CLUSTER_STATE + "-" + persistenceId().id());
        return tags;
    }
}
