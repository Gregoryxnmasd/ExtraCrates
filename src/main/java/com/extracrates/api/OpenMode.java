package com.extracrates.api;

import java.util.Locale;

public enum OpenMode {
    REWARD_ONLY,
    PREVIEW;

    public static OpenMode fromString(String value) {
        if (value == null) {
            return REWARD_ONLY;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        for (OpenMode mode : values()) {
            if (mode.name().toLowerCase(Locale.ROOT).equals(normalized)) {
                return mode;
            }
        }
        return REWARD_ONLY;
    }
}
