package com.dreams.domain.service.impl;

import com.dreams.infrastructure.config.LdmConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LatencyPenaltySigmoidCalculationServiceTest {

    private LatencyPenaltySigmoidCalculationService service;

    @BeforeEach
    void setUp() {
        LdmConfig ldmConfig = mock(LdmConfig.class);
        LdmConfig.Proposal proposal = mock(LdmConfig.Proposal.class);
        when(ldmConfig.proposal()).thenReturn(proposal);
        when(proposal.scalingFactor()).thenReturn(20.0);
        service = new LatencyPenaltySigmoidCalculationService(ldmConfig);
    }

    @Test
    void penalty_reducedWhenTargetAffinityHigher() {
        double penalty = service.getLatencyPenalty(30.0, 90.0, 100L);
        // Sigmoid reduces penalty when affinity difference is large
        assertTrue(penalty < 100, "Penalty should be less than raw latency");
        assertTrue(penalty > 0, "Penalty should be positive");
    }

    @Test
    void penalty_equalsLatencyWhenTargetNotHigher() {
        double penalty = service.getLatencyPenalty(90.0, 30.0, 100L);
        assertEquals(100.0, penalty);
    }

    @Test
    void penalty_equalsLatencyWhenAffinitiesEqual() {
        double penalty = service.getLatencyPenalty(50.0, 50.0, 100L);
        assertEquals(100.0, penalty);
    }

    @Test
    void penalty_zeroLatencyReturnsZero() {
        double penalty = service.getLatencyPenalty(30.0, 90.0, 0L);
        assertEquals(0.0, penalty);
    }
}
