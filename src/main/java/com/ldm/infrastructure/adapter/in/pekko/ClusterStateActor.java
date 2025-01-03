package com.ldm.infrastructure.adapter.in.pekko;

import com.ldm.domain.model.ClusterState;
import com.ldm.domain.model.K8sCluster;
import com.ldm.domain.model.Microservice;
import com.ldm.domain.model.MigrationAction;
import com.ldm.domain.model.testdata.ClusterData;
import com.ldm.shared.constants.LDMConstants;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.persistence.typed.PersistenceId;
import org.apache.pekko.persistence.typed.javadsl.CommandHandler;
import org.apache.pekko.persistence.typed.javadsl.EventHandler;
import org.apache.pekko.persistence.typed.javadsl.EventSourcedBehavior;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ClusterStateActor
        extends EventSourcedBehavior<ClusterStateActor.Command, ClusterStateActor.Event, ClusterStateActor.State> {

    private final ActorContext<Command> context;
    private final ActorRef<DurableStateClusterActor.Command> durableStateClusterActor;

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
                updatedMap.remove(event.migrationAction.microservice().getId());
            }
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

        public ClusterState toClusterState() {
            return new ClusterState(this.microserviceClusterMap, this.versionNumber, this.lastEventCreatedAt);
        }
    }

    public record NotifyStateChange(State updatedState) implements Command {
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
    public static Behavior<Command> create(String persistenceId, ActorRef<DurableStateClusterActor.Command> durableStateClusterActor) {
        return Behaviors.setup(ctx -> new ClusterStateActor(PersistenceId.ofUniqueId(persistenceId), durableStateClusterActor, ctx));
    }

    private ClusterStateActor(PersistenceId persistenceId, ActorRef<DurableStateClusterActor.Command> durableStateClusterActor, ActorContext<Command> context) {
        super(persistenceId);
        this.context = context;
        this.durableStateClusterActor = durableStateClusterActor;
    }

    @Override
    public State emptyState() {
        return new State();
    }

    @Override
    public CommandHandler<Command, Event, State> commandHandler() {
        return newCommandHandlerBuilder()
                .forAnyState()
                .onCommand(NotifyStateChange.class, (state, command) -> {
                    context.getLog().info("State changed, notifying DurableStateClusterActor...");
                    // Notify DurableStateClusterActor
                    this.durableStateClusterActor.tell(new DurableStateClusterActor.PersistClusterState(command.updatedState.toClusterState()));

                    return Effect().none();
                })
                .onCommand(InitCluster.class, (state, command) -> {
                    ClusterInitPerformed event = new ClusterInitPerformed(command.clusterData);
                    return Effect().persist(event).thenRun(updatedState -> context.getLog().info("Cluster Init performed: " + event));
                })
                .onCommand(PerformMigrationAction.class, (state, command) -> {
                    MigrationPerformed event = new MigrationPerformed(
                            command.migrationAction);
                    return Effect().persist(event).thenRun(updatedState ->
                            context.getLog().info("Migration performed: " + event)
                    );
                })
                .onCommand(GetState.class, (state, command) -> {
                    context.getLog().info("Current State: " + state);
                    return Effect().none();
                })
                .build();
    }

    @Override
    public EventHandler<State, Event> eventHandler() {
        return newEventHandlerBuilder()
                .forAnyState()
                .onEvent(ClusterInitPerformed.class, (state, event) -> {
                    context.getLog().debug("Applying event: " + event +
                            ", Current Version: " + state.getVersionNumber());
                    State newState = state.apply(event);
                    context.getSelf().tell(new NotifyStateChange(newState));

                    return newState;
                })
                .onEvent(MigrationPerformed.class, (state, event) -> {
                    context.getLog().debug("Applying event: " + event +
                            ", Current Version: " + state.getVersionNumber());

                    String id = persistenceId().id();
                    String prefix = LDMConstants.EVENT_TAG_CLUSTER_STATE + "-";

                    // Extract the part after the prefix
                    String ldmId = "";
                    if (id.startsWith(prefix)) {
                        ldmId = id.substring(prefix.length());
                        context.getLog().debug("Extracted value: " + ldmId);
                    } else {
                        context.getLog().warn("The string does not start with the expected prefix.");
                    }

                    State newState = state.apply(ldmId, event);
                    context.getSelf().tell(new NotifyStateChange(newState));

                    return newState;
                })
                .build();
    }

    @Override
    public Set<String> tagsFor(Event event) {
        Set<String> tags = new HashSet<>();
        context.getLog().debug("Tagging the following event: {}", event);
        context.getLog().debug("Tagging persisted event with: {}", persistenceId().id());
        tags.add(LDMConstants.EVENT_TAG_CLUSTER_STATE + "-" + persistenceId().id());
        return tags;
    }
}
