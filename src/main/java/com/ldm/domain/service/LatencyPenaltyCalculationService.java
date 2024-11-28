package com.ldm.domain.service;

public interface LatencyPenaltyCalculationService {

    double getLatencyPenalty(double localAffinityScore, double targetAffinityScore, long latencyToTargetCluster);
}
