package com.dreams.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.dreams.application.port.MigrationMachine;
import com.dreams.application.service.InterDomainLatencyMonitor;
import com.dreams.application.service.MigrationEligibilityEvaluator;
import com.dreams.application.service.LdmStateService;
import com.dreams.application.service.MetricsAggregator;
import com.dreams.domain.model.MigrationAction;
import com.dreams.domain.model.testdata.ClusterData;
import com.dreams.infrastructure.adapter.in.pekko.*;
import com.dreams.infrastructure.adapter.in.projection.ClusterStateProjectionR2dbcHandler;
import com.dreams.infrastructure.adapter.in.ratis.LDMStateMachine;
import com.dreams.infrastructure.adapter.in.websocket.DashboardWebSocket;
import com.dreams.infrastructure.adapter.out.pekko.PingManager;
import com.dreams.infrastructure.adapter.out.pekko.ProposalManager;
import com.dreams.infrastructure.mapper.MicroserviceMapper;
import com.dreams.infrastructure.mapper.MigrationMapper;
import com.dreams.infrastructure.serialization.protobuf.EvaluateMigrationProposalOuterClass;
import com.dreams.infrastructure.serialization.protobuf.PingPong;
import com.dreams.shared.constants.LDMConstants;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.cluster.ClusterEvent;
import org.apache.pekko.persistence.query.Offset;
import org.apache.pekko.persistence.query.typed.EventEnvelope;
import org.apache.pekko.persistence.r2dbc.query.javadsl.R2dbcReadJournal;
import org.apache.pekko.persistence.typed.PersistenceId;
import org.apache.pekko.projection.Projection;
import org.apache.pekko.projection.ProjectionBehavior;
import org.apache.pekko.projection.ProjectionId;
import org.apache.pekko.projection.eventsourced.javadsl.EventSourcedProvider;
import org.apache.pekko.projection.javadsl.SourceProvider;
import org.apache.pekko.projection.r2dbc.R2dbcProjectionSettings;
import org.apache.pekko.projection.r2dbc.javadsl.R2dbcProjection;
import org.apache.ratis.client.RaftClient;

import java.util.Optional;

@RequiredArgsConstructor
@Slf4j
public class ActorSystemManager {

    public interface Command {
    }

    public record StartProposalManager(RaftClient raftClient, MigrationEligibilityEvaluator domainManager) implements Command {
    }

    public record InitCluster(ClusterData clusterData) implements Command {}

    public record PerformMigrationAction(MigrationAction migrationAction) implements Command {}

    public static class StopProposalManager implements Command {
    }

    private ActorSystem<Command> actorSystem;
    private final LdmConfig ldmConfig;
    private final InterDomainLatencyMonitor clusterLatencyCache;
    private final RaftClient raftClient;

    private final MigrationEligibilityEvaluator domainManager;

    private final MigrationMapper migrationMapper;

    private final MicroserviceMapper microserviceMapper;

//    private final QuarkusHibernateSessionFactory hibernateSessionFactory;

    private final ObjectMapper objectMapper;

    private final LdmStateService ldmStateService;

    private final DashboardWebSocket dashboardWebSocket;

    private final MetricsAggregator measurementService;

    private final DatasourceConfig datasourceConfig;

    @Getter
    @Setter
    private MigrationMachine<LDMStateMachine> migrationMachine;


    private ActorRef<ProposalManager.QoSImproveSuggestionProtocol> qosImprovementSuggesterRef = null;

    void init() {
        log.info(">> Creating the ActorSystem (ClusterSysten)...");
        actorSystem = ActorSystem.create(rootBehavior(), "ClusterSystem");
    }

