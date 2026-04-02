package com.dreams.infrastructure.adapter.in.orm;

import com.dreams.infrastructure.persistence.entity.LdmState;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class LdmStateRepository implements PanacheRepository<LdmState> {
}
