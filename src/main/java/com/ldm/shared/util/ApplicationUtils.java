package com.ldm.shared.util;

import org.apache.ratis.protocol.ClientId;
import org.apache.ratis.protocol.RaftGroupId;
import org.apache.ratis.protocol.RaftPeerId;
import org.apache.ratis.protocol.TransferLeadershipRequest;
import org.eclipse.microprofile.config.ConfigProvider;

public class ApplicationUtils {

    public static boolean isProfileActive(String profile) {
        return ConfigProvider.getConfig().getOptionalValue("quarkus.profile", String.class)
                .map(profile::equals)
                .orElse(false);
    }

    public static TransferLeadershipRequest createTransferLeadershipRequest(
            RaftPeerId raftPeerId,
            RaftGroupId raftGroupId,
            RaftPeerId targetLeaderId
    ) {
        return new TransferLeadershipRequest(
                ClientId.randomId(),
                raftPeerId,
                raftGroupId,
                System.currentTimeMillis(),
                targetLeaderId,
                5000
        );
    }

//    public static GroupInfoReply getGroupInfo(
//            RaftServer raftServer,
//            RaftPeerId raftPeerId,
//            RaftGroupId raftGroupId
//    ) throws IOException {
//        GroupInfoRequest groupInfoRequest = new GroupInfoRequest(
//                ClientId.randomId(),
//                raftPeerId,
//                raftGroupId,
//                System.currentTimeMillis()
//        );
//        return raftServer.getGroupInfo(groupInfoRequest);
//    }

//    public static boolean isClusterMonitoringEnabled(){
//        return ConfigProvider.getConfig()
//                .getOptionalValue("ldm.enable-cluster-monitoring", Boolean.class)
//                .orElse(false);
//
//    }
}
