package com.dreams.domain.service;

import com.dreams.domain.model.Microservice;
import com.dreams.domain.model.MigrationCandidate;

import java.util.List;

public interface QoSOptimizationService {

    MigrationCandidate findBestMigrationCandidate();

    List<MigrationCandidate> findAllMigrationCandidates();

    boolean shouldApproveMigrationProposal(Microservice migratingMicroservice, String targetLdmId);
}
