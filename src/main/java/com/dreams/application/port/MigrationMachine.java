package com.dreams.application.port;

import com.dreams.infrastructure.serialization.protobuf.MigrationActionOuterClass;
import org.apache.ratis.server.RaftServer;

import java.util.concurrent.CompletableFuture;

public interface MigrationMachine<T> {

    CompletableFuture handleMigrationAction(RaftServer raftServer, MigrationActionOuterClass.MigrationAction protoMigrationAction);

    T getLDMStateMachine();
}
