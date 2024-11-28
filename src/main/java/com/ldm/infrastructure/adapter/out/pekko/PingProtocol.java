package com.ldm.infrastructure.adapter.out.pekko;

import org.apache.pekko.actor.typed.ActorRef;

public interface PingProtocol {

    class Ping {
        private final ActorRef<Pong> replyTo;

        public Ping(ActorRef<Pong> replyTo) {
            this.replyTo = replyTo;
        }

        public ActorRef<Pong> getReplyTo() {
            return replyTo;
        }
    }

    class Pong {
        private final String ldmId;

        public Pong(String ldmId) {
            this.ldmId = ldmId;
        }

        public String getLdmId() {
            return ldmId;
        }
    }
}
