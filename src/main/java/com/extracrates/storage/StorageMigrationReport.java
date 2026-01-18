package com.extracrates.storage;

public record StorageMigrationReport(
        boolean success,
        int sourceCooldowns,
        int sourceKeys,
        int sourceHistory,
        int targetCooldowns,
        int targetKeys,
        int targetHistory,
        String message
) {
    public static StorageMigrationReport failure(String message) {
        return new StorageMigrationReport(false, 0, 0, 0, 0, 0, 0, message);
    }
}
