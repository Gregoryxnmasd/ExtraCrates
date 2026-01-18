package com.extracrates.sync;

import java.time.Instant;
import java.util.UUID;

public record CrateHistoryEntry(
        SyncEventType type,
        UUID playerId,
        String crateId,
        String rewardId,
        Instant timestamp,
        String serverId
) {
}
