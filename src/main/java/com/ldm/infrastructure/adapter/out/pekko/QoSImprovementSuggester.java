package com.ldm.infrastructure.adapter.out.pekko;

import com.ldm.application.port.LeaderChangeHandler;
import com.ldm.application.port.MigrationMachine;
import com.ldm.application.service.DomainManager;
import com.ldm.domain.model.Microservice;
import com.ldm.domain.model.MigrationAction;
import com.ldm.domain.model.MigrationCandidate;
import com.ldm.infrastructure.adapter.in.pekko.MigrationProposalVoter;
import com.ldm.infrastructure.adapter.in.ratis.LDMStateMachine;
import com.ldm.infrastructure.mapper.MigrationMapper;
import com.ldm.infrastructure.serialization.protobuf.EvaluateMigrationProposalOuterClass;
import com.ldm.infrastructure.serialization.protobuf.MigrationActionOuterClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorRefResolver;
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
import java.util.Map;
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
            ActorRef<EvaluateMigrationProposalOuterClass.EvaluateMigrationProposal> localVoterRef,
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
                        context.getLog().info("**************************************************************************************");
                        context.getLog().info("**************************************************************************************");
                        context.getLog().info("++++++++++++++++++++++++++++Executing QoS Improvement Task++++++++++++++++++++++++++++");
                        context.getLog().info("**************************************************************************************");
                        context.getLog().info("**************************************************************************************");
                        context.getLog().info("PRINTING OUT CURRENT MICROSERVICES STATE BEFORE QoS Improvement:");
                        domainManager.printMicroservicesState();

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
            ActorRef<EvaluateMigrationProposalOuterClass.EvaluateMigrationProposal> localVoterRef,
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
                () -> triggerLeaderChangeIfNoMigration(ldmStateMachine, leaderChangeHandler)
        );
    }

    private static void handleMigration(
            ActorContext<QoSImproveSuggestionProtocol> context,
            MigrationCandidate migrationCandidate,
            RaftClient raftClient,
            ActorRef<EvaluateMigrationProposalOuterClass.EvaluateMigrationProposal> localVoterRef,
            MigrationMapper migrationMapper,
            ActorRef<Receptionist.Listing> listingResponseAdapter,
            Duration timeout
    ) {
        discoverInstances(context, timeout, MigrationProposalVoter.MIGRATION_PROPOSAL_VOTER_KEY, listingResponseAdapter)
                .thenCompose(listing -> {
                    Set<ActorRef<EvaluateMigrationProposalOuterClass.EvaluateMigrationProposal>> voters = listing.getServiceInstances(MigrationProposalVoter.MIGRATION_PROPOSAL_VOTER_KEY);
                    Set<ActorRef<EvaluateMigrationProposalOuterClass.EvaluateMigrationProposal>> remoteVoters = voters.stream()
                            .filter(voter -> !voter.equals(localVoterRef))
                            .collect(Collectors.toSet());

                    Set<CompletionStage<Boolean>> votes = collectVotes(remoteVoters, migrationCandidate, timeout, context);

                    return CompletableFuture.allOf(votes.stream().map(CompletionStage::toCompletableFuture).toArray(CompletableFuture[]::new))
                            .thenAccept(v -> {
                                if (shouldMigrate(votes, remoteVoters.size())) {
                                    sendMigrationAction(migrationCandidate, migrationMapper, raftClient);
                                }
                            });
                })
                .exceptionally(ex -> {
                    log.error("Error during migration proposal handling: {}", ex.getMessage(), ex);
                    return null;
                });
    }

    private static void triggerLeaderChangeIfNoMigration(
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
            RaftClient raftClient
    ) {
        MigrationAction migrationAction = migrationCandidate.toMigrationAction();
        MigrationActionOuterClass.MigrationAction migrationActionProto = migrationMapper.toProto(migrationAction);
        Message migrationActionMessage = Message.valueOf(ByteString.copyFrom(migrationActionProto.toByteArray()));

        log.debug("Sending this migrationActionProto: {}", migrationActionProto);

        raftClient.async().send(migrationActionMessage)
                .thenAccept(reply -> log.info("Migration action applied successfully for microservice {} suggested at {} by LDM {}!", migrationAction.microservice().getId(), migrationAction.suggestedAt(), migrationAction.suggesterId()))
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

    private static Set<CompletionStage<Boolean>> collectVotes(Set<ActorRef<EvaluateMigrationProposalOuterClass.EvaluateMigrationProposal>> voterSet, MigrationCandidate candidate, Duration timeout, ActorContext<QoSImproveSuggestionProtocol> context) {
        // Convert MigrationCandidate to Protobuf format
        Map<Microservice, Double> affinitiesInMicroservice = candidate.microservice().getAffinities();
        Map<String, Double> affinitiesMapForProto = affinitiesInMicroservice
                .entrySet()
                .stream()
                .collect(Collectors.toMap(
                        entry -> entry.getKey().getId(),
                        Map.Entry::getValue
                ));

        EvaluateMigrationProposalOuterClass.MigrationCandidate protobufCandidate =
                EvaluateMigrationProposalOuterClass.MigrationCandidate.newBuilder()
                        .setMicroservice(EvaluateMigrationProposalOuterClass.Microservice.newBuilder()
                                .setId(candidate.microservice().getId())
                                .setName(candidate.microservice().getName())
                                .setCpuUsage(candidate.microservice().getCpuUsage())
                                .setMemoryUsage(candidate.microservice().getMemoryUsage())
                                .setK8SCluster(EvaluateMigrationProposalOuterClass.K8sCluster.newBuilder()
                                        .setId(candidate.microservice().getK8sCluster().getId())
                                        .setLocation(candidate.microservice().getK8sCluster().getLocation())
                                        .build())
                                .putAllAffinities(affinitiesMapForProto)
                                .build())
                        .setImprovementScore(candidate.improvementScore())
                        .setTargetK8SCluster(EvaluateMigrationProposalOuterClass.K8sCluster.newBuilder()
                                .setId(candidate.targetK8sCluster().getId())
                                .setLocation(candidate.targetK8sCluster().getLocation())
                                .build())
                        .setSuggesterId(candidate.suggesterId())
                        .build();
        return voterSet.stream()
                .map(voter -> AskPattern.askWithStatus(
                        voter,
                        replyTo -> EvaluateMigrationProposalOuterClass.EvaluateMigrationProposal.newBuilder()
                                .setMigrationCandidate(protobufCandidate)
                                .setReplyToActorPath(ActorRefResolver.get(context.getSystem()).toSerializationFormat(replyTo)).build(),
                        timeout,
                        context.getSystem().scheduler()
                ))
                .map(future -> future.thenApply(response -> {
                    // Extract the Boolean approval from the response
                    if (response instanceof StatusReply) {
                        log.debug("Received Vote: " + ((StatusReply<?>) response).getValue());
                        return ((StatusReply<Boolean>) response).isSuccess()
                                ? ((StatusReply<Boolean>) response).getValue()
                                : false;
                    }
                    return (Boolean) response;
                }))
                .collect(Collectors.toSet());
    }

    private static boolean shouldMigrate(Set<CompletionStage<Boolean>> votes, long totalVoters) {
        long approvedVoteCount = votes.stream()
                .map(CompletionStage::toCompletableFuture)
                .map(CompletableFuture::join)
                .filter(Boolean::booleanValue)
                .count();

        log.debug("approvedVoteCount: " + approvedVoteCount);
        long majorityThreshold = (totalVoters / 2) + 1;
        log.debug("majorityThreshold: " + majorityThreshold);

        if (approvedVoteCount >= majorityThreshold) {
            log.info(">>>>>>>>>>>>>>>>>>>>>>>>>Majority approval APPROVED. Proceeding with the migration proposal.<<<<<<<<<<<<<<<<<<<<<<");
            return true;
        }

        log.info("-->>>>>>>>>>>>>>>>>>>>>>>>>Migration proposal REJECTED. Majority approval not met.<<<<<<<<<<<<<<<<<<<<<<--");
        return false;
    }
}
