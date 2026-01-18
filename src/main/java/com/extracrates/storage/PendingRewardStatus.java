package com.extracrates.storage;

import java.util.Locale;

public enum PendingRewardStatus {
    PENDING,
    DELIVERED;

    public static PendingRewardStatus fromString(String value) {
        if (value == null) {
            return PENDING;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if ("delivered".equals(normalized)) {
            return DELIVERED;
        }
        return PENDING;
    }
}
