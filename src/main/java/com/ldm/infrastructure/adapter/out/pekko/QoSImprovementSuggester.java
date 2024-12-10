package com.ldm.infrastructure.adapter.out.pekko;

import com.ldm.application.port.LeaderChangeHandler;
import com.ldm.application.port.MigrationMachine;
import com.ldm.application.service.DomainManager;
import com.ldm.domain.model.MigrationAction;
import com.ldm.domain.model.MigrationCandidate;
import com.ldm.infrastructure.adapter.in.pekko.MigrationProposalVoter;
import com.ldm.infrastructure.adapter.in.ratis.LDMStateMachine;
import com.ldm.infrastructure.mapper.MigrationMapper;
import com.ldm.infrastructure.serialization.protobuf.MigrationActionOuterClass;
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
import org.apache.ratis.protocol.Message;
import org.apache.ratis.protocol.RaftGroupId;
import org.apache.ratis.protocol.RaftGroupMemberId;
import org.apache.ratis.protocol.RaftPeerId;
import org.apache.ratis.server.RaftServer;
import org.apache.ratis.thirdparty.com.google.protobuf.ByteString;

import java.io.Serializable;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

@Slf4j
public class QoSImprovementSuggester {

    public interface QoSImproveSuggestionProtocol {
    }

    public static class RunQoSImprovement implements QoSImproveSuggestionProtocol {
    }

    public record Shutdown(ActorRef<StatusReply<Void>> replyTo) implements QoSImproveSuggestionProtocol, Serializable {
    }

    public static class StopCommand implements QoSImproveSuggestionProtocol {
    }

    // Wrapper class for Receptionist Listing responses
    public record QoSImprovementSuggesterListings(
            Receptionist.Listing listing) implements QoSImproveSuggestionProtocol {
    }

    public static final ServiceKey<QoSImproveSuggestionProtocol> QOS_IMPROVEMENT_SUGGESTER_SCHEDULER_KEY =
            ServiceKey.create(QoSImproveSuggestionProtocol.class, "QoSImprovementSuggester");

