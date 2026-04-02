package com.dreams.application.service;

import com.dreams.application.port.ClusterMonitoringService;
import com.dreams.domain.model.K8sCluster;
import com.dreams.domain.model.Microservice;
import com.dreams.domain.model.testdata.ClusterData;
import com.dreams.infrastructure.config.ActorSystemManager;
import com.dreams.infrastructure.config.LdmConfig;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.metrics.v1beta1.PodMetrics;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.quarkus.arc.lookup.LookupIfProperty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Domain Monitoring Module (DMM) -- Service Health Monitor.
 * Monitors microservice health by querying the Kubernetes Metrics API
 * for real-time CPU and memory usage of pods in the local domain.
 */
@Slf4j
@ApplicationScoped
@LookupIfProperty(name = "ldm.monitoring.mode", stringValue = "kubernetes")
public class KubernetesClusterMonitoringService implements ClusterMonitoringService {

    @Inject
    KubernetesClient kubernetesClient;

    @Inject
    LdmConfig ldmConfig;

    @Inject
    ServiceHealthMonitor serviceHealthMonitor;

    @Inject
    ActorSystemManager actorSystemManager;

    @Override
    public void getMicroservicesFromCluster() {
        log.info("Discovering microservices from Kubernetes cluster...");

        try {
            String namespace = "default";
            K8sCluster localCluster = new K8sCluster(ldmConfig.id(), "kubernetes");

            // Get all pods with the DREAMS-managed label
            List<Pod> pods = kubernetesClient.pods()
                    .inNamespace(namespace)
                    .withLabel("dreams.io/managed", "true")
                    .list()
                    .getItems();

            if (pods.isEmpty()) {
                log.warn("No DREAMS-managed pods found in namespace {}", namespace);
                return;
            }

            List<Microservice> microservices = new ArrayList<>();

            for (Pod pod : pods) {
                String podName = pod.getMetadata().getName();
                String appLabel = pod.getMetadata().getLabels().getOrDefault("app", podName);
                boolean nonMigratable = "true".equals(
                        pod.getMetadata().getLabels().getOrDefault("dreams.io/non-migratable", "false"));

                // Query metrics API for CPU and memory
                double cpuUsage = 0.0;
                double memoryUsage = 0.0;
                try {
                    PodMetrics metrics = kubernetesClient.top()
                            .pods()
                            .inNamespace(namespace)
                            .withName(podName)
                            .metric();
                    if (metrics != null && !metrics.getContainers().isEmpty()) {
                        var container = metrics.getContainers().get(0);
                        String cpuStr = container.getUsage().get("cpu").toString();
                        String memStr = container.getUsage().get("memory").toString();
                        cpuUsage = parseCpuMillicores(cpuStr);
                        memoryUsage = parseMemoryMb(memStr);
                    }
                } catch (Exception e) {
                    log.debug("Could not fetch metrics for pod {}: {}", podName, e.getMessage());
                }

                Microservice ms = Microservice.builder()
                        .id(appLabel)
                        .name(appLabel)
                        .isNonMigratable(nonMigratable)
                        .k8sCluster(localCluster)
                        .affinities(new HashMap<>())
                        .dataExchangedWithServices(new HashMap<>())
                        .messagesExchangedWithServices(null)
                        .dependsOnServices(null)
                        .sharedDeploymentEvents(null)
                        .privacyLevel(0)
                        .cpuUsage(cpuUsage)
                        .memoryUsage(memoryUsage)
                        .build();
                microservices.add(ms);
                serviceHealthMonitor.cacheMicroservice(ms.getId(), ms);

                log.info("Discovered microservice: {} (cpu={}%, mem={}MB, migratable={})",
                        appLabel, cpuUsage, memoryUsage, !nonMigratable);
            }

            // Initialize cluster state via Pekko
            ClusterData clusterData = new ClusterData();
            clusterData.setClusterId(ldmConfig.id());
            clusterData.setLocation("kubernetes");
            clusterData.setMicroservices(microservices);
            actorSystemManager.getActorSystem().tell(new ActorSystemManager.InitCluster(clusterData));

            log.info("Discovered {} microservices from Kubernetes cluster", microservices.size());

        } catch (Exception e) {
            log.error("Failed to discover microservices from Kubernetes: {}", e.getMessage(), e);
        }
    }

    private double parseCpuMillicores(String cpu) {
        if (cpu.endsWith("n")) {
            return Double.parseDouble(cpu.replace("n", "")) / 1_000_000.0;
        } else if (cpu.endsWith("m")) {
            return Double.parseDouble(cpu.replace("m", "")) / 10.0;
        }
        return Double.parseDouble(cpu) * 100.0;
    }

    private double parseMemoryMb(String memory) {
        if (memory.endsWith("Ki")) {
            return Double.parseDouble(memory.replace("Ki", "")) / 1024.0;
        } else if (memory.endsWith("Mi")) {
            return Double.parseDouble(memory.replace("Mi", ""));
        } else if (memory.endsWith("Gi")) {
            return Double.parseDouble(memory.replace("Gi", "")) * 1024.0;
        }
        return Double.parseDouble(memory) / (1024.0 * 1024.0);
    }
}
