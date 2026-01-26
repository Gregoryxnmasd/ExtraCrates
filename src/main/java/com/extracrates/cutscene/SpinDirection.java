package com.extracrates.cutscene;

import java.util.Locale;

public enum SpinDirection {
    LEFT(-1.0),
    RIGHT(1.0);

    private final double multiplier;

    SpinDirection(double multiplier) {
        this.multiplier = multiplier;
    }

    public double multiplier() {
        return multiplier;
    }

    public static SpinDirection fromString(String value) {
        if (value == null) {
            return RIGHT;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.equals("left") || normalized.equals("izquierda") || normalized.equals("l")) {
            return LEFT;
        }
        return RIGHT;
    }
}
