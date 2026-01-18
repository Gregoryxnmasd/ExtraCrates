package com.extracrates.storage;

import java.time.Instant;
import java.util.UUID;

public record CrateOpenEntry(
        UUID playerId,
        String crateId,
        String rewardId,
        String serverId,
        Instant openedAt
) {
}
