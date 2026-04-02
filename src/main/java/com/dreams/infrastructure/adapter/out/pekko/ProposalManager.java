package com.dreams.infrastructure.adapter.out.pekko;

import com.dreams.application.port.LeaderChangeHandler;
import com.dreams.application.port.MigrationMachine;
import com.dreams.application.service.MigrationEligibilityEvaluator;
import com.dreams.application.service.MetricsAggregator;
import com.dreams.domain.model.MigrationCandidate;
import com.dreams.infrastructure.adapter.in.pekko.MigrationProposalVoter;
import com.dreams.infrastructure.adapter.in.ratis.LDMStateMachine;
import com.dreams.infrastructure.adapter.in.websocket.DashboardWebSocket;
import com.dreams.infrastructure.mapper.MigrationMapper;
import com.dreams.infrastructure.serialization.protobuf.EvaluateMigrationProposalOuterClass;
import com.dreams.shared.constants.PerformanceMeasurementConstants;
import lombok.extern.slf4j.Slf4j;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.AskPattern;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.receptionist.Receptionist;
import org.apache.pekko.actor.typed.receptionist.ServiceKey;
import org.apache.pekko.pattern.StatusReply;
import org.apache.ratis.client.RaftClient;
import org.apache.ratis.protocol.RaftGroupId;
import org.apache.ratis.protocol.RaftGroupMemberId;
import org.apache.ratis.protocol.RaftPeerId;
import org.apache.ratis.server.RaftServer;

import java.io.Serializable;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

@Slf4j
public class ProposalManager {

    public interface QoSImproveSuggestionProtocol {
    }

    public record RunQoSImprovement() implements QoSImproveSuggestionProtocol {
    }

    public record Shutdown(ActorRef<StatusReply<Void>> replyTo) implements QoSImproveSuggestionProtocol, Serializable {
    }

    public static class StopCommand implements QoSImproveSuggestionProtocol {
    }

    // Wrapper class for Receptionist Listing responses
    public record ProposalManagerListings(
            Receptionist.Listing listing) implements QoSImproveSuggestionProtocol {
    }

    public static final ServiceKey<QoSImproveSuggestionProtocol> QOS_IMPROVEMENT_SUGGESTER_SCHEDULER_KEY =
            ServiceKey.create(QoSImproveSuggestionProtocol.class, "ProposalManager");

    public static Behavior<QoSImproveSuggestionProtocol> create(
            RaftClient raftClient,
            MigrationEligibilityEvaluator domainManager,
            int interval,
            int timeoutSeconds,
            ActorRef<EvaluateMigrationProposalOuterClass.EvaluateMigrationProposal> localVoterRef,
            MigrationMapper migrationMapper,
            MigrationMachine<LDMStateMachine> migrationMachine,
            String ldmId,
            MetricsAggregator measurementService,
            DashboardWebSocket dashboardWebSocket
    ) {
        return Behaviors.setup(context -> Behaviors.withTimers(timersSetup -> {
            Duration TIMEOUT_DURATION = Duration.ofSeconds(timeoutSeconds);

            // Schedule regular QoS improvement tasks
            timersSetup.startTimerAtFixedRate(new RunQoSImprovement(), Duration.ofSeconds(interval));

            // Create a message adapter for Receptionist.Listing to handle listing responses
            ActorRef<Receptionist.Listing> listingResponseAdapter =
                    context.messageAdapter(Receptionist.Listing.class, ProposalManagerListings::new);

            // Register with the receptionist for discoverability
            context.getSystem().receptionist().tell(Receptionist.register(QOS_IMPROVEMENT_SUGGESTER_SCHEDULER_KEY, context.getSelf()));

            return Behaviors.receive(QoSImproveSuggestionProtocol.class)
                    .onMessage(RunQoSImprovement.class, message -> {
                        context.getLog().info("**************************************************************************************");
                        context.getLog().info("**************************************************************************************");
                        context.getLog().info("++++++++++++++++++++++++++++Executing QoS Improvement Task++++++++++++++++++++++++++++");
                        context.getLog().info("**************************************************************************************");
                        context.getLog().info("**************************************************************************************");
                        context.getLog().info("PRINTING OUT CURRENT MICROSERVICES STATE BEFORE QoS Improvement:");
                        domainManager.printMicroservicesState();

                        long startTime = System.nanoTime();
                        String processId = UUID.randomUUID().toString();
                        measurementService.recordStart(processId, PerformanceMeasurementConstants.PROCESS_E2E_QOS_OPTIMIZATION, startTime, ldmId);

                        discoverAndShutdownSuggesters(context, TIMEOUT_DURATION, listingResponseAdapter)
                                .thenRun(() -> handleQoSImprovement(
                                        context,
                                        domainManager,
                                        raftClient,
                                        localVoterRef,
                                        migrationMapper,
                                        migrationMachine.getLDMStateMachine().getLeaderChangeHandler(),
                                        migrationMachine.getLDMStateMachine(),
                                        listingResponseAdapter,
                                        TIMEOUT_DURATION,
                                        processId,
                                        measurementService,
                                        dashboardWebSocket
                                ))
                                .exceptionally(ex -> {
                                    context.getLog().error("Error during QoS improvement task: {}", ex.getMessage(), ex);
                                    return null;
                                });

                        return Behaviors.same();
                    })
                    .onMessage(Shutdown.class, message -> {
                        context.getLog().info("Received shutdown request");
                        deregisterProposalManager(context);
                        // Acknowledge shutdown and stop the actor
                        message.replyTo.tell(StatusReply.success(null));
                        return Behaviors.stopped();
                    })
                    .onMessage(StopCommand.class, message -> {
                        context.getLog().info("Stopping ProposalManager");
                        deregisterProposalManager(context);
                        return Behaviors.stopped();
                    })
                    .build();
        }));
    }


