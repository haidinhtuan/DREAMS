package com.dreams.application.port;

import com.dreams.infrastructure.config.ActorSystemManager;
import io.smallrye.mutiny.Uni;

public interface LeaderChangeHandler<GM, E, K, GI> {

    void handleLeaderChangedEvent(GM groupMemberId, E newLeaderId);

    Uni<Void> triggerLeaderChange(K raftServer, E peerId, GI groupId);

    ActorSystemManager getActorSystemsManager();
}
