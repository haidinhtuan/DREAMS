package com.dreams.domain.saga;

import com.dreams.domain.model.Microservice;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * Weighted undirected graph G(V, E) representing microservice affinities.
 * Used by the SAGA framework for graph-based placement optimization.
 * Vertices are microservices, edges are affinity relationships.
 */
@Slf4j
@Getter
public class AffinityGraph {

    private final List<Microservice> vertices;
    private final Map<String, Map<String, Double>> edges; // adjacency: id -> id -> weight

    public AffinityGraph(List<Microservice> microservices) {
        this.vertices = new ArrayList<>(microservices);
        this.edges = new HashMap<>();
        buildEdges();
    }

    private void buildEdges() {
        for (Microservice ms : vertices) {
            if (ms.getAffinities() == null) continue;
            ms.getAffinities().forEach((neighbor, weight) -> {
                edges.computeIfAbsent(ms.getId(), k -> new HashMap<>()).put(neighbor.getId(), weight);
                edges.computeIfAbsent(neighbor.getId(), k -> new HashMap<>()).put(ms.getId(), weight);
            });
        }
    }

    public double getEdgeWeight(String u, String v) {
        return edges.getOrDefault(u, Collections.emptyMap()).getOrDefault(v, 0.0);
    }

    /**
     * Apply min-max normalization to all edge weights (Eq. 12.2).
     * w(u,v) = (a(u,v) - min) / (max - min)
     */
    public void normalizeWeights() {
        double min = Double.MAX_VALUE;
        double max = Double.MIN_VALUE;

        for (Map<String, Double> neighbors : edges.values()) {
            for (double w : neighbors.values()) {
                if (w < min) min = w;
                if (w > max) max = w;
            }
        }

        if (max == min) return; // all weights equal, normalization not possible

        double range = max - min;
        for (Map<String, Double> neighbors : edges.values()) {
            for (Map.Entry<String, Double> entry : neighbors.entrySet()) {
                entry.setValue((entry.getValue() - min) / range);
            }
        }
        log.debug("Normalized edge weights: min={}, max={}, range={}", min, max, range);
    }

    /**
     * Calculate total inter-domain affinity (the optimization objective).
     * minimize SUM w(u,v) for u in V_i, v in V_j, i != j
     */
    public double calculateInterDomainAffinity() {
        double total = 0.0;
        Set<String> counted = new HashSet<>();
        for (Microservice u : vertices) {
            if (u.getAffinities() == null) continue;
            for (Map.Entry<Microservice, Double> entry : u.getAffinities().entrySet()) {
                Microservice v = entry.getKey();
                String edgeKey = u.getId().compareTo(v.getId()) < 0 ?
                        u.getId() + "-" + v.getId() : v.getId() + "-" + u.getId();
                if (!counted.contains(edgeKey) &&
                        !u.getK8sCluster().getId().equals(v.getK8sCluster().getId())) {
                    total += entry.getValue();
                    counted.add(edgeKey);
                }
            }
        }
        return total;
    }

    public int size() {
        return vertices.size();
    }
}
