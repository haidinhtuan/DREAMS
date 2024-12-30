package com.ldm.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ldm.application.port.MigrationMachine;
import com.ldm.application.service.ClusterLatencyCache;
import com.ldm.application.service.DomainManager;
import com.ldm.domain.model.MigrationAction;
import com.ldm.domain.model.testdata.ClusterData;
import com.ldm.infrastructure.adapter.in.pekko.ClusterMembershipSync;
import com.ldm.infrastructure.adapter.in.pekko.ClusterStateActor;
import com.ldm.infrastructure.adapter.in.pekko.MigrationProposalVoter;
import com.ldm.infrastructure.adapter.in.pekko.PingService;
import com.ldm.infrastructure.adapter.in.projection.ClusterStateProjectionR2dbcHandler;
import com.ldm.infrastructure.adapter.in.ratis.LDMStateMachine;
import com.ldm.infrastructure.adapter.out.pekko.PingManager;
import com.ldm.infrastructure.adapter.out.pekko.QoSImprovementSuggester;
import com.ldm.infrastructure.mapper.MicroserviceMapper;
import com.ldm.infrastructure.mapper.MigrationMapper;
import com.ldm.infrastructure.serialization.protobuf.EvaluateMigrationProposalOuterClass;
import com.ldm.infrastructure.serialization.protobuf.PingPong;
import com.ldm.shared.constants.LDMConstants;
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

    public record StartQoSImprovementSuggester(RaftClient raftClient, DomainManager domainManager) implements Command {
    }

    public record InitCluster(ClusterData clusterData) implements Command {}

    public record PerformMigrationAction(MigrationAction migrationAction) implements Command {}

    public static class StopQoSImprovementSuggester implements Command {
    }

    private ActorSystem<Command> actorSystem;
    private final LdmConfig ldmConfig;
    private final ClusterLatencyCache clusterLatencyCache;
    private final RaftClient raftClient;

    private final DomainManager domainManager;

    private final MigrationMapper migrationMapper;

    private final MicroserviceMapper microserviceMapper;

//    private final QuarkusHibernateSessionFactory hibernateSessionFactory;

    private final ObjectMapper objectMapper;

    private final DatasourceConfig datasourceConfig;

    @Getter
    @Setter
    private MigrationMachine<LDMStateMachine> migrationMachine;


    private ActorRef<QoSImprovementSuggester.QoSImproveSuggestionProtocol> qosImprovementSuggesterRef = null;

    void init() {
        log.info(">> Creating the ActorSystem (ClusterSysten)...");
        actorSystem = ActorSystem.create(rootBehavior(), "ClusterSystem");
    }

    private Behavior<Command> rootBehavior() {
        return Behaviors.setup(context -> {
            // Initialize PingManager actor
            ActorRef<PingPong.Ping> pingService = context.spawn(
                    PingService.create(ldmConfig.id()),
                    "PingService"
            );

            ActorRef<PingManager.Command> pingManager = context.spawn(
                    PingManager.create(ldmConfig.id(), ldmConfig.latenciesCheck().interval(), ldmConfig.latenciesCheck().maxRetry(), clusterLatencyCache, pingService),
                    "PingManager"
            );

            // Initialize ClusterMembershipSync actor
            ActorRef<ClusterEvent.MemberEvent> clusterMembershipSync = context.spawn(
                    ClusterMembershipSync.create(ldmConfig.id(), raftClient, context.getSystem()),
                    "ClusterMembershipSync"
            );

            // Initialize MigrationProposalVoter actor
            ActorRef<EvaluateMigrationProposalOuterClass.EvaluateMigrationProposal> migrationProposalVoter = context.spawn(
                    MigrationProposalVoter.create(ldmConfig.id(), domainManager, microserviceMapper),
                    "MigrationProposalVoter"
            );

            final String PERSISTENCE_ID = LDMConstants.EVENT_TAG_CLUSTER_STATE + "-" + ldmConfig.id();

            ActorRef<ClusterStateActor.Command> clusterStateActor = context.spawn(
                    ClusterStateActor.create(PERSISTENCE_ID),
                    "ClusterStateActor"
            );

            ProjectionId projectionId =
                    ProjectionId.of(PERSISTENCE_ID, PERSISTENCE_ID);

            Optional<R2dbcProjectionSettings> settings = Optional.empty();

            SourceProvider<Offset, EventEnvelope<ClusterStateActor.Event>> sourceProvider = createSourceProvider(actorSystem, R2dbcReadJournal.Identifier(), "", PERSISTENCE_ID);
            Projection<EventEnvelope<ClusterStateActor.Event>> projection =
                    R2dbcProjection.exactlyOnce(
                            projectionId, settings, sourceProvider, () -> new ClusterStateProjectionR2dbcHandler(ldmConfig, objectMapper), actorSystem);

            ActorRef<ProjectionBehavior.Command> clusterStateProjector = context.spawn(ProjectionBehavior.create(projection), projection.projectionId().id());

            return Behaviors.receive(Command.class)
                    .onMessage(StartQoSImprovementSuggester.class, msg -> {
                        if (qosImprovementSuggesterRef == null) {
                            // Spawn QoSImprovementSuggester actor
                            qosImprovementSuggesterRef = context.spawn(
                                    QoSImprovementSuggester.create(msg.raftClient, msg.domainManager, ldmConfig.proposal().interval(), ldmConfig.proposal().requestTimeout(), migrationProposalVoter, migrationMapper, migrationMachine),
                                    "QoSImprovementSuggester"
                            );

                            log.info("## ActorSystemsManager: QoSImprovementSuggester actor created. ##");
                        } else {
                            log.warn("## ActorSystemsManager: QoSImprovementSuggester is already running. ##");
                        }
                        return Behaviors.same();
                    })
                    .onMessage(StopQoSImprovementSuggester.class, msg -> {
                        if (qosImprovementSuggesterRef != null) {
                            // Send a stop message to the QoSImprovementSuggester actor
                            qosImprovementSuggesterRef.tell(new QoSImprovementSuggester.StopCommand());
                            log.info("## ActorSystemsManager: Stop command sent to QoSImprovementSuggester actor. ##");

                            // Clear the reference
                            qosImprovementSuggesterRef = null;
                        } else {
                            log.warn("## ActorSystemsManager: No QoSImprovementSuggester instance is running. ##");
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
