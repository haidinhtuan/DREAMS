package com.ldm.infrastructure.persistence.entity;

import java.io.Serializable;
import java.util.Objects;

public class LdmStateId implements Serializable {
    private String ldmId;
    private String microserviceId;

    public LdmStateId() {}

    public LdmStateId(String ldmId, String microserviceId) {
        this.ldmId = ldmId;
        this.microserviceId = microserviceId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LdmStateId that = (LdmStateId) o;
        return Objects.equals(ldmId, that.ldmId) &&
                Objects.equals(microserviceId, that.microserviceId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ldmId, microserviceId);
    }
}
