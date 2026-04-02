package com.dreams.infrastructure.adapter.out.pekko;

import com.dreams.domain.model.Microservice;
import com.dreams.domain.model.MigrationCandidate;
import com.dreams.infrastructure.serialization.protobuf.EvaluateMigrationProposalOuterClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorRefResolver;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.AskPattern;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

@Slf4j
public class VotingCoordinator {

    static Set<CompletionStage<Boolean>> collectVotes(Set<ActorRef<EvaluateMigrationProposalOuterClass.EvaluateMigrationProposal>> voterSet, MigrationCandidate candidate, Duration timeout, ActorContext<QoSImprovementSuggester.QoSImproveSuggestionProtocol> context) {
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
                    if (response instanceof Boolean vote) {
                        log.debug("Received Vote: " + vote);
                        return vote;
                    }
                    log.warn("Unexpected response type: {}", response.getClass());
                    return false;
                }))
                .collect(Collectors.toSet());
    }

    static boolean shouldMigrate(Set<CompletionStage<Boolean>> votes, long totalVoters) {
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
