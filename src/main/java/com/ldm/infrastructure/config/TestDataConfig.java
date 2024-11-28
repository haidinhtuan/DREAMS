package com.ldm.infrastructure.config;

import io.smallrye.config.ConfigMapping;

@ConfigMapping(prefix = "testdata")
public interface TestDataConfig {

    String file();


//    List<LatencyConfig> latencies();  // Use a list to allow dynamic configuration via environment variables
//
//    interface LatencyConfig {
//        String id();  // Cluster ID, e.g., Cluster2
//
//        int latency();  // Latency value in ms
//    }

//    /**
//     * Mock latencies to other clusters by cluster ID.
//     * Example configuration:
//     * <pre>
//     *     ldm:
//     *       latencies:
//     *         Cluster2: 80
//     *         Cluster3: 250
//     * </pre>
//     *
//     * @return Map of cluster IDs to latency values in milliseconds.
//     */
//    Map<String, Integer> latencies();

}
