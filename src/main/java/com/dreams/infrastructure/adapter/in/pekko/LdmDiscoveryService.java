package com.dreams.infrastructure.adapter.in.pekko;

import lombok.extern.slf4j.Slf4j;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.cluster.ClusterEvent;
import org.apache.pekko.cluster.Member;
import org.apache.pekko.cluster.typed.Cluster;
import org.apache.ratis.client.RaftClient;
import org.apache.ratis.protocol.RaftPeer;
import org.apache.ratis.protocol.RaftPeerId;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Slf4j
public class ClusterMembershipSync {

    // Factory method to create the behavior
    public static Behavior<ClusterEvent.MemberEvent> create(String ldmId, RaftClient ratisClient, ActorSystem<?> system) {
        return Behaviors.setup(context -> {
            // Initialize Pekko cluster
            Cluster cluster = Cluster.get(system);

            // Convert Iterable<Member> to Set<Member> and perform initial synchronization with Ratis
            Set<Member> members = StreamSupport.stream(cluster.state().getMembers().spliterator(), false)
                    .collect(Collectors.toSet());
            syncExistingMembersWithRatis(ratisClient, members);

            // Define the functional behavior to handle membership events
            return Behaviors.receive(ClusterEvent.MemberEvent.class)
                    .onMessage(ClusterEvent.MemberUp.class, event -> onMemberUp(event.member(), ratisClient, cluster))
                    .onMessage(ClusterEvent.MemberRemoved.class, event -> onMemberRemoved(event.member(), ratisClient, cluster))
                    .build();
        });
    }

    // Synchronize all current cluster members with the Ratis cluster
    private static void syncExistingMembersWithRatis(RaftClient ratisClient, Set<Member> members) {
        log.info("++++++++++[syncExistingMembersWith]: UPDATING RATIS CONFIGURATION++++++++++");
        List<RaftPeer> peers = members.stream()
                .map(member -> {
                    log.info(">> [syncExistingMembersWith]: Apache Pekko Member Address: " + member.address().toString());
                    ParsedAddress parsedAddress = parseAddress(member.address().toString());

                    return RaftPeer.newBuilder()
                            .setId(RaftPeerId.valueOf(member.address().toString()))
                            .setAddress(parsedAddress.host+":"+parsedAddress.port)
                            .build();
                })
                .collect(Collectors.toList());
        setRatisConfiguration(ratisClient, peers);
    }

    // Add a member to the Ratis cluster by updating the configuration
    private static Behavior<ClusterEvent.MemberEvent> onMemberUp(Member member, RaftClient ratisClient, Cluster cluster) {
        updateRatisConfiguration(cluster, ratisClient);
        return Behaviors.same();
    }

    // Remove a member from the Ratis cluster by updating the configuration
    private static Behavior<ClusterEvent.MemberEvent> onMemberRemoved(Member member, RaftClient raftClient, Cluster cluster) {
        updateRatisConfiguration(cluster, raftClient);
        return Behaviors.same();
    }

    // Helper method to update Ratis configuration based on current Pekko cluster state
    private static void updateRatisConfiguration(Cluster cluster, RaftClient raftClient) {
        log.info("++++++++++[updateRatisConfiguration]: UPDATING RATIS CONFIGURATION++++++++++");
        List<RaftPeer> peers = StreamSupport.stream(cluster.state().getMembers().spliterator(), false)
                .map(member -> member.getRoles().stream()
                        .filter(role -> role.startsWith("ldmId-"))
                        .map(role -> role.substring("ldmId-".length()))
                        .findFirst()
                        .map(ldmId -> {
                            log.info(">> [updateRatisConfiguration]: Apache Pekko Member Address: " + member.address().toString());
                            ParsedAddress parsedAddress = parseAddress(member.address().toString());
                            return RaftPeer.newBuilder()
                                    .setId(RaftPeerId.valueOf(ldmId))
                                    .setAddress(parsedAddress.host+":"+parsedAddress.port)
                                    .build();
                        })
                        .orElse(null))
                .filter(Objects::nonNull)
                .toList();

        setRatisConfiguration(raftClient, peers);
    }

    public static ParsedAddress parseAddress(String address) {
        // Check if the address contains '@' and ':'
        int atIndex = address.lastIndexOf('@');
        int colonIndex = address.lastIndexOf(':');

        if (atIndex == -1 || colonIndex == -1 || atIndex > colonIndex) {
            throw new IllegalArgumentException("Invalid address format: " + address);
        }

        // Extract host and port
        String host = address.substring(atIndex + 1, colonIndex);
        String port = address.substring(colonIndex + 1);

        return new ParsedAddress(host, Integer.parseInt(port));
    }

    // Helper class to store parsed address
    static class ParsedAddress {
        String host;
        int port;

        ParsedAddress(String host, int port) {
            this.host = host;
            this.port = port;
        }
    }

    // Send the configuration change request to Ratis
    private static void setRatisConfiguration(RaftClient raftClient, List<RaftPeer> peers) {
        try {
            log.info(">---------> Setting the Ratis Configuration to the following peers: " + peers.toString());
//            raftClient.admin().setConfiguration(peers); // Updates the Ratis cluster configuration
        } catch (Exception e) {
            log.error("Failed to set Ratis configuration: " + e.getMessage());
        }
    }
}
