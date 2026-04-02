package com.ldm.domain.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MicroserviceTest {

    private K8sCluster clusterNY;
    private K8sCluster clusterBerlin;
    private K8sCluster clusterSingapore;
    private Microservice ms1, ms2, ms3, ms4;

    @BeforeEach
    void setUp() {
        clusterNY = new K8sCluster("cluster-ny", "New York");
        clusterBerlin = new K8sCluster("cluster-berlin", "Berlin");
        clusterSingapore = new K8sCluster("cluster-sg", "Singapore");

        ms2 = new Microservice("ms2", "service-2", false, clusterNY, new HashMap<>(), new HashMap<>(), 0.0, 0.0);
        ms3 = new Microservice("ms3", "service-3", false, clusterBerlin, new HashMap<>(), new HashMap<>(), 0.0, 0.0);
        ms4 = new Microservice("ms4", "service-4", false, clusterSingapore, new HashMap<>(), new HashMap<>(), 0.0, 0.0);

        Map<Microservice, Double> affinities = new HashMap<>();
        affinities.put(ms2, 90.0);  // same cluster
        affinities.put(ms3, 50.0);  // different cluster
        affinities.put(ms4, 30.0);  // different cluster

        Map<Microservice, Double> dataExchanged = new HashMap<>();
        dataExchanged.put(ms2, 100.0);
        dataExchanged.put(ms3, 50.0);

        ms1 = new Microservice("ms1", "service-1", false, clusterNY, affinities, dataExchanged, 25.0, 512.0);
    }

    @Test
    void getTotalAffinityPerCluster_groupsCorrectly() {
        Map<K8sCluster, Double> result = ms1.getTotalAffinityPerCluster();
        assertEquals(90.0, result.get(clusterNY));
        assertEquals(50.0, result.get(clusterBerlin));
        assertEquals(30.0, result.get(clusterSingapore));
    }

    @Test
    void getTotalLocalAffinity_sumsSameCluster() {
        double localAffinity = ms1.getTotalLocalAffinity();
        assertEquals(90.0, localAffinity);
    }

    @Test
    void getK8sClusterWithHighestAffinity_returnsCorrectCluster() {
        Map.Entry<K8sCluster, Double> highest = ms1.getK8sClusterWithHighestAffinity();
        assertNotNull(highest);
        assertEquals(clusterNY, highest.getKey());
        assertEquals(90.0, highest.getValue());
    }

    @Test
    void equals_basedOnId() {
        Microservice duplicate = new Microservice("ms1", "different-name", true, clusterBerlin, null, null, 0.0, 0.0);
        assertEquals(ms1, duplicate);
        assertEquals(ms1.hashCode(), duplicate.hashCode());
    }

    @Test
    void equals_differentId() {
        assertNotEquals(ms1, ms2);
    }

    @Test
    void nonMigratable_flag() {
        Microservice nonMigratable = new Microservice("ms5", "service-5", true, clusterNY, new HashMap<>(), new HashMap<>(), 0.0, 0.0);
        assertTrue(nonMigratable.isNonMigratable());
        assertFalse(ms1.isNonMigratable());
    }

    @Test
    void getDataExchangedWith_returnsCorrectValue() {
        assertEquals(100.0, ms1.getDataExchangedWith(ms2));
        assertEquals(50.0, ms1.getDataExchangedWith(ms3));
        assertEquals(0.0, ms1.getDataExchangedWith(ms4)); // no data exchanged
    }

    @Test
    void getTotalDataExchanged_sumsAll() {
        assertEquals(150.0, ms1.getTotalDataExchanged());
    }
}
