package com.ldm.infrastructure.adapter.out.pekko;

import com.ldm.application.service.ClusterLatencyCache;
import com.ldm.infrastructure.adapter.in.pekko.PingService;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.receptionist.Receptionist;

import java.time.Duration;
import java.util.Set;

public class PingManager {
    public interface Command {}

    private static final class Tick implements Command {}
    private static final class ListingResponse implements Command {
        final Receptionist.Listing listing;
        ListingResponse(Receptionist.Listing listing) {
            this.listing = listing;
        }
    }

    public static Behavior<Command> create(String ldmId, int pingInterval, int maxPingRetry, ClusterLatencyCache cache) {
        return Behaviors.setup(context -> Behaviors.withTimers(timersSetup -> {
            ActorRef<Receptionist.Listing> listingResponseAdapter =
                    context.messageAdapter(Receptionist.Listing.class, ListingResponse::new);

            timersSetup.startTimerAtFixedRate(new Tick(), Duration.ofSeconds(pingInterval));

            return Behaviors.receive(Command.class)
                    .onMessage(Tick.class, tick -> {
                        context.getSystem().receptionist().tell(
                                Receptionist.find(PingService.PING_SERVICE_KEY, listingResponseAdapter)
                        );
                        return Behaviors.same();
                    })
                    .onMessage(ListingResponse.class, response -> {
                        Set<ActorRef<PingProtocol.Ping>> services = response.listing.getServiceInstances(PingService.PING_SERVICE_KEY);
                        services.forEach(service -> {
                            // Generate a unique ID or use an existing ID for each service instance
//                            String ldmId = service.path().address().toString();
                            context.spawnAnonymous(Pinger.create(service, ldmId, maxPingRetry, cache));
                        });
                        return Behaviors.same();
                    })
                    .build();
        }));
    }
}
