package com.dreams.domain.service;

public interface LatencyPenaltyCalculationService {

    double getLatencyPenalty(double localAffinityScore, double targetAffinityScore, long latencyToTargetCluster);
}
