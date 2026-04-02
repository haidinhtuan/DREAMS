package com.dreams.infrastructure.adapter.out.pekko;

import com.dreams.application.service.InterDomainLatencyMonitor;
import com.dreams.application.service.ClusterMonitoringMock;
import com.dreams.infrastructure.serialization.protobuf.PingPong;
import lombok.extern.slf4j.Slf4j;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorRefResolver;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.Scheduler;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.AskPattern;
import org.apache.pekko.actor.typed.javadsl.Behaviors;

import java.time.Duration;
import java.util.concurrent.CompletionStage;

@Slf4j
public class Pinger {
    private static final Duration BACKOFF = Duration.ofSeconds(5);
    private static final Duration TIMEOUT_DURATION = Duration.ofSeconds(3);

    // Sealed interface for all command types that this actor can handle
    public sealed interface Command permits WrappedPong, Stop, Retry {
    }

    // Wrapper for PingProtocol.Pong to make it a Command
    public record WrappedPong(PingPong.Pong pong) implements Command {
    }

    public static Behavior<Command> create(ActorRef<PingPong.Ping> pingService, String ldmId, int maxRetry, InterDomainLatencyMonitor cache) {
        return Behaviors.setup(context -> new Pinger(context, pingService, ldmId, maxRetry, cache, 0).ping());
    }

    private final ActorContext<Command> context;
    private final ActorRef<PingPong.Ping> pingService;
    private final String ldmId;

    private final int maxRetry;
    private final InterDomainLatencyMonitor cache;
    private final int retryCount;

    private Pinger(ActorContext<Command> context, ActorRef<PingPong.Ping> pingService, String ldmId, int maxRetry, InterDomainLatencyMonitor cache, int retryCount) {
        this.context = context;
        this.pingService = pingService;
        this.ldmId = ldmId;
        this.cache = cache;
        this.retryCount = retryCount;
        this.maxRetry = maxRetry;
    }

    private Behavior<Command> ping() {
        Scheduler scheduler = context.getSystem().scheduler();

        CompletionStage<PingPong.Pong> result = AskPattern.ask(pingService, replyTo -> PingPong.Ping.newBuilder().setReplyTo(ActorRefResolver.get(context.getSystem()).toSerializationFormat(replyTo)).setStartTime(System.nanoTime()).build(), TIMEOUT_DURATION, scheduler);

        result.whenComplete((response, throwable) -> {
            if (response != null) {
                log.debug("Received a response! Sending WrappedPong...");
                context.getSelf().tell(new WrappedPong(response));
            } else if (throwable != null) {
                context.getLog().error("Ping failed to {}: {}", pingService.path().address(), throwable.getMessage());

                if (retryCount < maxRetry) {
                    log.debug("Retrying ping to {} (attempt {}/{})", pingService.path().address(), retryCount + 1, maxRetry);
                    context.getSystem().scheduler().scheduleOnce(BACKOFF, () -> context.getSelf().tell(new Retry(retryCount + 1)),  // Retry with incremented count
                            context.getExecutionContext());
                } else {
                    log.warn("Setting max latency so the target ldm since the ping failed");
//                  TODO:  cache.cacheClusterLatency();
                    log.info("Max retries reached. Stopping Pinger.");
                    context.getSelf().tell(new Stop());
                }
            }
        });
        return Behaviors.receive(Command.class).onMessage(WrappedPong.class, msg -> {
            long latency = System.nanoTime() - msg.pong.getStartTime();
            boolean isClusterMonitoringEnabled = false;

            // TODO: For Testing/Experiments
            if (!isClusterMonitoringEnabled) {
                Long clusterLatencyFromTestData = ClusterMonitoringMock.latencyToLDMs.get(msg.pong.getLdmId());
                context.getLog().debug(">> TEST DATA: Latency to {}: {} ns", pingService.path().address(), clusterLatencyFromTestData);
                cache.cacheClusterLatency(msg.pong.getLdmId(), clusterLatencyFromTestData);
                //                    long clusterLatencyFromTestData = ExperimentUtil.getClusterLatencyByLdmId(ldmId, testDataConfig);
//                    if(clusterLatencyFromTestData==-1) throw new RuntimeException("Make sure that the latency to the LDM/Cluster is set properly in the test data!!");
            } else {
                context.getLog().debug("Latency to {}: {} ns", pingService.path().address(), latency);
                // Cache the calculated latency
                cache.cacheClusterLatency(msg.pong.getLdmId(), latency);
            }

            return Behaviors.stopped();
        }).onMessage(Stop.class, msg -> Behaviors.stopped()).onMessage(Retry.class, msg -> new Pinger(context, pingService, ldmId, maxRetry, cache, msg.retryCount).ping()).build();
    }

    // Internal message classes for controlling behavior
    public static final class Stop implements Command {
    }

    public static final class Retry implements Command {
        final int retryCount;

        public Retry(int retryCount) {
            this.retryCount = retryCount;
        }
    }
}

