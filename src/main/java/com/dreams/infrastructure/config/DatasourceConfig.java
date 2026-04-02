package com.dreams.infrastructure.config;

import io.smallrye.config.ConfigMapping;

@ConfigMapping(prefix = "quarkus.datasource")
public interface DatasourceConfig {

    String username();

    String password();

    Jdbc jdbc();

    interface Jdbc {
        String url();
    }
}