    private static CompletionStage<Void> discoverAndShutdownSuggesters(
            ActorContext<QoSImproveSuggestionProtocol> context,
            Duration timeout,
            ActorRef<Receptionist.Listing> listingResponseAdapter
    ) {
        return discoverInstances(context, timeout, QOS_IMPROVEMENT_SUGGESTER_SCHEDULER_KEY, listingResponseAdapter)
                .thenCompose(listing -> {
                    Set<ActorRef<QoSImproveSuggestionProtocol>> suggesters = listing.getServiceInstances(QOS_IMPROVEMENT_SUGGESTER_SCHEDULER_KEY);

                    if (suggesters.isEmpty()) {
                        log.warn("No ProposalManager instances found. Skipping shutdown.");
                        return CompletableFuture.completedFuture(null);
                    }

                    Set<CompletionStage<Void>> shutdownAcks = suggesters.stream()
                            .filter(suggester -> !suggester.equals(context.getSelf()))
                            .map(suggester -> AskPattern.askWithStatus(
                                    suggester,
                                    Shutdown::new,
                                    timeout,
                                    context.getSystem().scheduler()
                            ))
                            .collect(Collectors.toSet());

                    return CompletableFuture.allOf(
                            shutdownAcks.stream().map(CompletionStage::toCompletableFuture).toArray(CompletableFuture[]::new)
                    );
                });
    }

    private static void handleQoSImprovement(
            ActorContext<QoSImproveSuggestionProtocol> context,
            MigrationEligibilityEvaluator domainManager,
            RaftClient raftClient,
            ActorRef<EvaluateMigrationProposalOuterClass.EvaluateMigrationProposal> localVoterRef,
            MigrationMapper migrationMapper,
            LeaderChangeHandler<RaftGroupMemberId, RaftPeerId, RaftServer, RaftGroupId> leaderChangeHandler,
            LDMStateMachine ldmStateMachine,
            ActorRef<Receptionist.Listing> listingResponseAdapter,
            Duration timeout,
            String processId,
            MetricsAggregator measurementService,
            DashboardWebSocket dashboardWebSocket
    ) {
        Optional<MigrationCandidate> migrationCandidateOpt = Optional.ofNullable(domainManager.findMigrationCandidate());

        migrationCandidateOpt.ifPresentOrElse(
                migrationCandidate -> handleMigration(
                        context,
                        migrationCandidate,
                        raftClient,
                        localVoterRef,
                        migrationMapper,
                        listingResponseAdapter,
                        timeout,
                        ldmStateMachine,
                        leaderChangeHandler,
                        processId,
                        measurementService,
                        dashboardWebSocket
                ),
                () -> MigrationOrchestrator.triggerLeaderChange(ldmStateMachine, leaderChangeHandler, processId, measurementService, PerformanceMeasurementConstants.RESULT_NO_PROPOSAL, dashboardWebSocket)
        );
    }

