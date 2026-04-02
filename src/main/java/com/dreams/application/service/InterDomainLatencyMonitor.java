package com.dreams.application.service;

import io.quarkus.cache.*;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;

@ApplicationScoped
@Slf4j
public class InterDomainLatencyMonitor {

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
    public void removeLatencyEntry(@CacheKey String microserviceId) {
        cache.invalidate(microserviceId).await().indefinitely();
    }

    public void outputCache() {
        CaffeineCache caffeineCache = cache.as(CaffeineCache.class);

        log.debug("Current Microservices Cache: ");

        // Get all keys from the cache
        caffeineCache.keySet().forEach(key -> {
            // For each key, retrieve the value
            cache.get(key, k -> null)
                    .subscribe().with(
                            value -> log.debug(key + " -> " + value),
                            throwable -> log.error("Error accessing cache for key: " + key, throwable)
                    );
        });
    }
}
