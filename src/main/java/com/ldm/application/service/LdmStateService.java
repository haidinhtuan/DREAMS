package com.ldm.application.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ldm.domain.entity.LdmState;
import com.ldm.domain.model.Microservice;
import com.ldm.infrastructure.adapter.in.orm.LdmStateRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonObjectBuilder;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@ApplicationScoped
@Slf4j
public class LdmStateService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Inject
    LdmStateRepository repository;

    public JsonObjectBuilder getGraphData() {
        List<LdmState> states = repository.listAll();

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
            String affinitiesJson = state.getMicroserviceAffinities();
            Map<Microservice, Double> affinities = parseAffinities(affinitiesJson);
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

    public Map<Microservice, Double> parseAffinities(String affinitiesJson) {
        try {
            // Deserialize JSON into a Map<Microservice, Double>
            objectMapper.findAndRegisterModules(); // Ensure custom serializers/deserializers are registered
            return objectMapper.readValue(affinitiesJson, new TypeReference<Map<Microservice, Double>>() {});
        } catch (Exception e) {
            // Log and return an empty map in case of error
            e.printStackTrace();
            return Collections.emptyMap();
        }
    }
}
