package com.dreams.application.service;

import com.dreams.application.port.MigrationService;
import com.dreams.domain.model.MigrationAction;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import io.quarkus.arc.lookup.LookupIfProperty;
import lombok.extern.slf4j.Slf4j;

/**
 * Migration Execution Module (MEM) -- Migration Orchestrator.
 * Executes approved migration decisions by interacting with the Kubernetes API
 * to reschedule pods to target clusters/nodes.
 */
@Slf4j
@ApplicationScoped
@LookupIfProperty(name = "ldm.migration.mode", stringValue = "kubernetes")
public class KubernetesMigrationService implements MigrationService {

    @Inject
    KubernetesClient kubernetesClient;

    @Override
    public void executeMigration(MigrationAction migrationAction) {
        String microserviceId = migrationAction.microservice().getId();
        String targetClusterId = migrationAction.targetK8sCluster().getId();
        String targetLocation = migrationAction.targetK8sCluster().getLocation();

        log.info("Executing migration of {} to cluster {} ({})",
                microserviceId, targetClusterId, targetLocation);

        try {
            // Find the pod by label matching the microservice ID
            Pod pod = kubernetesClient.pods()
                    .inAnyNamespace()
                    .withLabel("app", microserviceId)
                    .list()
                    .getItems()
                    .stream()
                    .findFirst()
                    .orElse(null);

            if (pod == null) {
                log.warn("No pod found for microservice {}. Migration skipped.", microserviceId);
                return;
            }

            String podName = pod.getMetadata().getName();
            String namespace = pod.getMetadata().getNamespace();

            log.info("Found pod {} in namespace {} for microservice {}",
                    podName, namespace, microserviceId);

            // Add node affinity to target the pod to a specific node/cluster
            // In a real multi-cluster setup, this would use federation or
            // a migration framework like MS2M/SHADOW
            kubernetesClient.pods()
                    .inNamespace(namespace)
                    .withName(podName)
                    .edit(p -> {
                        if (p.getMetadata().getAnnotations() == null) {
                            p.getMetadata().setAnnotations(new java.util.HashMap<>());
                        }
                        p.getMetadata().getAnnotations().put(
                                "dreams.migration/target-cluster", targetClusterId);
                        p.getMetadata().getAnnotations().put(
                                "dreams.migration/target-location", targetLocation);
                        p.getMetadata().getAnnotations().put(
                                "dreams.migration/improvement-score",
                                String.valueOf(migrationAction.improvementScore()));
                        return p;
                    });

            // Delete the pod to trigger rescheduling (if managed by a Deployment/StatefulSet)
            kubernetesClient.pods()
                    .inNamespace(namespace)
                    .withName(podName)
                    .delete();

            log.info("Migration initiated: pod {} deleted for rescheduling to cluster {}",
                    podName, targetClusterId);

        } catch (Exception e) {
            log.error("Migration failed for microservice {}: {}", microserviceId, e.getMessage(), e);
        }
    }
}
