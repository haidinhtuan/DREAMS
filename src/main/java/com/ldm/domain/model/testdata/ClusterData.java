package com.ldm.domain.model.testdata;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ldm.domain.model.Microservice;

import java.util.List;
import java.util.Map;

//@Data
public record ClusterData(@JsonProperty("clusterId") String clusterId, @JsonProperty("location") String location,
                          @JsonProperty("latencyToLDMs") Map<String, Long> latencyToLDMs,
                          @JsonProperty("microservices") List<Microservice> microservices) {

}
