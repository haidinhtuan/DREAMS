package com.ldm.infrastructure.adapter.in.pekko;

import com.ldm.application.service.DomainManager;
import com.ldm.domain.model.MigrationCandidate;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.receptionist.ServiceKey;
import org.apache.pekko.pattern.StatusReply;

public class MigrationProposalVoter {

    public interface VotingProtocol {}

//    public record EvaluateMigrationProposal(MigrationCandidate migrationCandidate, ActorRef<StatusReply<Boolean>> replyTo) implements VotingProtocol {}

    public static class EvaluateMigrationProposal implements VotingProtocol {

        private final MigrationCandidate migrationCandidate;
        private final ActorRef<StatusReply<Boolean>> replyTo;

        public EvaluateMigrationProposal(MigrationCandidate migrationCandidate, ActorRef<StatusReply<Boolean>> replyTo) {
            this.replyTo = replyTo;
            this.migrationCandidate = migrationCandidate;
        }

        public ActorRef<StatusReply<Boolean>> getReplyTo() {
            return replyTo;
        }

        public MigrationCandidate getMigrationCandidate() {
            return migrationCandidate;
        }
    }
    public static final ServiceKey<VotingProtocol> MIGRATION_PROPOSAL_VOTER_KEY =
            ServiceKey.create(VotingProtocol.class, "MigrationProposalVoter");

    public static Behavior<VotingProtocol> create(String ldmId, DomainManager domainManager) {
        return Behaviors.setup(context -> {
//            context.getSystem().receptionist().tell(Receptionist.register(MIGRATION_PROPOSAL_VOTER_KEY, context.getSelf()));
            return Behaviors.receive(VotingProtocol.class)
                    .onMessage(EvaluateMigrationProposal.class, message -> {
                        context.getLog().info(ldmId+ ": "+"Evaluation migration proposal {}", message);
                        boolean approveMigrationProposal = domainManager.voteOnMigrationProposal(message.migrationCandidate.microservice(), message.migrationCandidate.targetK8sCluster().getId());
                        message.getReplyTo().tell(StatusReply.success(approveMigrationProposal));
                        return Behaviors.same();
                    })
                    .build();
        });
    }

}
