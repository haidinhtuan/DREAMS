package com.ldm.domain.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.Objects;

@Getter
@Setter
@AllArgsConstructor
@ToString
public class K8sCluster {

    @Getter
    private String id;

    private String location; // Geographical location (e.g., "Europe", "U.S.", Asia, etc.)

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        K8sCluster that = (K8sCluster) o;
        return Objects.equals(this.id, that.getId());
    }

    // Override hashCode method to return hash based on serviceId
    @Override
    public int hashCode() {
        return Objects.hash(this.id);
    }
}
