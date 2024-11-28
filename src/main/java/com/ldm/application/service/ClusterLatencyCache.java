package com.ldm.application.service;

import io.quarkus.cache.*;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.concurrent.CompletableFuture;

@ApplicationScoped
public class ClusterLatencyCache {

    @CacheName("ldm-service-latencies")
    Cache cache;

    // Synchronously fetch a microservice from the cache by its ID
    @CacheResult(cacheName = "ldm-service-latencies")
    public Long getLatencyToLDMById(@CacheKey String ldmId) {
        return (Long) cache.get(ldmId, k -> null)
                .await().indefinitely();  // Wait for the result synchronously
    }

    public void cacheClusterLatency(String ldmId, Long latency){
        cache.as(CaffeineCache.class).put(ldmId, CompletableFuture.completedFuture(latency));
    }

    // Synchronously invalidate (remove) a microservice's entry from the cache
    @CacheInvalidate(cacheName = "ldm-service-latencies")
    public void removeMicroservicePlacement(@CacheKey String microserviceId) {
        cache.invalidate(microserviceId).await().indefinitely();
    }
}
