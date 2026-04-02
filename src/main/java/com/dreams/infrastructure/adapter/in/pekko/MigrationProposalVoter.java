package com.dreams.infrastructure.adapter.in.pekko;

import com.dreams.application.service.MigrationEligibilityEvaluator;
import com.dreams.domain.model.Microservice;
import com.dreams.domain.model.MigrationCandidate;
import com.dreams.infrastructure.mapper.MicroserviceMapper;
import com.dreams.infrastructure.serialization.protobuf.EvaluateMigrationProposalOuterClass;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorRefResolver;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.receptionist.Receptionist;
import org.apache.pekko.actor.typed.receptionist.ServiceKey;
import org.apache.pekko.pattern.StatusReply;

public class MigrationProposalVoter {

    public interface VotingProtocol {
    }

    public record EvaluateMigrationProposal(MigrationCandidate migrationCandidate,
                                            ActorRef<StatusReply<Boolean>> replyTo) implements VotingProtocol {}

    public static final ServiceKey<EvaluateMigrationProposalOuterClass.EvaluateMigrationProposal> MIGRATION_PROPOSAL_VOTER_KEY =
            ServiceKey.create(EvaluateMigrationProposalOuterClass.EvaluateMigrationProposal.class, "MigrationProposalVoter");

    public static Behavior<EvaluateMigrationProposalOuterClass.EvaluateMigrationProposal> create(String ldmId, MigrationEligibilityEvaluator domainManager, MicroserviceMapper microserviceMapper) {
        return Behaviors.setup(context -> {
            context.getSystem().receptionist().tell(Receptionist.register(MIGRATION_PROPOSAL_VOTER_KEY, context.getSelf()));
            return Behaviors.receive(EvaluateMigrationProposalOuterClass.EvaluateMigrationProposal.class)
                    .onMessage(EvaluateMigrationProposalOuterClass.EvaluateMigrationProposal.class, message -> {
                        context.getLog().info("[LDM: " +  ldmId + "]: " + "Evaluation migration proposal {}", message);

                        ActorRefResolver resolver = ActorRefResolver.get(context.getSystem());
                        ActorRef<Object> replyTo = resolver.resolveActorRef(message.getReplyToActorPath());

                        Microservice microservice = microserviceMapper.toDomainModel(message.getMigrationCandidate().getMicroservice());
                        context.getLog().debug("Checking if the following microservice should be migrated to prepare the vote: {}", microservice);

                        boolean approveMigrationProposal = domainManager.voteOnMigrationProposal(microservice, message.getMigrationCandidate().getTargetK8SCluster().getId());
                        replyTo.tell(StatusReply.success(approveMigrationProposal));

                        return Behaviors.same();
                    })
                    .build();
        });
    }

}