    private Behavior<Command> rootBehavior() {
        return Behaviors.setup(context -> {
            // Initialize PingManager actor
            ActorRef<PingPong.Ping> pingService = context.spawn(
                    HealthExchangeService.create(ldmConfig.id()),
                    "HealthExchangeService"
            );

            ActorRef<PingManager.Command> pingManager = context.spawn(
                    PingManager.create(ldmConfig.id(), ldmConfig.latenciesCheck().interval(), ldmConfig.latenciesCheck().maxRetry(), clusterLatencyCache, pingService),
                    "PingManager"
            );

            // Initialize LdmDiscoveryService actor
            ActorRef<ClusterEvent.MemberEvent> clusterMembershipSync = context.spawn(
                    LdmDiscoveryService.create(ldmConfig.id(), raftClient, context.getSystem()),
                    "LdmDiscoveryService"
            );

            // Initialize MigrationProposalVoter actor
            ActorRef<EvaluateMigrationProposalOuterClass.EvaluateMigrationProposal> migrationProposalVoter = context.spawn(
                    MigrationProposalVoter.create(ldmConfig.id(), domainManager, microserviceMapper),
                    "MigrationProposalVoter"
            );

            final String PERSISTENCE_ID = LDMConstants.EVENT_TAG_CLUSTER_STATE + "-" + ldmConfig.id();

            ActorRef<DurableStateClusterActor.Command> durableStateClusterActor = context.spawn(
                    DurableStateClusterActor.create(PersistenceId.ofUniqueId("durable-cluster-state-" + ldmConfig.id())),
                    "DurableStateClusterActor"
            );

            ActorRef<ClusterStateActor.Command> clusterStateActor = context.spawn(
                    ClusterStateActor.create(PERSISTENCE_ID, durableStateClusterActor),
                    "ClusterStateActor"
            );

            ProjectionId projectionId =
                    ProjectionId.of(PERSISTENCE_ID, PERSISTENCE_ID);

            Optional<R2dbcProjectionSettings> settings = Optional.empty();

            SourceProvider<Offset, EventEnvelope<ClusterStateActor.Event>> sourceProvider = createSourceProvider(actorSystem, R2dbcReadJournal.Identifier(), "", PERSISTENCE_ID);
            Projection<EventEnvelope<ClusterStateActor.Event>> projection =
                    R2dbcProjection.exactlyOnce(
                            projectionId, settings, sourceProvider, () -> new ClusterStateProjectionR2dbcHandler(ldmConfig, objectMapper, ldmStateService, dashboardWebSocket), actorSystem);

            ActorRef<ProjectionBehavior.Command> clusterStateProjector = context.spawn(ProjectionBehavior.create(projection), projection.projectionId().id());

            return Behaviors.receive(Command.class)
                    .onMessage(StartProposalManager.class, msg -> {
                        if (qosImprovementSuggesterRef == null) {
                            // Spawn ProposalManager actor
                            qosImprovementSuggesterRef = context.spawn(
                                    ProposalManager.create(msg.raftClient, msg.domainManager, ldmConfig.proposal().interval(), ldmConfig.proposal().requestTimeout(), migrationProposalVoter, migrationMapper, migrationMachine, ldmConfig.id(), measurementService, dashboardWebSocket),
                                    "ProposalManager"
                            );

                            log.info("## ActorSystemsManager: ProposalManager actor created. ##");
                        } else {
                            log.warn("## ActorSystemsManager: ProposalManager is already running. ##");
                        }
                        return Behaviors.same();
                    })
                    .onMessage(StopProposalManager.class, msg -> {
                        if (qosImprovementSuggesterRef != null) {
                            // Send a stop message to the ProposalManager actor
                            qosImprovementSuggesterRef.tell(new ProposalManager.StopCommand());
                            log.info("## ActorSystemsManager: Stop command sent to ProposalManager actor. ##");

                            // Clear the reference
                            qosImprovementSuggesterRef = null;
                        } else {
                            log.warn("## ActorSystemsManager: No ProposalManager instance is running. ##");
                        }
                        return Behaviors.same();
                    })
                    .onMessage(InitCluster.class, msg -> {
                        context.getLog().debug(("Delegating Init Cluster command..."));
                        clusterStateActor.tell(new ClusterStateActor.InitCluster(msg.clusterData));

                        return Behaviors.same();
                    })
                    .onMessage(PerformMigrationAction.class, msg -> {
                        context.getLog().debug("Delegating PerformMigrationActor command...");
                        clusterStateActor.tell(new ClusterStateActor.PerformMigrationAction(msg.migrationAction));

                        return Behaviors.same();
                    })
                    .build();
        });
    }

    public SourceProvider<Offset, EventEnvelope<ClusterStateActor.Event>> createSourceProvider(
            ActorSystem<?> actorSystem,
            String readJournalPluginId,
            String entityType,
            String persistenceId) {

        // Calculate the slice for the specific persistenceId
        int slice = EventSourcedProvider.sliceForPersistenceId(actorSystem, readJournalPluginId, persistenceId);

        // Use the slice as both minSlice and maxSlice
        return EventSourcedProvider.eventsBySlices(
                actorSystem,
                readJournalPluginId,
                entityType,
                slice,  // minSlice
                slice   // maxSlice
        );
    }

    @PreDestroy
    void onStop() {
        if (actorSystem != null) {
            actorSystem.terminate();
            log.info("ActorSystem terminated.");
        }

    }

    public ActorSystem<Command> getActorSystem() {
        return actorSystem;
    }

}
