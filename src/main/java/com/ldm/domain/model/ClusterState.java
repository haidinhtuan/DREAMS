package com.ldm.domain.model;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public record ClusterState(Map<String, Microservice> microservices, long version,
                           LocalDateTime lastUpdated) {

    public ClusterState(Map<String, Microservice> microservices, long version, LocalDateTime lastUpdated) {
        this.microservices = new HashMap<>(microservices); // Defensive copy
        this.version = version;
        this.lastUpdated = lastUpdated;
    }

    @Override
    public Map<String, Microservice> microservices() {
        return new HashMap<>(microservices); // Defensive copy
    }

    public static ClusterState empty() {
        return new ClusterState(new HashMap<>(), 0, LocalDateTime.now());
    }

    public ClusterState withUpdatedMicroservice(String id, Microservice microservice) {
        Map<String, Microservice> updatedMicroservices = new HashMap<>(microservices);
        updatedMicroservices.put(id, microservice);
        return new ClusterState(updatedMicroservices, version + 1, LocalDateTime.now());
    }

    public ClusterState withoutMicroservice(String id) {
        Map<String, Microservice> updatedMicroservices = new HashMap<>(microservices);
        updatedMicroservices.remove(id);
        return new ClusterState(updatedMicroservices, version + 1, LocalDateTime.now());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ClusterState)) return false;
        ClusterState that = (ClusterState) o;
        return version == that.version &&
                lastUpdated == that.lastUpdated &&
                Objects.equals(microservices, that.microservices);
    }

    @Override
    public String toString() {
        return "ClusterState{" +
                "microservices=" + microservices +
                ", version=" + version +
                ", lastUpdated=" + lastUpdated +
                '}';
    }
}

