package com.extracrates.storage;

import java.time.Instant;
import java.util.UUID;

public record CrateOpenStartedEntry(
        UUID playerId,
        String crateId,
        String serverId,
        Instant openedAt
) {
}
