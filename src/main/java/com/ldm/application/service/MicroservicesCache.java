package com.ldm.application.service;

import com.ldm.domain.model.Microservice;
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
//         Wait for the result synchronously
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

    // Reactive methods for backward compatibility

//    public Uni<Void> cacheMicroserviceReactive(String microserviceId, Microservice microservice) {
//        log.debug("Reactively caching microservice with ID: {}", microserviceId);
//        CaffeineCache caffeineCache = cache.as(CaffeineCache.class);
//        caffeineCache.put(microserviceId, CompletableFuture.completedFuture(microservice));
//        return Uni.createFrom().voidItem();
//    }

    public Uni<Void> cacheMicroserviceReactive(String microserviceId, Microservice microservice) {
        log.debug("Caching microservice with ID: {}", microserviceId);

        // Ensure no recursive update is triggered
        CaffeineCache caffeineCache = cache.as(CaffeineCache.class);
        if (caffeineCache.getIfPresent(microserviceId) == null) {
            caffeineCache.put(microserviceId, CompletableFuture.completedFuture(microservice));
            return Uni.createFrom().voidItem();
        } else {
            log.warn("Attempt to update an entry already being processed: {}", microserviceId);
        }
        return Uni.createFrom().voidItem();
    }



    public Uni<Void> removeMicroserviceByIdReactive(String microserviceId) {
        log.debug("Reactively removing microservice with ID: {}", microserviceId);
        return cache.invalidate(microserviceId)
                .onFailure().invoke(e -> log.error("Failed to remove microservice from cache: {}", e.getMessage()))
                .replaceWithVoid();
    }

    public Uni<List<Microservice>> getAllMicroservicesReactive() {
        log.debug("Reactively fetching all microservices from the cache.");
        return Uni.createFrom().item(() -> cache.as(CaffeineCache.class).keySet())
                .flatMap(keys -> Uni.combine().all().unis(keys.stream()
                        .map(key -> getMicroserviceById(key.toString()))
                        .collect(Collectors.toList())).combinedWith(results ->
                        results.stream().map(item -> (Microservice) item).collect(Collectors.toList())
                ));
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

//    public Uni<Void> updateMicroserviceIfExists(String microserviceId, Microservice updatedMicroservice) {
//        return getMicroserviceById(microserviceId)
//                .onItem().ifNotNull().call(existingMicroservice -> {
//                    log.debug("Microservice found in cache with ID: {}. Updating entry.", microserviceId);
//                    return cacheMicroserviceReactive(microserviceId, updatedMicroservice);
//                })
//                .onItem().ifNull().continueWith(() -> {
//                    log.debug("No microservice found in cache with ID: {}. No update performed.", microserviceId);
//                    return null;
//                })
//                .replaceWithVoid();
//    }

    public Uni<Void> updateMicroserviceIfExists(String microserviceId, Microservice updatedMicroservice) {
        return getMicroserviceById(microserviceId)
                .onItem().ifNotNull().call(existingMicroservice -> {
                    log.debug("Microservice found in cache with ID: {}. Updating entry.", microserviceId);

                    // Cache the updated microservice directly without modifying the existing one
                    return cacheMicroserviceReactive(microserviceId, updatedMicroservice);
                })
                .onItem().ifNull().continueWith(() -> {
                    log.debug("No microservice found in cache with ID: {}. No update performed.", microserviceId);
                    return null; // Necessary to keep the Uni<Void> chain consistent
                })
                .replaceWithVoid(); // Ensure the return type is always Uni<Void>
    }


    public Uni<Void> performActionIfEntryExistsReactive(String microserviceId, Runnable runnable) {
        log.debug("Checking if entry exists reactively for ID: {}", microserviceId);
        return getMicroserviceById(microserviceId)
                .onItem().ifNotNull().invoke(microservice -> {
                    log.debug("Entry exists for ID: {}. Performing action.", microserviceId);
                    runnable.run();
                })
                .onItem().ifNull().continueWith(() -> {
                    log.debug("Cache entry for ID {} does not exist.", microserviceId);
                    return null; // Returning null since this is just a continuation.
                })
                .replaceWithVoid(); // Ensures the method returns Uni<Void>.
    }

}

