package com.dreams.domain.service.impl;

import com.dreams.infrastructure.config.LdmConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AffinityPenaltySigmoidWeightCalculationServiceTest {

    private AffinityPenaltySigmoidWeightCalculationService service;

    @BeforeEach
    void setUp() {
        LdmConfig ldmConfig = mock(LdmConfig.class);
        LdmConfig.Voting voting = mock(LdmConfig.Voting.class);
        when(ldmConfig.voting()).thenReturn(voting);
        when(voting.scalingFactor()).thenReturn(10.0);
        service = new AffinityPenaltySigmoidWeightCalculationService(ldmConfig);
    }

    @Test
    void weight_returnsValueBetweenZeroAndOne() {
        double weight = service.calculateAffinityPenaltyWeight(0.5);
        assertTrue(weight > 0 && weight < 1);
    }

    @Test
    void weight_highImpactReturnsHighWeight() {
        double highWeight = service.calculateAffinityPenaltyWeight(1.0);
        double lowWeight = service.calculateAffinityPenaltyWeight(0.1);
        assertTrue(highWeight > lowWeight);
    }

    @Test
    void weight_zeroImpactReturnsHalf() {
        double weight = service.calculateAffinityPenaltyWeight(0.0);
        assertEquals(0.5, weight, 0.001);
    }

    @Test
    void weight_negativeImpactReturnsBelowHalf() {
        double weight = service.calculateAffinityPenaltyWeight(-0.5);
        assertTrue(weight < 0.5);
    }
}
