package com.dreams.application.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.dreams.domain.model.K8sCluster;
import com.dreams.domain.model.Microservice;
import com.dreams.infrastructure.adapter.in.orm.LdmStateRepository;
import com.dreams.infrastructure.persistence.entity.LdmState;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonObjectBuilder;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApplicationScoped
@Slf4j
public class LdmStateService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Inject
    LdmStateRepository repository;

    public JsonObjectBuilder getJsonObjectBuilder(List<LdmState> states) {
        JsonObjectBuilder graphBuilder = Json.createObjectBuilder();
        var nodes = Json.createArrayBuilder();
        var edges = Json.createArrayBuilder();

        for (LdmState state : states) {
            // Add node
            nodes.add(Json.createObjectBuilder()
                    .add("data", Json.createObjectBuilder()
                            .add("id", state.getMicroserviceId())
                            .add("label", state.getMicroserviceId())
                            .add("clusterId", state.getK8sClusterId())
                            .add("location", state.getK8sClusterLocation())
                    ));

            // Parse microservice_affinities as edges
            Map<String, Double> microserviceAffinities = state.getMicroserviceAffinities();
            Map<Microservice, Double> affinities = parseAffinities(microserviceAffinities);
            for (var entry : affinities.entrySet()) {
                edges.add(Json.createObjectBuilder()
                        .add("data", Json.createObjectBuilder()
                                .add("source", state.getMicroserviceId())
                                .add("target", entry.getKey().getId())
                                .add("weight", entry.getValue())
                        ));
            }
        }

        return graphBuilder.add("nodes", nodes).add("edges", edges);
    }

    @Transactional
    public JsonObjectBuilder getGraphData() {
        List<LdmState> states = repository.listAll();

        return getJsonObjectBuilder(states);
    }

    public Map<Microservice, Double> parseAffinities(Map<String, Double> rawAffinities) {
        Map<Microservice, Double> affinities = new HashMap<>();
        for (Map.Entry<String, Double> entry : rawAffinities.entrySet()) {
            try {
                Microservice microservice = parseMicroserviceFromString(entry.getKey());
                affinities.put(microservice, entry.getValue());
            } catch (Exception e) {
                log.error("Failed to parse microservice from string: {}", entry.getKey(), e);
            }
        }
        return affinities;
    }

    public Map<Microservice, Double> parseAffinities(String affinitiesJson) {
        try {
            // Deserialize JSON into a Map<String, Double>
            Map<String, Double> rawAffinities = objectMapper.readValue(
                    affinitiesJson, new TypeReference<Map<String, Double>>() {
                    }
            );

            // Convert String keys to Microservice objects
            Map<Microservice, Double> affinities = new HashMap<>();
            for (Map.Entry<String, Double> entry : rawAffinities.entrySet()) {
                Microservice microservice = parseMicroserviceFromString(entry.getKey());
                affinities.put(microservice, entry.getValue());
            }

            return affinities;
        } catch (Exception e) {
            // Log and return an empty map in case of error
            log.error("Parsing microservice affinities failed!", e);
            return Collections.emptyMap();
        }
    }

    private Microservice parseMicroserviceFromString(String microserviceString) {
        try {
            // Match the fields in the string representation of the Microservice
            Pattern pattern = Pattern.compile(
                    "Microservice\\{id='(.*?)', name='(.*?)', isNonMigratable=(.*?), k8sCluster=(.*?) \\((.*?)\\).*?\\}"
            );
            Matcher matcher = pattern.matcher(microserviceString);

            if (matcher.matches()) {
                String id = matcher.group(1);
                String name = matcher.group(2);
                boolean isNonMigratable = Boolean.parseBoolean(matcher.group(3));
                String k8sClusterId = matcher.group(4);
                String k8sClusterLocation = matcher.group(5);

                // Create and return the Microservice object
                return new Microservice(id, name, isNonMigratable, new K8sCluster(k8sClusterId, k8sClusterLocation), null, null, 0.0, 0.0);
            } else {
                throw new IllegalArgumentException("Invalid Microservice string format: " + microserviceString);
            }
        } catch (Exception e) {
            log.error("Failed to parse Microservice from string: {}", microserviceString, e);
            return null; // Handle the error appropriately
        }
    }
}
