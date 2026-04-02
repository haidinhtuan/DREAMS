package com.dreams.domain.service.impl;

import com.dreams.domain.model.K8sCluster;
import com.dreams.domain.model.Microservice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DefaultAffinityCalculationServiceTest {

    private DefaultAffinityCalculationService service;
    private K8sCluster clusterA;
    private K8sCluster clusterB;

    @BeforeEach
    void setUp() {
        service = new DefaultAffinityCalculationService();
        clusterA = new K8sCluster("cluster-a", "Location A");
        clusterB = new K8sCluster("cluster-b", "Location B");
    }

    @Test
    void calculateDataAffinity_correctRatio() {
        Microservice ms2 = new Microservice("ms2", "svc2", false, clusterB, new HashMap<>(), new HashMap<>(), 0, 0);
        Microservice ms3 = new Microservice("ms3", "svc3", false, clusterB, new HashMap<>(), new HashMap<>(), 0, 0);

        Map<Microservice, Double> dataExchanged = new HashMap<>();
        dataExchanged.put(ms2, 60.0);
        dataExchanged.put(ms3, 40.0);

        Microservice ms1 = new Microservice("ms1", "svc1", false, clusterA, new HashMap<>(), dataExchanged, 0, 0);

        double affinity = service.calculateDataAffinity(ms1, ms2);
        assertEquals(0.6, affinity, 0.001);
    }

    @Test
    void calculateTotalAffinity_appliesWeight() {
        Microservice ms2 = new Microservice("ms2", "svc2", false, clusterB, new HashMap<>(), new HashMap<>(), 0, 0);

        Map<Microservice, Double> dataExchanged = new HashMap<>();
        dataExchanged.put(ms2, 80.0);

        Microservice ms1 = new Microservice("ms1", "svc1", false, clusterA, new HashMap<>(), dataExchanged, 0, 0);

        Map<String, Double> weights = Map.of("data", 0.5);
        double totalAffinity = service.calculateTotalAffinity(ms1, ms2, weights);
        assertEquals(0.5, totalAffinity, 0.001); // 1.0 * 0.5
    }

    @Test
    void calculateTotalAffinityImpact_sumsConnectedAffinities() {
        Microservice migrating = new Microservice("ms-migrate", "migrate", false, clusterA, new HashMap<>(), new HashMap<>(), 0, 0);

        Map<Microservice, Double> affinities1 = new HashMap<>();
        affinities1.put(migrating, 50.0);
        Microservice local1 = new Microservice("local1", "local1", false, clusterB, affinities1, new HashMap<>(), 0, 0);

        Map<Microservice, Double> affinities2 = new HashMap<>();
        affinities2.put(migrating, 30.0);
        Microservice local2 = new Microservice("local2", "local2", false, clusterB, affinities2, new HashMap<>(), 0, 0);

        Microservice local3 = new Microservice("local3", "local3", false, clusterB, new HashMap<>(), new HashMap<>(), 0, 0);

        double impact = service.calculateTotalAffinityImpact(migrating, List.of(local1, local2, local3));
        assertEquals(80.0, impact);
    }

    @Test
    void calculateTotalAffinityImpact_zeroWhenNoConnections() {
        Microservice migrating = new Microservice("ms-migrate", "migrate", false, clusterA, new HashMap<>(), new HashMap<>(), 0, 0);
        Microservice local1 = new Microservice("local1", "local1", false, clusterB, new HashMap<>(), new HashMap<>(), 0, 0);

        double impact = service.calculateTotalAffinityImpact(migrating, List.of(local1));
        assertEquals(0.0, impact);
    }
}
