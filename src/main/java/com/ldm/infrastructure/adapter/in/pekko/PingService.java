package com.ldm.infrastructure.adapter.in.pekko;

import com.ldm.infrastructure.adapter.out.pekko.PingProtocol;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.receptionist.Receptionist;
import org.apache.pekko.actor.typed.receptionist.ServiceKey;

public class PingService {
    public static final ServiceKey<PingProtocol.Ping> PING_SERVICE_KEY =
            ServiceKey.create(PingProtocol.Ping.class, "PingService");

    public static Behavior<PingProtocol.Ping> create(String ldmId) {
        return Behaviors.setup(context -> {
            context.getSystem().receptionist().tell(Receptionist.register(PING_SERVICE_KEY, context.getSelf()));
            return Behaviors.receive(PingProtocol.Ping.class)
                    .onMessage(PingProtocol.Ping.class, message -> {
                        context.getLog().info("Ping received from {}", message.getReplyTo());
                        message.getReplyTo().tell(new PingProtocol.Pong(ldmId));
                        return Behaviors.same();
                    })
                    .build();
        });
    }
}
