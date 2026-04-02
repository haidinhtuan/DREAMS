package com.dreams.domain.saga;

import com.dreams.domain.model.Microservice;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * SAGA Affinity-based Clusterer (SAC) — reconstructs the affinity graph,
 * applies min-max normalization (Eq. 12.2), and uses graph partitioning
 * to determine optimal microservice placement.
 *
 * Implements a simplified version of the k-way Kernighan-Lin algorithm
 * for minimum-weight k-cut partitioning (Algorithm 1, Ch. 12.4).
 *
 * Full algorithm implementation: https://github.com/haidinhtuan/k-way-Kernighan-Lin-algorithm
 */
@ApplicationScoped
@Slf4j
public class AffinityClusterer {

    /**
     * Partition microservices into k clusters minimizing inter-cluster affinity.
     * Uses a greedy heuristic inspired by the Kernighan-Lin algorithm.
     *
     * @param graph The affinity graph with normalized weights
     * @param k Number of target clusters
     * @return Map of cluster ID to list of microservices
     */
    public Map<Integer, List<Microservice>> partition(AffinityGraph graph, int k) {
        log.info("SAC: Partitioning {} microservices into {} clusters", graph.size(), k);

        // Apply min-max normalization (Eq. 12.2)
        graph.normalizeWeights();

        List<Microservice> vertices = new ArrayList<>(graph.getVertices());
        Map<Integer, List<Microservice>> clusters = new HashMap<>();

        // Initial assignment: distribute evenly across k clusters
        for (int i = 0; i < vertices.size(); i++) {
            int clusterId = i % k;
            clusters.computeIfAbsent(clusterId, c -> new ArrayList<>()).add(vertices.get(i));
        }

        // Iterative improvement: swap pairs that reduce inter-cluster affinity
        boolean improved = true;
        int maxIterations = 100;
        int iteration = 0;

        while (improved && iteration < maxIterations) {
            improved = false;
            iteration++;

            for (int ci = 0; ci < k; ci++) {
                for (int cj = ci + 1; cj < k; cj++) {
                    List<Microservice> clusterI = clusters.get(ci);
                    List<Microservice> clusterJ = clusters.get(cj);

                    if (clusterI == null || clusterJ == null) continue;

                    // Find best swap pair
                    double bestGain = 0;
                    Microservice bestA = null, bestB = null;

                    for (Microservice a : clusterI) {
                        for (Microservice b : clusterJ) {
                            double gain = computeSwapGain(a, b, clusterI, clusterJ, graph);
                            if (gain > bestGain) {
                                bestGain = gain;
                                bestA = a;
                                bestB = b;
                            }
                        }
                    }

                    if (bestA != null && bestGain > 0) {
                        clusterI.remove(bestA);
                        clusterJ.remove(bestB);
                        clusterI.add(bestB);
                        clusterJ.add(bestA);
                        improved = true;
                        log.debug("SAC: Swapped {} and {} with gain {}", bestA.getId(), bestB.getId(), bestGain);
                    }
                }
            }
        }

        log.info("SAC: Partitioning completed in {} iterations", iteration);
        return clusters;
    }

    /**
     * Compute the gain from swapping microservices a and b between their clusters.
     * gain = D[a] + D[b] - 2 * c(a,b)
     * where D[x] = external cost - internal cost for node x
     */
    private double computeSwapGain(Microservice a, Microservice b,
                                    List<Microservice> clusterA, List<Microservice> clusterB,
                                    AffinityGraph graph) {
        double externalA = 0, internalA = 0;
        for (Microservice ms : clusterA) {
            if (!ms.equals(a)) internalA += graph.getEdgeWeight(a.getId(), ms.getId());
        }
        for (Microservice ms : clusterB) {
            externalA += graph.getEdgeWeight(a.getId(), ms.getId());
        }

        double externalB = 0, internalB = 0;
        for (Microservice ms : clusterB) {
            if (!ms.equals(b)) internalB += graph.getEdgeWeight(b.getId(), ms.getId());
        }
        for (Microservice ms : clusterA) {
            externalB += graph.getEdgeWeight(b.getId(), ms.getId());
        }

        double dA = externalA - internalA;
        double dB = externalB - internalB;
        double cab = graph.getEdgeWeight(a.getId(), b.getId());

        return dA + dB - 2 * cab;
    }
}
