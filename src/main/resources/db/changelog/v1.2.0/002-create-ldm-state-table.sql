--liquibase formatted sql

-- changeset tien:003-create-ldm-state-table
CREATE TABLE IF NOT EXISTS ldm_state (
                                         ldm_id VARCHAR(255) NOT NULL,
                                         microservice_id VARCHAR(255) NOT NULL,
                                         k8s_cluster_id VARCHAR(255) NOT NULL,
                                         k8s_cluster_location VARCHAR(255) NOT NULL,
                                         improvement_score DOUBLE PRECISION NOT NULL,
                                         microservice_affinities JSON NOT NULL,
                                         last_update TIMESTAMP NOT NULL,
                                         created_at TIMESTAMP NOT NULL,
                                         PRIMARY KEY (ldm_id, microservice_id)
);

-- changeset tien:003-add-index-k8s-cluster-id
CREATE INDEX IF NOT EXISTS idx_ldm_state_k8s_cluster_id
    ON ldm_state (k8s_cluster_id);

-- changeset tien:003-add-index-last-update
CREATE INDEX IF NOT EXISTS idx_ldm_state_last_update
    ON ldm_state (last_update);
