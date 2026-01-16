package com.extracrates.sync;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class SyncEvent {
    private final SyncEventType type;
    private final String serverId;
    private final UUID playerId;
    private final String crateId;
    private final String rewardId;
    private final Instant timestamp;

    public SyncEvent(
            SyncEventType type,
            String serverId,
            UUID playerId,
            String crateId,
            String rewardId,
            Instant timestamp
    ) {
        this.type = Objects.requireNonNull(type, "type");
        this.serverId = Objects.requireNonNull(serverId, "serverId");
        this.playerId = Objects.requireNonNull(playerId, "playerId");
        this.crateId = Objects.requireNonNull(crateId, "crateId");
        this.rewardId = rewardId;
        this.timestamp = Objects.requireNonNull(timestamp, "timestamp");
    }

    public SyncEventType getType() {
        return type;
    }

    public String getServerId() {
        return serverId;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public String getCrateId() {
        return crateId;
    }

    public String getRewardId() {
        return rewardId;
    }

    public Instant getTimestamp() {
        return timestamp;
    }
}
