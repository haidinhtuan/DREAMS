package com.ldm.infrastructure.config;

import com.ldm.application.service.ClusterLatencyCache;
import com.ldm.application.service.DomainManager;
import com.ldm.infrastructure.adapter.in.pekko.ClusterMembershipSync;
import com.ldm.infrastructure.adapter.in.pekko.MigrationProposalVoter;
import com.ldm.infrastructure.adapter.out.pekko.PingManager;
import com.ldm.infrastructure.adapter.out.pekko.QoSImprovementSuggester;
import com.ldm.infrastructure.mapper.MigrationMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.receptionist.Receptionist;
import org.apache.pekko.cluster.ClusterEvent;
import org.apache.ratis.client.RaftClient;

@Singleton
@RequiredArgsConstructor
@Slf4j
public class ActorSystemManager {

    public interface Command {}

    public record StartQoSImprovementSuggester(RaftClient raftClient, DomainManager domainManager) implements Command {}

    public static class StopQoSImprovementSuggester implements Command {}

    private ActorSystem<Command> actorSystem;
    private final LdmConfig ldmConfig;
    private final ClusterLatencyCache clusterLatencyCache;
    private final RaftClient raftClient;

    private final DomainManager domainManager;

    private final MigrationMapper migrationMapper;

    private ActorRef<QoSImprovementSuggester.QoSImproveSuggestionProtocol> qosImprovementSuggesterRef = null;

    @PostConstruct
    void init() {
        log.info(">> Creating the ActorSystem (ClusterSysten)...");
        actorSystem = ActorSystem.create(rootBehavior(), "ClusterSystem");
    }

    private Behavior<Command> rootBehavior() {
        return Behaviors.setup(context -> {
            // Initialize PingManager actor
            ActorRef<PingManager.Command> pingManager = context.spawn(
                    PingManager.create(ldmConfig.id(), ldmConfig.latenciesCheck().interval(), ldmConfig.latenciesCheck().maxRetry(), clusterLatencyCache),
                    "PingManager"
            );

            // Initialize ClusterMembershipSync actor
            ActorRef<ClusterEvent.MemberEvent> clusterMembershipSync = context.spawn(
                    ClusterMembershipSync.create(ldmConfig.id(), raftClient, context.getSystem()),
                    "ClusterMembershipSync"
            );

            // Initialize MigrationProposalVoter actor
            ActorRef<MigrationProposalVoter.VotingProtocol> migrationProposalVoter = context.spawn(
                    MigrationProposalVoter.create(ldmConfig.id(), domainManager),
                    "MigrationProposalVoter"
            );


            return Behaviors.receive(Command.class)
                    .onMessage(StartQoSImprovementSuggester.class, msg -> {
                        if (qosImprovementSuggesterRef == null) {
                            // Spawn QoSImprovementSuggester actor
                            qosImprovementSuggesterRef = context.spawn(
                                    QoSImprovementSuggester.create(msg.raftClient, msg.domainManager, ldmConfig.proposal().interval(), ldmConfig.proposal().requestTimeout(), migrationProposalVoter, migrationMapper),
                                    "QoSImprovementSuggester"
                            );

                            // Register the QoSImprovementSuggester actor with the receptionist
                            context.getSystem().receptionist().tell(
                                    Receptionist.register(QoSImprovementSuggester.QOS_IMPROVEMENT_SUGGESTER_SCHEDULER_KEY, qosImprovementSuggesterRef)
                            );
                            log.info("QoSImprovementSuggester actor started and registered successfully.");
                        } else {
                            log.warn("QoSImprovementSuggester is already running.");
                        }
                        return Behaviors.same();
                    })
                    .onMessage(StopQoSImprovementSuggester.class, msg -> {
                        if (qosImprovementSuggesterRef != null) {
                            // Send a stop message to the QoSImprovementSuggester actor
                            qosImprovementSuggesterRef.tell(new QoSImprovementSuggester.StopCommand());
                            log.info("Stop command sent to QoSImprovementSuggester actor.");

                            // Deregister from receptionist if needed
                            context.getSystem().receptionist().tell(
                                    Receptionist.deregister(QoSImprovementSuggester.QOS_IMPROVEMENT_SUGGESTER_SCHEDULER_KEY, qosImprovementSuggesterRef)
                            );

                            // Clear the reference
                            qosImprovementSuggesterRef = null;
                        } else {
                            log.warn("No QoSImprovementSuggester instance is running.");
                        }
                        return Behaviors.same();
                    })
                    .build();
        });
    }

    @PreDestroy
    void onStop() {
        if(actorSystem != null) {
            actorSystem.terminate();
            log.info("ActorSystem terminated.");
        }

    }

    public ActorSystem<Command> getActorSystem() {
        return actorSystem;
    }
}
