package com.extracrates.storage;

import java.time.Instant;

public record OpenHistoryFilter(String crateId, Instant from, Instant to) {
    public static OpenHistoryFilter none() {
        return new OpenHistoryFilter(null, null, null);
    }

    public boolean matches(CrateOpenEntry entry) {
        if (entry == null) {
            return false;
        }
        if (crateId != null && !crateId.isEmpty() && !crateId.equalsIgnoreCase(entry.crateId())) {
            return false;
        }
        if (from != null && entry.openedAt().isBefore(from)) {
            return false;
        }
        if (to != null && entry.openedAt().isAfter(to)) {
            return false;
        }
        return true;
    }
}
