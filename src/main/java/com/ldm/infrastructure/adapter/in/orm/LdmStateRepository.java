package com.ldm.infrastructure.adapter.in.orm;

import com.ldm.infrastructure.persistence.entity.LdmState;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class LdmStateRepository implements PanacheRepository<LdmState> {
}