    public static Behavior<QoSImproveSuggestionProtocol> create(
            RaftClient raftClient,
            DomainManager domainManager,
            int interval,
            int timeoutSeconds,
            ActorRef<MigrationProposalVoter.VotingProtocol> localVoterRef,
            MigrationMapper migrationMapper,
            MigrationMachine<LDMStateMachine> migrationMachine
    ) {
        return Behaviors.setup(context -> Behaviors.withTimers(timersSetup -> {
            Duration TIMEOUT_DURATION = Duration.ofSeconds(timeoutSeconds);

            // Schedule regular QoS improvement tasks
            timersSetup.startTimerAtFixedRate(new RunQoSImprovement(), Duration.ofSeconds(interval));

            // Create a message adapter for Receptionist.Listing to handle listing responses
            ActorRef<Receptionist.Listing> listingResponseAdapter =
                    context.messageAdapter(Receptionist.Listing.class, QoSImprovementSuggesterListings::new);

            // Register with the receptionist for discoverability
            context.getSystem().receptionist().tell(Receptionist.register(QOS_IMPROVEMENT_SUGGESTER_SCHEDULER_KEY, context.getSelf()));

            return Behaviors.receive(QoSImproveSuggestionProtocol.class)
                    .onMessage(RunQoSImprovement.class, message -> {
                        context.getLog().info("Executing QoS Improvement Task");


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
                                        TIMEOUT_DURATION
                                ))
                                .exceptionally(ex -> {
                                    context.getLog().error("Error during QoS improvement task: {}", ex.getMessage(), ex);
                                    return null;
                                });

                        return Behaviors.same();
                    })
                    .onMessage(Shutdown.class, message -> {
                        context.getLog().info("Received shutdown request");
                        deregisterQoSImprovementSuggester(context);
                        // Acknowledge shutdown and stop the actor
                        message.replyTo.tell(StatusReply.success(null));
                        return Behaviors.stopped();
                    })
                    .onMessage(StopCommand.class, message -> {
                        context.getLog().info("Stopping QoSImprovementSuggester");
                        deregisterQoSImprovementSuggester(context);
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
                        log.warn("No QoSImprovementSuggester instances found. Skipping shutdown.");
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
            DomainManager domainManager,
            RaftClient raftClient,
            ActorRef<MigrationProposalVoter.VotingProtocol> localVoterRef,
            MigrationMapper migrationMapper,
            LeaderChangeHandler<RaftGroupMemberId, RaftPeerId, RaftServer, RaftGroupId> leaderChangeHandler,
            LDMStateMachine ldmStateMachine,
            ActorRef<Receptionist.Listing> listingResponseAdapter,
            Duration timeout
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
                        timeout
                ),
                () -> triggerLeaderChangeIfNoMigration(context, ldmStateMachine, leaderChangeHandler)
        );
    }

    private static void handleMigration(
            ActorContext<QoSImproveSuggestionProtocol> context,
            MigrationCandidate migrationCandidate,
            RaftClient raftClient,
            ActorRef<MigrationProposalVoter.VotingProtocol> localVoterRef,
            MigrationMapper migrationMapper,
            ActorRef<Receptionist.Listing> listingResponseAdapter,
            Duration timeout
    ) {
        discoverInstances(context, timeout, MigrationProposalVoter.MIGRATION_PROPOSAL_VOTER_KEY, listingResponseAdapter)
                .thenCompose(listing -> {
                    Set<ActorRef<MigrationProposalVoter.VotingProtocol>> voters = listing.getServiceInstances(MigrationProposalVoter.MIGRATION_PROPOSAL_VOTER_KEY);
                    Set<ActorRef<MigrationProposalVoter.VotingProtocol>> remoteVoters = voters.stream()
                            .filter(voter -> !voter.equals(localVoterRef))
                            .collect(Collectors.toSet());

                    Set<CompletionStage<Boolean>> votes = collectVotes(remoteVoters, migrationCandidate, timeout, context);

                    return CompletableFuture.allOf(votes.stream().map(CompletionStage::toCompletableFuture).toArray(CompletableFuture[]::new))
                            .thenAccept(v -> {
                                if (shouldMigrate(votes, voters.size(), context)) {
                                    sendMigrationAction(migrationCandidate, migrationMapper, raftClient, context);
                                }
                            });
                })
                .exceptionally(ex -> {
                    log.error("Error during migration proposal handling: {}", ex.getMessage(), ex);
                    return null;
                });
    }

    private static void triggerLeaderChangeIfNoMigration(
            ActorContext<QoSImproveSuggestionProtocol> context,
            LDMStateMachine LDMStateMachine,
            LeaderChangeHandler<RaftGroupMemberId, RaftPeerId, RaftServer, RaftGroupId> leaderChangeHandler
    ) {
        LDMStateMachine.getServer()
                .thenAccept(raftServer -> {
                    leaderChangeHandler.triggerLeaderChange(
                            raftServer,
                            raftServer.getId(),
                            LDMStateMachine.getGroupId()
                    ).subscribe().with(
                            unused -> log.info("Leader change triggered successfully."),
                            failure -> log.error("Failed to trigger leader change: {}", failure.getMessage())
                    );
                }).exceptionally(failure -> {
                    log.error("Failed to retrieve raft server: {}", failure.getMessage());
                    return null;
                });
    }

    private static void sendMigrationAction(
            MigrationCandidate migrationCandidate,
            MigrationMapper migrationMapper,
            RaftClient raftClient,
            ActorContext<?> context
    ) {
        MigrationAction migrationAction = migrationCandidate.toMigrationAction();
        MigrationActionOuterClass.MigrationAction migrationActionProto = migrationMapper.toProto(migrationAction);
        Message migrationActionMessage = Message.valueOf(ByteString.copyFrom(migrationActionProto.toByteArray()));

        raftClient.async().send(migrationActionMessage)
                .thenAccept(reply -> log.info("Migration action sent successfully: {}", migrationAction))
                .exceptionally(ex -> {
                    log.error("Error while sending migration action: ", ex);
                    return null;
                });
    }


    public static void deregisterQoSImprovementSuggester(ActorContext<QoSImproveSuggestionProtocol> context) {
        discoverInstances(context, Duration.ofSeconds(5), QOS_IMPROVEMENT_SUGGESTER_SCHEDULER_KEY, context.messageAdapter(Receptionist.Listing.class, QoSImprovementSuggesterListings::new))
                .thenAccept(listing -> {
                    if (listing.getServiceInstances(QOS_IMPROVEMENT_SUGGESTER_SCHEDULER_KEY).contains(context.getSelf())) {
                        // Deregister from the receptionist
                        context.getLog().info("Deregistering the QoSImprovementSuggester from the receptionist...");
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

    private static Set<CompletionStage<Boolean>> collectVotes(Set<ActorRef<MigrationProposalVoter.VotingProtocol>> voterSet, MigrationCandidate candidate, Duration timeout, ActorContext<QoSImproveSuggestionProtocol> context) {
        return voterSet.stream()
                .map(voter -> AskPattern.askWithStatus(
                        voter,
                        (ActorRef<StatusReply<Boolean>> replyTo) -> new MigrationProposalVoter.EvaluateMigrationProposal(candidate, replyTo),
                        timeout,
                        context.getSystem().scheduler()
                ))
                .collect(Collectors.toSet());
    }

    private static boolean shouldMigrate(Set<CompletionStage<Boolean>> votes, long totalVoters, ActorContext<QoSImproveSuggestionProtocol> context) {
        long approvedVoteCount = votes.stream()
                .map(CompletionStage::toCompletableFuture)
                .map(CompletableFuture::join)
                .filter(Boolean::booleanValue)
                .count();
        long majorityThreshold = (totalVoters / 2) + 1;

        if (approvedVoteCount >= majorityThreshold) {
            context.getLog().info("Majority approval received. Proceeding with the migration proposal.");
            return true;
        }

        context.getLog().info("Migration proposal rejected. Majority approval not met.");
        return false;
    }
}
