package com.extracrates.storage;

import java.time.Instant;
import java.util.UUID;

public record OpenHistoryEntry(
        UUID playerId,
        String crateId,
        String rewardId,
        String serverId,
        Instant openedAt
) {
}
