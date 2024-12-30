package com.ldm.domain.model;

public class AffinityDetails {
    private final double affinityScore;
    private final String k8sClusterId;
    private final String k8sClusterLocation;

    public AffinityDetails(double affinityScore, String k8sClusterId, String k8sClusterLocation) {
        this.affinityScore = affinityScore;
        this.k8sClusterId = k8sClusterId;
        this.k8sClusterLocation = k8sClusterLocation;
    }

    public double getAffinityScore() {
        return affinityScore;
    }

    public String getK8sClusterId() {
        return k8sClusterId;
    }

    public String getK8sClusterLocation() {
        return k8sClusterLocation;
    }

    @Override
    public String toString() {
        return "AffinityDetails{" +
                "affinityScore=" + affinityScore +
                ", k8sClusterId='" + k8sClusterId + '\'' +
                ", k8sClusterLocation='" + k8sClusterLocation + '\'' +
                '}';
    }
}

