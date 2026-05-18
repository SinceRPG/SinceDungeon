package net.danh.sinceDungeon.utils;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;

import java.lang.reflect.InvocationTargetException;
import java.util.Locale;

public final class AttributeUtils {

    private AttributeUtils() {
    }

    public static Attribute resolve(String rawName) {
        if (rawName == null || rawName.isBlank()) return null;

        String attrName = rawName.trim().toLowerCase(Locale.ROOT)
                .replace("minecraft:", "")
                .replace("generic.", "");

        Attribute modern = resolveModern(attrName);
        if (modern != null) return modern;

        Attribute legacy = resolveLegacy(attrName.toUpperCase(Locale.ROOT));
        if (legacy != null) return legacy;
        return resolveLegacy("GENERIC_" + attrName.toUpperCase(Locale.ROOT));
    }

    private static Attribute resolveModern(String attrName) {
        try {
            return Registry.ATTRIBUTE.get(NamespacedKey.minecraft(attrName));
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Attribute resolveLegacy(String enumName) {
        try {
            Object value = Attribute.class.getMethod("valueOf", String.class).invoke(null, enumName);
            return value instanceof Attribute attribute ? attribute : null;
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException ignored) {
            return null;
        }
    }
}
