package com.ldm.infrastructure.adapter.in.pekko;

import com.ldm.infrastructure.serialization.protobuf.PingPong;
import org.apache.pekko.actor.typed.*;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.receptionist.Receptionist;
import org.apache.pekko.actor.typed.receptionist.ServiceKey;

public class PingService {
    public static final ServiceKey<PingPong.Ping> PING_SERVICE_KEY =
            ServiceKey.create(PingPong.Ping.class, "PingService");

    public static Behavior<PingPong.Ping> create(String ldmId) {
        // Wrap behavior creation with supervision strategy
        return Behaviors.supervise(
                createBehavior(ldmId) // Call a method to build the main behavior
        ).onFailure(SupervisorStrategy.restart().withLoggingEnabled(true));
    }

    private static Behavior<PingPong.Ping> createBehavior(String ldmId) {
        // Define the main behavior
        return Behaviors.setup((ActorContext<PingPong.Ping> context) -> {
            registerWithReceptionist(context); // Register the actor with the Receptionist

            // Define the actor behavior (message and signal handling)
            return Behaviors.receive(PingPong.Ping.class)
                    .onMessage(PingPong.Ping.class, message -> handlePingMessage(context, message, ldmId))
                    .onAnyMessage(message -> {
                        context.getLog().warn("Received unhandled message of type: {}", message.getClass());
                        return Behaviors.unhandled();
                    })
                    .onSignal(PreRestart.class, signal -> {
                        context.getLog().warn("PreRestart Signal Received on PingService: {}", PING_SERVICE_KEY.id());
                        return Behaviors.same();
                    })
                    .onSignal(PostStop.class, signal -> {
                        context.getLog().info("PostStop Signal Received on PingService: {}", PING_SERVICE_KEY.id());
                        return Behaviors.same();
                    })
                    .build(); // Build the behavior
        });
    }

    // Method to register with the Receptionist
    private static void registerWithReceptionist(ActorContext<PingPong.Ping> context) {
        context.getSystem().receptionist().tell(Receptionist.register(PING_SERVICE_KEY, context.getSelf()));
        context.getLog().info("Registered PingService with Receptionist under key: {}", PING_SERVICE_KEY.id());
    }

    // Handle incoming Ping messages
    private static Behavior<PingPong.Ping> handlePingMessage(ActorContext<PingPong.Ping> context, PingPong.Ping message, String ldmId) {
        try {
            // Resolve the ActorRef to send the Pong back
            ActorRefResolver resolver = ActorRefResolver.get(context.getSystem());
            ActorRef<Object> replyTo = resolver.resolveActorRef(message.getReplyTo());

            // Build the Pong message
            PingPong.Pong pong = PingPong.Pong.newBuilder()
                    .setLdmId(ldmId)
                    .setStartTime(message.getStartTime())
                    .build();

            // Log and send the Pong response
            context.getLog().debug("Ping received from {}. Sending back Pong: {}", replyTo, pong);
            replyTo.tell(pong);

            return Behaviors.same(); // Continue with the same behavior
        } catch (Exception ex) {
            // Log any exceptions and allow the supervision strategy to handle them
            context.getLog().error("Failed to process Ping message: {}", ex.getMessage(), ex);
            throw ex;
        }
    }
}
