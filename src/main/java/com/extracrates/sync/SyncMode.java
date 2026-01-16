package com.extracrates.sync;

public enum SyncMode {
    EVENTUAL,
    STRONG;

    public static SyncMode fromText(String value) {
        if (value == null) {
            return EVENTUAL;
        }
        return switch (value.toLowerCase()) {
            case "strong" -> STRONG;
            default -> EVENTUAL;
        };
    }
}
