package com.ldm.infrastructure.adapter.in.pekko;

import com.ldm.infrastructure.serialization.protobuf.PingPong;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorRefResolver;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.receptionist.Receptionist;
import org.apache.pekko.actor.typed.receptionist.ServiceKey;

public class PingService {
    public static final ServiceKey<PingPong.Ping> PING_SERVICE_KEY =
            ServiceKey.create(PingPong.Ping.class, "PingService");

    public static Behavior<PingPong.Ping> create(String ldmId) {
        return Behaviors.setup(context -> {
            context.getSystem().receptionist().tell(Receptionist.register(PING_SERVICE_KEY, context.getSelf()));
            return Behaviors.receive(PingPong.Ping.class)
                    .onMessage(PingPong.Ping.class, message -> {
                        ActorRefResolver actorRefResolver = ActorRefResolver.get(context.getSystem());
                        ActorRef<Object> replyTo = actorRefResolver.resolveActorRef(message.getReplyTo());
                        PingPong.Pong pong = PingPong.Pong.newBuilder().setLdmId(ldmId).setStartTime(message.getStartTime()).build();

                        context.getLog().info("Ping received and sending back Pong {}", replyTo);
                        replyTo.tell(pong);

                        return Behaviors.same();
                    })
                    .build();
        });
    }
}
