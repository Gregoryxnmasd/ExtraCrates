package com.extracrates.storage;

import java.util.Locale;

public enum RewardDeliveryStatus {
    PENDING,
    DELIVERED;

    public static RewardDeliveryStatus fromString(String value) {
        if (value == null) {
            return PENDING;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        for (RewardDeliveryStatus status : values()) {
            if (status.name().equals(normalized)) {
                return status;
            }
        }
        return PENDING;
    }

    public String toStorageValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}
