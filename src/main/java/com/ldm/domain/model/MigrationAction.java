package com.ldm.domain.model;

import java.time.LocalDateTime;


public record MigrationAction(Microservice microservice, double improvementScore, K8sCluster targetK8sCluster,
                              LocalDateTime suggestedAt, String suggesterId, LocalDateTime createdAt) {
}
