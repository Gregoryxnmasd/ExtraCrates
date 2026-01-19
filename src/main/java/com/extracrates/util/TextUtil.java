package com.extracrates.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
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

    public static Component colorNoItalic(String text) {
        return color(text).decoration(TextDecoration.ITALIC, false);
    }

    public static String colorString(String text) {
        if (text == null) {
            return "";
        }
        return SERIALIZER.serialize(SERIALIZER.deserialize(text));
    }

    public static String serializeLegacy(Component component) {
        if (component == null) {
            return "";
        }
        return SERIALIZER.serialize(component);
    }
}
