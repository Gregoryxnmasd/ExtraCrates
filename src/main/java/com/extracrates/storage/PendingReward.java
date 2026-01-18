package com.extracrates.storage;

import java.time.Instant;

public record PendingReward(String crateId, String rewardId, Instant createdAt) {
}
