package com.dreams.domain.model;

import java.time.LocalDateTime;


public record MigrationCandidate(Microservice microservice, double improvementScore, K8sCluster targetK8sCluster,
                                 LocalDateTime suggestedAt, String suggesterId) {
    public MigrationAction toMigrationAction() {
        return new MigrationAction(this.microservice, this.improvementScore, this.targetK8sCluster, this.suggestedAt, this.suggesterId, LocalDateTime.now());
    }
}
