package com.dreams.infrastructure.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

@ConfigMapping(prefix = "ldm")
public interface LdmConfig {
//   @Size(min = 1)
    String id();

    Proposal proposal();

    Voting voting();

    LatenciesCheck latenciesCheck();

    @WithName("affinity-weights")
    AffinityWeights affinityWeights();

    interface LatenciesCheck {
        @WithDefault("5")
        int timeout();

        @WithDefault("10")
        int interval();

        @WithDefault("3")
        int maxRetry();
    }

    interface Proposal {

        @WithDefault("30")
        int interval();

        @WithDefault("10")
        int requestTimeout();

        @WithDefault("0.2")
        double threshold();

        /**
         * The scaling factor used to adjust the sigmoid function's steepness in calculating the
         * latency penalty. A larger scaling factor results in a smoother transition for penalties,
         * while a smaller one makes the penalty more sensitive to affinity differences.
         */
        @WithDefault("20")
        double scalingFactor();

        @WithDefault("1")
        @WithName("batch-size")
        int batchSize();
    }

    interface Voting {
        @WithDefault("0.3")
        double threshold();

        /**
         * The scaling factor used to control the steepness of the sigmoid function. A lower value makes the penalty
         * more sensitive to affinity changes, while a higher value makes it smoother.
         */
        @WithDefault("10")
        double scalingFactor();
    }

    interface AffinityWeights {
        @WithDefault("0.3")
        double data();
        @WithDefault("0.25")
        double coupling();
        @WithDefault("0.2")
        double functional();
        @WithDefault("0.15")
        double operational();
        @WithDefault("0.1")
        double privacy();

        /** Operational balance factor γ_o (Eq. 12.3.4): 0 = pure similarity, 1 = pure contention */
        @WithDefault("0.5")
        @WithName("operational-balance-factor")
        double operationalBalanceFactor();
    }

    Migration migration();

    interface Migration {
        @WithDefault("experiment")
        String mode();
    }

    Monitoring monitoring();

    interface Monitoring {
        @WithDefault("experiment")
        String mode();
    }
}
