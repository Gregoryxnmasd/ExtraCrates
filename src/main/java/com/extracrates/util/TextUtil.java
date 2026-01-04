package com.extracrates.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public final class TextUtil {
    private static final LegacyComponentSerializer SERIALIZER = LegacyComponentSerializer.legacyAmpersand();

    private TextUtil() {
    }

    public static Component color(String text) {
        if (text == null) {
            return Component.empty();
        }
        return SERIALIZER.deserialize(text);
    }

    public static String colorString(String text) {
        if (text == null) {
            return "";
        }
        return SERIALIZER.deserialize(text).content();
    }
}
