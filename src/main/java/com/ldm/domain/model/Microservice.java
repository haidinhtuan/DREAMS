package com.ldm.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.ldm.infrastructure.json.MicroserviceDeserializer;
import com.ldm.infrastructure.json.MicroserviceSerializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonDeserialize(using = MicroserviceDeserializer.class)
@JsonSerialize(using = MicroserviceSerializer.class)
public class Microservice {

    @JsonProperty("id")
    private String id;
    private String name;

    // ---
    @JsonProperty("isNonMigratable")
    private boolean isNonMigratable;
    private K8sCluster k8sCluster;

    @JsonProperty("affinities")
    private Map<Microservice, Double> affinities; // Affinities with other microservices (microserviceId -> affinity score)
    private Map<Microservice, Double> dataExchangedWithServices;  // Data exchanged with other microservices in MB

    // ---


    public void addAffinity(Microservice microservice, double affinityScore) {
        this.affinities.put(microservice, affinityScore);
    }


    private double cpuUsage;  // CPU usage as a percentage
    private double memoryUsage;  // Memory usage in MB




    // Adds the amount of data exchanged with another microservice
    public void addDataExchanged(Microservice otherService, double dataExchanged) {
        this.dataExchangedWithServices.put(otherService, dataExchanged);
    }

    // Retrieves the amount of data exchanged with a specific microservice
    public double getDataExchangedWith(Microservice otherService) {
        return this.dataExchangedWithServices.getOrDefault(otherService, 0.0);
    }

    // Returns the total amount of data exchanged with all other services
    public double getTotalDataExchanged() {
        return this.dataExchangedWithServices.values().stream().mapToDouble(Double::doubleValue).sum();
    }

    // Override equals method to compare Microservice objects based on serviceId
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Microservice that = (Microservice) o;
        return Objects.equals(this.id, that.getId());
    }

    // Override hashCode method to return hash based on serviceId
    @Override
    public int hashCode() {
        return Objects.hash(this.id);
    }

    @JsonIgnore
    // Method to calculate total affinity per cluster
    public Map<K8sCluster, Double> getTotalAffinityPerCluster() {
        return affinities.entrySet().stream()
                .collect(Collectors.groupingBy(
                        entry -> entry.getKey().getK8sCluster(),
                        Collectors.summingDouble(Map.Entry::getValue)
                ));
    }

    // Method to calculate total affinity to local microservices (on the same cluster)
    public double getTotalLocalAffinity() {
        return affinities.entrySet().stream()
                .filter(entry -> entry.getKey().getK8sCluster().equals(this.k8sCluster))
                .mapToDouble(Map.Entry::getValue)
                .sum();
    }



    @JsonIgnore// Method to return the K8sCluster with the highest total affinity and the total affinity value
    public Map.Entry<K8sCluster, Double> getK8sClusterWithHighestAffinity() {
        return getTotalAffinityPerCluster().entrySet().stream()
                .max(Comparator.comparingDouble(Map.Entry::getValue)) // Find the entry with the highest affinity value
                .orElse(null); // Return null if no entries are found
    }

    public double getTotalAffinityToCluster(String targetLdmId) {
        return affinities.entrySet().stream()
                .filter(entry -> entry.getKey().getK8sCluster().getId().equalsIgnoreCase(targetLdmId))
                .mapToDouble(Map.Entry::getValue).sum(); // Sum the affinity values for the target cluster
    }
}
