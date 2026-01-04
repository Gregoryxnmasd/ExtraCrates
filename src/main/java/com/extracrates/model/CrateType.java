package com.extracrates.model;

public enum CrateType {
    NORMAL,
    KEYED,
    EVENT,
    SEASONAL,
    MYSTERY;

    public static CrateType fromString(String value) {
        if (value == null) {
            return NORMAL;
        }
        try {
            return CrateType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return NORMAL;
        }
    }
}
