package com.dreams.domain.service;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Exponential Moving Average (EMA) smoothing for latency measurements.
 * Reduces noise from transient network spikes in migration decisions.
 *
 * EMA formula: EMA_t = α × value_t + (1 - α) × EMA_{t-1}
 * where α (smoothing factor) controls responsiveness to new data.
 */
@ApplicationScoped
@Slf4j
public class LatencySmoothing {

    /** Smoothing factor: 0.3 balances responsiveness and stability */
    private static final double ALPHA = 0.3;

    private final Map<String, Double> emaValues = new ConcurrentHashMap<>();

    /**
     * Update the EMA for a given cluster and return the smoothed value.
     */
    public long update(String clusterId, long rawLatency) {
        double current = emaValues.getOrDefault(clusterId, (double) rawLatency);
        double smoothed = ALPHA * rawLatency + (1 - ALPHA) * current;
        emaValues.put(clusterId, smoothed);
        log.debug("Latency EMA for {}: raw={}ms, smoothed={}ms", clusterId, rawLatency, Math.round(smoothed));
        return Math.round(smoothed);
    }

    /**
     * Get the current smoothed latency for a cluster.
     */
    public long getSmoothedLatency(String clusterId) {
        return Math.round(emaValues.getOrDefault(clusterId, 0.0));
    }

    /**
     * Reset all smoothed values (e.g., after a network topology change).
     */
    public void reset() {
        emaValues.clear();
    }
}
