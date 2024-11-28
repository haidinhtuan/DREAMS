package com.ldm.application.service;

import com.ldm.domain.model.Microservice;
import io.quarkus.cache.*;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@ApplicationScoped
public class MicroservicesCache {

    @CacheName("microservices")
    Cache cache;

    // Synchronously fetch a microservice from the cache by its ID
    @CacheResult(cacheName = "microservices")
    public Uni<Microservice> getMicroserviceById(@CacheKey String microserviceId) {
        return cache.get(microserviceId, k -> null);
//         Wait for the result synchronously
    }

    public void cacheMicroservice(String microserviceId, Microservice microservice) {
        cache.as(CaffeineCache.class).put(microserviceId, CompletableFuture.completedFuture(microservice));
    }

    // Synchronously invalidate (remove) a microservice's entry from the cache
    @CacheInvalidate(cacheName = "microservices")
    public void removeMicroservicePlacement(@CacheKey String microserviceId) {
        cache.invalidate(microserviceId).await().indefinitely();
    }

    // Retrieve all microservices from the cache using the underlying Caffeine cache
    public List<Microservice> getAllMicroservices() {
        // Get the list of Uni<Microservice> from the cache
        List<Uni<Microservice>> uniList = cache.as(CaffeineCache.class)
                .keySet()
                .parallelStream()
                .map(microserviceId -> getMicroserviceById((String) microserviceId))
                .collect(Collectors.toList());

        // Combine all Uni<Microservice> into a single Uni<List<Microservice>> and wait for the result
        return Uni.combine().all().unis(uniList).with(results -> results.stream()
                        .map(result -> (Microservice) result)
                        .collect(Collectors.toList()))
                .await().indefinitely();
    }

    public Uni<List<Microservice>> getAllMicroservicesNonBlocking() {
        // Get the list of Uni<Microservice> from the cache
        List<Uni<Microservice>> uniList = cache.as(CaffeineCache.class)
                .keySet()
                .parallelStream()
                .map(microserviceId -> getMicroserviceById((String) microserviceId))
                .collect(Collectors.toList());

        // Combine all Uni<Microservice> into a single Uni<List<Microservice>> and wait for the result
        return Uni.combine().all().unis(uniList).with(results -> results.stream()
                .map(result -> (Microservice) result)
                .collect(Collectors.toList()));
    }
}
//        return cache.as(CaffeineCache.class)
//                .keySet()
//                .parallelStream()  // Use parallel stream for large datasets
//                .map(microserviceId -> getMicroserviceById((String) microserviceId))
//                .toList();
//    }


    /*
            // Access the underlying Caffeine cache and call the asMap() method to get all cache entries
        Map<Object, CompletableFuture<Object>> caffeineCacheMap = cache.as(CaffeineCache.class);

        // Stream through the entries, resolve the CompletableFuture, and collect the Microservices
        return caffeineCacheMap.values().stream()
                .map(future -> (Microservice) future.join())  // Resolve the CompletableFuture
                .collect(Collectors.toSet());  // Collect all microservices into a set
     */

