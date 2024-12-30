package com.ldm.domain.model.testdata;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ldm.domain.model.Microservice;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class ClusterData {
    @JsonProperty("clusterId")
    private String clusterId;

    @JsonProperty("location")
    private String location;

    @JsonProperty("latencyToLDMs")
    private Map<String, Long> latencyToLDMs;

    @JsonProperty("microservices")
    private List<Microservice> microservices;
}
