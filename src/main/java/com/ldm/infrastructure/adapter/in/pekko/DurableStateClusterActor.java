package com.ldm.infrastructure.adapter.in.pekko;

import com.ldm.domain.model.ClusterState;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.persistence.typed.PersistenceId;
import org.apache.pekko.persistence.typed.state.javadsl.CommandHandler;
import org.apache.pekko.persistence.typed.state.javadsl.DurableStateBehavior;

public class DurableStateClusterActor extends DurableStateBehavior<DurableStateClusterActor.Command, ClusterState> {
    private final ActorContext<Command> context;

    @Override
    public ClusterState emptyState() {
        return ClusterState.empty();
    }

    @Override
    public CommandHandler<Command, ClusterState> commandHandler() {
        return newCommandHandlerBuilder()
                .forAnyState()
                .onCommand(PersistClusterState.class, (state, command) -> {
                    context.getLog().info("Persisting cluster state: {}", command.state);
                    return Effect().persist(command.state()).thenRun(() -> {
                       context.getLog().info("Cluster state persisted successfully.");
                    });
                })
                .build();
    }

    public interface Command {}

    public record PersistClusterState(ClusterState state) implements Command {
    }

    public DurableStateClusterActor(PersistenceId persistenceId, ActorContext<Command> ctx) {
        super(persistenceId);
        this.context = ctx;
    }

    public static Behavior<Command> create(PersistenceId persistenceId) {
        return Behaviors.setup(ctx -> new DurableStateClusterActor(persistenceId, ctx));
    }
}
