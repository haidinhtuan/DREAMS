package com.dreams.infrastructure.adapter.out.pekko;

import com.dreams.application.service.ClusterLatencyCache;
import com.dreams.infrastructure.adapter.in.pekko.PingService;
import com.dreams.infrastructure.serialization.protobuf.PingPong;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.receptionist.Receptionist;

import java.time.Duration;
import java.util.UUID;

public class PingManager {
    public interface Command {
    }

    private static final class Tick implements Command {
    }

    private static final class ListingResponse implements Command {
        final Receptionist.Listing listing;

        ListingResponse(Receptionist.Listing listing) {
            this.listing = listing;
        }
    }

    public static Behavior<Command> create(String ldmId, int pingInterval, int maxPingRetry, ClusterLatencyCache cache, ActorRef<PingPong.Ping> selfPingServiceActorRef ) {
        return Behaviors.setup(context -> Behaviors.withTimers(timersSetup -> {
            ActorRef<Receptionist.Listing> listingResponseAdapter =
                    context.messageAdapter(Receptionist.Listing.class, ListingResponse::new);

            timersSetup.startTimerAtFixedRate(new Tick(), Duration.ofSeconds(pingInterval));

            return Behaviors.receive(Command.class)
                    .onMessage(Tick.class, tick -> {
                        context.getLog().debug("Retrieving PingServices...");
                        context.getSystem().receptionist().tell(
                                Receptionist.find(PingService.PING_SERVICE_KEY, listingResponseAdapter)
                        );
                        return Behaviors.same();
                    })
                    .onMessage(ListingResponse.class, response -> {
                        response.listing.getServiceInstances(PingService.PING_SERVICE_KEY)
                                .stream()
                                .filter(pingServiceRef -> !pingServiceRef.equals(selfPingServiceActorRef))
                                .forEach(service -> {
                                    ActorRef<Pinger.Command> pinger = context.spawn(
                                            Pinger.create(service, ldmId, maxPingRetry, cache),
                                            "pinger-" + ldmId + "-" + service.path().name() + "-" + UUID.randomUUID() // Assign unique name for clarity
                                    );
                                    context.getLog().debug("Created Pinger ActorRef: {}", pinger);
                                });

                        return Behaviors.same();
                    })
                    .build();
        }));
    }
}
