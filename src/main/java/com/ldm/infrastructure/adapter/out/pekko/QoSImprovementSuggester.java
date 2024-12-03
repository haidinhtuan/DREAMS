package com.ldm.infrastructure.adapter.out.pekko;

import com.ldm.application.service.DomainManager;
import com.ldm.domain.model.MigrationAction;
import com.ldm.domain.model.MigrationCandidate;
import com.ldm.infrastructure.adapter.in.pekko.MigrationProposalVoter;
import com.ldm.infrastructure.mapper.MigrationMapper;
import com.ldm.infrastructure.serialization.protobuf.MigrationActionOuterClass;
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
import org.apache.ratis.thirdparty.com.google.protobuf.ByteString;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

public class QoSImprovementSuggester {

    public interface QoSImproveSuggestionProtocol {
    }

    public static class RunQoSImprovement implements QoSImproveSuggestionProtocol {
    }

    public record Shutdown(ActorRef<StatusReply<Void>> replyTo) implements QoSImproveSuggestionProtocol {
    }

    public static class StopCommand implements QoSImproveSuggestionProtocol {
    }

    // Wrapper class for Receptionist Listing responses
    public static class QoSImprovementSuggesterListings implements QoSImproveSuggestionProtocol {
        private final Receptionist.Listing listing;

        public QoSImprovementSuggesterListings(Receptionist.Listing listing) {
            this.listing = listing;
        }

        public Receptionist.Listing getListing() {
            return listing;
        }
    }

    public static final ServiceKey<QoSImproveSuggestionProtocol> QOS_IMPROVEMENT_SUGGESTER_SCHEDULER_KEY =
            ServiceKey.create(QoSImproveSuggestionProtocol.class, "QoSImprovementSuggester");

