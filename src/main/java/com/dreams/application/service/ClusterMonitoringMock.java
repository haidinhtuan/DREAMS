package com.dreams.application.service;

import com.dreams.application.port.ClusterMonitoringService;
import com.dreams.domain.model.K8sCluster;
import com.dreams.domain.model.Microservice;
import com.dreams.domain.model.testdata.ClusterData;
import com.dreams.infrastructure.config.ActorSystemManager;
import com.dreams.infrastructure.config.TestDataConfig;
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

    private final ActorSystemManager actorSystemManager;

    @Override
    public void getMicroservicesFromCluster() {
        try {
            log.info("Getting test data from resource file: " + testDataConfig.file());
            ClusterData clusterData = this.testDataInitializer.getTestDataFromFile(testDataConfig.file());

            K8sCluster k8sCluster = new K8sCluster(clusterData.getClusterId(), clusterData.getLocation());

            clusterData.getMicroservices().forEach(microservice -> {
                microservice.setK8sCluster(k8sCluster);
                microservice.setName(microservice.getId());
            });

            List<Microservice> microservices = clusterData.getMicroservices();

            latencyToLDMs = clusterData.getLatencyToLDMs();

            log.info("Data loaded for cluster ID: {}", clusterData.getClusterId());

            microservices.forEach(microservice -> this.microservicesCache.cacheMicroservice(microservice.getId(), microservice));

            clusterData.setMicroservices(microservices);

            this.actorSystemManager.getActorSystem().tell(new ActorSystemManager.InitCluster(clusterData));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
