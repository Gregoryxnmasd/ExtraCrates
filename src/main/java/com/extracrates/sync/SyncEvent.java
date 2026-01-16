package com.extracrates.sync;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record SyncEvent(
        SyncEventType type,
        String serverId,
        UUID playerId,
        String crateId,
        String rewardId,
        Instant timestamp
) {
    public SyncEvent {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(serverId, "serverId");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(crateId, "crateId");
        Objects.requireNonNull(timestamp, "timestamp");
    }
}
