package com.ldm.domain.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "ldm_state")
@Data
@NoArgsConstructor
public class LdmState {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ldm_id")
    private String ldmId;

    @Column(name = "microservice_id")
    private String microserviceId;

    @Column(name = "k8s_cluster_id")
    private String k8sClusterId;

    @Column(name = "k8s_cluster_location")
    private String k8sClusterLocation;

    @Column(name = "improvement_score")
    private Double improvementScore;

    @Column(name = "microservice_affinities", columnDefinition = "TEXT")
    private String microserviceAffinities;

    @Column(name = "last_update")
    private LocalDateTime lastUpdate;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