    private static void handleMigration(
            ActorContext<QoSImproveSuggestionProtocol> context,
            MigrationCandidate migrationCandidate,
            RaftClient raftClient,
            ActorRef<EvaluateMigrationProposalOuterClass.EvaluateMigrationProposal> localVoterRef,
            MigrationMapper migrationMapper,
            ActorRef<Receptionist.Listing> listingResponseAdapter,
            Duration timeout,
            LDMStateMachine ldmStateMachine,
            LeaderChangeHandler<RaftGroupMemberId, RaftPeerId, RaftServer, RaftGroupId> leaderChangeHandler,
            String processId,
            MetricsAggregator measurementService,
            DashboardWebSocket dashboardWebSocket
    ) {
        discoverInstances(context, timeout, MigrationProposalVoter.MIGRATION_PROPOSAL_VOTER_KEY, listingResponseAdapter)
                .thenCompose(listing -> {
                    Set<ActorRef<EvaluateMigrationProposalOuterClass.EvaluateMigrationProposal>> voters = listing.getServiceInstances(MigrationProposalVoter.MIGRATION_PROPOSAL_VOTER_KEY);
                    Set<ActorRef<EvaluateMigrationProposalOuterClass.EvaluateMigrationProposal>> remoteVoters = voters.stream()
                            .filter(voter -> !voter.equals(localVoterRef))
                            .collect(Collectors.toSet());

                    Set<CompletionStage<Boolean>> votes = ConsensusVotingEngine.collectVotes(remoteVoters, migrationCandidate, timeout, context);

                    return CompletableFuture.allOf(votes.stream().map(CompletionStage::toCompletableFuture).toArray(CompletableFuture[]::new))
                            .thenAccept(v -> {
                                if (ConsensusVotingEngine.shouldMigrate(votes, remoteVoters.size())) {
                                    MigrationOrchestrator.sendMigrationAction(migrationCandidate, migrationMapper, raftClient, processId, measurementService, dashboardWebSocket);
                                } else {
                                    MigrationOrchestrator.triggerLeaderChange(ldmStateMachine, leaderChangeHandler, processId, measurementService, PerformanceMeasurementConstants.RESULT_REJECTED, dashboardWebSocket);
                                }
                            });
                })
                .exceptionally(ex -> {
                    log.error("Error during migration proposal handling: {}", ex.getMessage(), ex);
                    return null;
                });
    }

    public static void deregisterProposalManager(ActorContext<QoSImproveSuggestionProtocol> context) {
        discoverInstances(context, Duration.ofSeconds(5), QOS_IMPROVEMENT_SUGGESTER_SCHEDULER_KEY, context.messageAdapter(Receptionist.Listing.class, ProposalManagerListings::new))
                .thenAccept(listing -> {
                    if (listing.getServiceInstances(QOS_IMPROVEMENT_SUGGESTER_SCHEDULER_KEY).contains(context.getSelf())) {
                        // Deregister from the receptionist
                        context.getLog().info("Deregistering the ProposalManager from the receptionist...");
                        context.getSystem().receptionist().tell(Receptionist.deregister(QOS_IMPROVEMENT_SUGGESTER_SCHEDULER_KEY, context.getSelf()));
                    }
                });
    }

    private static CompletionStage<Receptionist.Listing> discoverInstances(ActorContext<QoSImproveSuggestionProtocol> context, Duration timeout, ServiceKey<?> serviceKey, ActorRef<Receptionist.Listing> responseAdapter) {
        return AskPattern.ask(
                context.getSystem().receptionist(),
                replyTo -> Receptionist.find(serviceKey, replyTo),
                timeout,
                context.getSystem().scheduler()
        );
    }

}
