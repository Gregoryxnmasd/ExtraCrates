package com.extracrates.storage;

import java.util.Locale;
import java.util.Optional;

public enum StorageTarget {
    LOCAL,
    SQL;

    public static Optional<StorageTarget> fromString(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "local" -> Optional.of(LOCAL);
            case "sql", "mysql", "mariadb", "sqlite", "postgres", "postgresql" -> Optional.of(SQL);
            default -> Optional.empty();
        };
    }
}
