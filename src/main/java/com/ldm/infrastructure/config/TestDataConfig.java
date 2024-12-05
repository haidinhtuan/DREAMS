package com.ldm.infrastructure.config;

import io.smallrye.config.ConfigMapping;

@ConfigMapping(prefix = "testdata")
public interface TestDataConfig {

    String file();
    
}
