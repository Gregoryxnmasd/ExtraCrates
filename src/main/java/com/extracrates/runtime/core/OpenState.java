package com.extracrates.runtime.core;

import java.time.Instant;

public class OpenState {
    private final boolean lockAcquired;
    private final boolean keyConsumed;
    private final boolean cooldownApplied;
    private final Instant previousCooldown;
    private boolean lockReleased;

    public OpenState(boolean lockAcquired, boolean keyConsumed, boolean cooldownApplied, Instant previousCooldown) {
        this.lockAcquired = lockAcquired;
        this.keyConsumed = keyConsumed;
        this.cooldownApplied = cooldownApplied;
        this.previousCooldown = previousCooldown;
    }

    public boolean isLockAcquired() {
        return lockAcquired;
    }

    public boolean isKeyConsumed() {
        return keyConsumed;
    }

    public boolean isCooldownApplied() {
        return cooldownApplied;
    }

    public Instant getPreviousCooldown() {
        return previousCooldown;
    }

    public boolean isLockReleased() {
        return lockReleased;
    }

    public void markLockReleased() {
        this.lockReleased = true;
    }
}
