package com.ldm.infrastructure.adapter.out.pekko;

import com.ldm.application.service.ClusterLatencyCache;
import com.ldm.application.service.TestClusterMonitoringServiceMock;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.Scheduler;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.AskPattern;
import org.apache.pekko.actor.typed.javadsl.Behaviors;

import java.time.Duration;
import java.util.concurrent.CompletionStage;

public class Pinger {
//    private static final int MAX_RETRIES = 3;
    private static final Duration BACKOFF = Duration.ofSeconds(5);
    private static final Duration TIMEOUT_DURATION = Duration.ofSeconds(3);

    // Sealed interface for all command types that this actor can handle
    public sealed interface Command permits WrappedPong, Stop, Retry {}

    // Wrapper for PingProtocol.Pong to make it a Command
    public static final class WrappedPong implements Command {
        public final PingProtocol.Pong pong;

        public WrappedPong(PingProtocol.Pong pong) {
            this.pong = pong;
        }
    }

    public static Behavior<Command> create(ActorRef<PingProtocol.Ping> pingService,
                                           String ldmId,
                                           int maxRetry,
                                           ClusterLatencyCache cache) {
        return Behaviors.setup(context -> new Pinger(context, pingService, ldmId, maxRetry, cache, 0).ping());
    }

    private final ActorContext<Command> context;
    private final ActorRef<PingProtocol.Ping> pingService;
    private final String ldmId;

    private final int maxRetry;
    private final ClusterLatencyCache cache;
    private final int retryCount;


    private Pinger(ActorContext<Command> context,
                   ActorRef<PingProtocol.Ping> pingService,
                   String ldmId,
                   int maxRetry,
                   ClusterLatencyCache cache,
                   int retryCount) {
        this.context = context;
        this.pingService = pingService;
        this.ldmId = ldmId;
        this.cache = cache;
        this.retryCount = retryCount;
        this.maxRetry = maxRetry;
    }

    private Behavior<Command> ping() {
        long startTime = System.nanoTime();

        // Use AskPattern.ask with Duration and Scheduler to send Ping
        Scheduler scheduler = context.getSystem().scheduler();
        CompletionStage<PingProtocol.Pong> result = AskPattern.ask(
                pingService,
                PingProtocol.Ping::new,
                TIMEOUT_DURATION,
                scheduler
        );

        // Map the response to WrappedPong and send it to self
        result.whenComplete((response, throwable) -> {
            if (response != null) {
                context.getSelf().tell(new WrappedPong(response));
            } else if (throwable != null) {
                context.getLog().error("Ping failed to {}: {}", pingService.path().address(), throwable.getMessage());

                if (retryCount < maxRetry) {
                    context.getLog().info("Retrying ping to {} (attempt {}/{})", pingService.path().address(), retryCount + 1, maxRetry);
                    context.getSystem().scheduler().scheduleOnce(
                            BACKOFF,
                            () -> context.getSelf().tell(new Retry(retryCount + 1)),  // Retry with incremented count
                            context.getExecutionContext()
                    );
                } else {
                    context.getLog().info("Setting max latency so the target ldm since the ping failed");
//                  TODO:  cache.cacheClusterLatency();
                    context.getLog().info("Max retries reached. Stopping Pinger.");
                    context.getSelf().tell(new Stop());
                }
            }
        });

        return Behaviors.receive(Command.class)
                .onMessage(WrappedPong.class, msg -> {
                    long latency = System.nanoTime() - startTime;
                    boolean isClusterMonitoringEnabled = false;

                    // TODO: For Testing/Experiments
//                    if(!ApplicationUtils.isClusterMonitoringEnabled()){
                    if(!isClusterMonitoringEnabled){
                        Long clusterLatencyFromTestData = TestClusterMonitoringServiceMock.latencyToLDMs.get(msg.pong.getLdmId());
                        context.getLog().info(">> TEST DATA: Latency to {}: {} ns", pingService.path().address(), clusterLatencyFromTestData);
                        cache.cacheClusterLatency(msg.pong.getLdmId(), clusterLatencyFromTestData);
                        //                    long clusterLatencyFromTestData = ExperimentUtil.getClusterLatencyByLdmId(ldmId, testDataConfig);
//                    if(clusterLatencyFromTestData==-1) throw new RuntimeException("Make sure that the latency to the LDM/Cluster is set properly in the test data!!");
                    } else {
                        context.getLog().info("Latency to {}: {} ns", pingService.path().address(), latency);
                        // Cache the calculated latency
                        cache.cacheClusterLatency(msg.pong.getLdmId(), latency);

                    }

                    return Behaviors.stopped();
                })
                .onMessage(Stop.class, msg -> Behaviors.stopped())
                .onMessage(Retry.class, msg -> new Pinger(context, pingService, ldmId, maxRetry, cache, msg.retryCount).ping())
                .build();
    }

    // Internal message classes for controlling behavior
    public static final class Stop implements Command {}

    public static final class Retry implements Command {
        final int retryCount;

        public Retry(int retryCount) {
            this.retryCount = retryCount;
        }
    }
}

