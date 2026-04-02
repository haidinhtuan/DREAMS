package com.dreams.modules;

import com.dreams.infrastructure.config.LdmConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

/**
 * Configuration Control Module (CCM) — Configuration repository, dynamic updates, and validation.
 * Centralizes access to all LDM configuration parameters.
 */
@ApplicationScoped
@Slf4j
public class ConfigurationControlModule {

    @Inject
    LdmConfig ldmConfig;

    public String getLdmId() {
        return ldmConfig.id();
    }

    public double getProposalThreshold() {
        return ldmConfig.proposal().threshold();
    }

    public double getVotingThreshold() {
        return ldmConfig.voting().threshold();
    }

    public int getProposalInterval() {
        return ldmConfig.proposal().interval();
    }

    public LdmConfig getFullConfig() {
        return ldmConfig;
    }
}
