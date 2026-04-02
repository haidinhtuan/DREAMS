package com.dreams.application.service;

import com.dreams.domain.model.Microservice;
import io.quarkus.cache.*;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@ApplicationScoped
@Slf4j
public class MicroservicesCache {

    @CacheName("microservices")
    Cache cache;

    // Synchronously fetch a microservice from the cache by its ID
    @CacheResult(cacheName = "microservices")
    public Uni<Microservice> getMicroserviceById(@CacheKey String microserviceId) {
        return cache.get(microserviceId, k -> null);
    }

    public void cacheMicroservice(String microserviceId, Microservice microservice) {
        cache.as(CaffeineCache.class).put(microserviceId, CompletableFuture.completedFuture(microservice));
    }

    // Synchronously invalidate (remove) a microservice's entry from the cache
    @CacheInvalidate(cacheName = "microservices")
    public void removeMicroserviceById(@CacheKey String microserviceId) {
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

    public void outputCache() {
        CaffeineCache caffeineCache = cache.as(CaffeineCache.class);

        log.debug("Microservices Cache: ");

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

    public Uni<Void> removeMicroserviceByIdReactive(String microserviceId) {
        log.debug("Reactively removing microservice with ID: {}", microserviceId);
        return cache.invalidate(microserviceId)
                .onFailure()
                .invoke(e -> log.error("Failed to remove microservice from cache: {}", e.getMessage()));
    }



    public List<Uni<Microservice>> getAllMicroservicesAsUnisReactive() {
        log.debug("Reactively fetching all microservices from the cache as individual Unis.");
        return cache.as(CaffeineCache.class)
                .keySet()
                .stream()
                .map(key -> getMicroserviceById(key.toString()))
                .collect(Collectors.toList());
    }

    public Multi<Microservice> getAllMicroservicesAsMultiReactive() {
        log.debug("Reactively fetching all microservices from the cache as a Multi.");
        return Multi.createFrom().items(cache.as(CaffeineCache.class).keySet().stream())
                .onItem().transformToUniAndMerge(key -> getMicroserviceById(key.toString()));
    }

    public Uni<Void> addMicroserviceIfNotExistsReactive(String microserviceId, Microservice microservice) {
        log.debug("Caching microservice with ID: {}", microserviceId);

        CaffeineCache caffeineCache = cache.as(CaffeineCache.class);
        if (caffeineCache.getIfPresent(microserviceId) == null) {
            caffeineCache.put(microserviceId, CompletableFuture.completedFuture(microservice));
        } else {
            log.warn("No cache update has been performed since the microservice was not found in the cache: {}", microserviceId);
        }

        return Uni.createFrom().voidItem();
    }

    public Uni<Void> updateMicroserviceIfExists(String microserviceId, Microservice updatedMicroservice) {
        return Uni.createFrom().item(() -> this.cache.as(CaffeineCache.class).getIfPresent(microserviceId))
                .onItem().transformToUni(microserviceFuture -> {
                    log.debug("microserviceFuture Received");
                    if (microserviceFuture != null) {
                        log.debug("microserviceFuture is NOT null");
                        log.debug("microserviceFuture: {}", microserviceFuture);
                        // Convert the CompletableFuture to a Uni
                        return Uni.createFrom().completionStage(microserviceFuture)
                                .onItem().transformToUni(existingMicroservice -> {
                                    log.debug("Microservice found in cache with ID: {}. Updating entry.", microserviceId);
                                    return removeMicroserviceByIdReactive(microserviceId)
                                            .onItem()
                                            .transformToUni(unused -> addMicroserviceIfNotExistsReactive(microserviceId, updatedMicroservice));
                                });
                    } else {
                        log.debug("No microservice found in cache with ID: {}. No update performed.", microserviceId);
                        return Uni.createFrom().voidItem(); // No action needed
                    }
                })
                .replaceWithVoid();
    }
}

