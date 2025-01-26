package com.ldm.infrastructure.persistence.entity;

import com.ldm.infrastructure.persistence.converter.JsonConverter;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "ldm_state")
@Data
@NoArgsConstructor
@IdClass(LdmStateId.class)
public class LdmState {

    @Id
    @Column(name = "ldm_id")
    private String ldmId;

    @Id
    @Column(name = "microservice_id")
    private String microserviceId;

    @Column(name = "k8s_cluster_id")
    private String k8sClusterId;

    @Column(name = "k8s_cluster_location")
    private String k8sClusterLocation;

    @Column(name = "improvement_score")
    private Double improvementScore;

    @Column(name = "microservice_affinities", columnDefinition = "json")
    @Convert(converter = JsonConverter.class)
    private Map<String, Double> microserviceAffinities;

    @Column(name = "last_update")
    private LocalDateTime lastUpdate;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
