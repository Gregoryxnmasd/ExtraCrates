package com.extracrates.storage;

public record PendingReward(String crateId, String rewardId, PendingRewardStatus status) {
    public boolean isPending() {
        return status == PendingRewardStatus.PENDING;
    }
}
