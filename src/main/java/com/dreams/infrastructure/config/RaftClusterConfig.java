package com.dreams.infrastructure.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import jakarta.validation.constraints.Size;

import java.util.List;

@ConfigMapping(prefix = "raft")
public interface RaftClusterConfig {

    Server server();

    Cluster cluster();

    interface Cluster {
        @WithDefault("1")
        @Size(min = 1)
        String id();

        List<Peer> peers();
    }

    interface Server {
        @WithDefault("1")
        @Size(min = 1)
        String id();

        @WithDefault("localhost")
        String host();

        @WithDefault("8080")
        int port();

        @WithDefault("raft-storage")
        Storage storage();
    }


    interface Peer {
        String id();
        String host();
        int port();
    }

    interface Storage {
        String dir();
    }

}
