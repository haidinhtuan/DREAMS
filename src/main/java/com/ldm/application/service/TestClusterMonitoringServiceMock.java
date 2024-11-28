package com.ldm.application.service;

import com.ldm.application.port.ClusterMonitoringService;
import com.ldm.domain.model.K8sCluster;
import com.ldm.domain.model.Microservice;
import com.ldm.domain.model.testdata.ClusterData;
import com.ldm.infrastructure.config.TestDataConfig;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.List;
import java.util.Map;

//@IfBuildProperty(name = "ldm.enable-cluster-monitoring", stringValue = "false")
@ApplicationScoped
@RequiredArgsConstructor
@Slf4j
public class TestClusterMonitoringServiceMock implements ClusterMonitoringService {

    @Getter
    public static Map<String, Long> latencyToLDMs;

    private final MicroservicesCache microservicesCache;

    private final TestDataInitializer testDataInitializer;

    private final TestDataConfig testDataConfig;

    @Override
    public void getMicroservicesFromCluster() {
        try {
            log.info("Getting test data from resource file: " + testDataConfig.file());
            ClusterData clusterData = this.testDataInitializer.getTestDataFromFile(testDataConfig.file());

            K8sCluster k8sCluster = new K8sCluster(clusterData.clusterId(), clusterData.location());

            // Set the K8sCluster details for each microservice and return the list
            List<Microservice> microservices = clusterData.microservices().stream()
                    .peek(microservice -> {
                        microservice.setK8sCluster(k8sCluster);
                        microservice.setName(microservice.getId());
                    }).toList();

            latencyToLDMs = clusterData.latencyToLDMs();

            log.info("Data loaded for cluster ID: {}", clusterData.clusterId());

            microservices.forEach(microservice -> this.microservicesCache.cacheMicroservice(microservice.getId(), microservice));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