    public static Behavior<QoSImproveSuggestionProtocol> create(RaftClient raftClient, DomainManager domainManager, int interval, int timeoutSeconds, ActorRef<MigrationProposalVoter.VotingProtocol> localVoterRef, MigrationMapper migrationMapper) {
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

                        // Discover all QoS Improvement Suggester instances
                        CompletionStage<Receptionist.Listing> listingCompletion = discoverInstances(context, TIMEOUT_DURATION, QOS_IMPROVEMENT_SUGGESTER_SCHEDULER_KEY, listingResponseAdapter);

                        listingCompletion.thenCompose(listing -> {
//                            context.getLog().info("Retrieved listing: {}", listing);
                            Set<ActorRef<QoSImproveSuggestionProtocol>> suggesters =
                                    listing.getServiceInstances(QOS_IMPROVEMENT_SUGGESTER_SCHEDULER_KEY);

                            if (suggesters.isEmpty()) {
                                context.getLog().warn("No QoSImprovementSuggester instances found. Proceeding with the optimization stage...");
                                return CompletableFuture.completedFuture(null); // Skip further processing
                            }

                            // Send shutdown to each suggester and collect acknowledgment futures
                            Set<CompletionStage<Void>> shutdownAcks = suggesters.stream()
                                    .map(suggester -> AskPattern.askWithStatus(
                                            suggester,
                                            Shutdown::new,
                                            TIMEOUT_DURATION,
                                            context.getSystem().scheduler()
                                    ))
                                    .collect(Collectors.toSet());

                            // Combine all acknowledgments into a CompletableFuture
                            return CompletableFuture.allOf(
                                    shutdownAcks.stream()
                                            .map(CompletionStage::toCompletableFuture)
                                            .toArray(CompletableFuture[]::new)
                            );
                        }).thenRun(() -> {
                            context.getLog().info("All suggesters shut down. Proceeding with QoS improvement.");
                            MigrationCandidate migrationCandidate = domainManager.findMigrationCandidate();

                            if(migrationCandidate==null) {
                                return;
                            }
                            // Discover all MigrationProposalVoter instances and handle voting
                            discoverInstances(context, TIMEOUT_DURATION, MigrationProposalVoter.MIGRATION_PROPOSAL_VOTER_KEY, listingResponseAdapter)
                                    .thenCompose(listing -> {
                                        Set<ActorRef<MigrationProposalVoter.VotingProtocol>> migrationProposalVoterSet =
                                                listing.getServiceInstances(MigrationProposalVoter.MIGRATION_PROPOSAL_VOTER_KEY);

                                        // Filter out the local voter
                                        Set<ActorRef<MigrationProposalVoter.VotingProtocol>> remoteVoterSet = migrationProposalVoterSet.stream()
                                                .filter(voter -> !voter.equals(localVoterRef))
                                                .collect(Collectors.toSet());

                                        Set<CompletionStage<Boolean>> votes = collectVotes(remoteVoterSet, migrationCandidate, TIMEOUT_DURATION, context);

                                        // Combine all voting results and evaluate majority approval
                                        return CompletableFuture.allOf(
                                                        votes.stream()
                                                                .map(CompletionStage::toCompletableFuture)
                                                                .toArray(CompletableFuture[]::new)
                                                ).thenAccept(v -> {
                                                    if (shouldMigrate(votes, migrationProposalVoterSet.size(), context)) {
                                                        context.getLog().info("Sending the migration action via the RaftClient...");
                                                        MigrationAction migrationAction = migrationCandidate.toMigrationAction();

                                                        MigrationActionOuterClass.MigrationAction migrationActionProto = migrationMapper.toProto(migrationAction);
                                                        Message migrationActionMessage = Message.valueOf(ByteString.copyFrom(migrationActionProto.toByteArray()));

                                                        raftClient.async().send(migrationActionMessage)
                                                                .thenAccept(reply -> context.getLog().info("Migration action sent successfully via RaftClient: {} ", migrationAction))
                                                                .exceptionally(ex -> {
                                                                    context.getLog().error("Error while sending the migration action via RaftClient: ", ex);
                                                                    return null;
                                                                });;
                                                    }
                                                })
                                                .exceptionally(ex -> {
                                                    context.getLog().error("Error during the voting process: ", ex);
                                                    return null;
                                                });
                                    });
                        }).exceptionally(ex -> {
                            context.getLog().error("Failed to shut down all suggesters", ex);
                            return null;
                        });

                        return Behaviors.same();
                    })
                    .onMessage(Shutdown.class, message -> {
                        context.getLog().info("Received shutdown request");
                        discoverInstances(context, Duration.ofSeconds(5), QOS_IMPROVEMENT_SUGGESTER_SCHEDULER_KEY, context.messageAdapter(Receptionist.Listing.class, QoSImprovementSuggesterListings::new))
                                .thenAccept(listing -> {
                                    if (listing.getServiceInstances(QOS_IMPROVEMENT_SUGGESTER_SCHEDULER_KEY).contains(context.getSelf())) {
                                        // Deregister from the receptionist
                                        context.getLog().info("Deregistering the QoSImprovementSuggester from the receptionist...");
                                        context.getSystem().receptionist().tell(Receptionist.deregister(QOS_IMPROVEMENT_SUGGESTER_SCHEDULER_KEY, context.getSelf()));
                                    }
                                });
                        // Acknowledge shutdown and stop the actor
                        message.replyTo.tell(StatusReply.success(null));
                        return Behaviors.stopped();
                    })
                    .onMessage(StopCommand.class, message -> {
                        context.getLog().info("Stopping QoSImprovementSuggester");

                        discoverInstances(context, Duration.ofSeconds(5), QOS_IMPROVEMENT_SUGGESTER_SCHEDULER_KEY, context.messageAdapter(Receptionist.Listing.class, QoSImprovementSuggesterListings::new))
                                .thenAccept(listing -> {
                                    if (listing.getServiceInstances(QOS_IMPROVEMENT_SUGGESTER_SCHEDULER_KEY).contains(context.getSelf())) {
                                        // Deregister from the receptionist
                                        context.getLog().info("Deregistering the QoSImprovementSuggester from the receptionist...");
                                        context.getSystem().receptionist().tell(Receptionist.deregister(QOS_IMPROVEMENT_SUGGESTER_SCHEDULER_KEY, context.getSelf()));
                                    }
                                });

//                        // Deregister from the receptionist
//                        context.getSystem().receptionist().tell(Receptionist.deregister(QOS_IMPROVEMENT_SUGGESTER_SCHEDULER_KEY, context.getSelf()));
                        return Behaviors.stopped();
                    })
                    .build();
        }));
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
