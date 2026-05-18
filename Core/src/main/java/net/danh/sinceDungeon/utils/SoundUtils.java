package net.danh.sinceDungeon.utils;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;

import java.util.Locale;

public class SoundUtils {

    /**
     * Safely parses a sound string across multiple Minecraft versions.
     * Supports both modern namespaced keys and legacy enum names.
     */
    public static Sound getSound(String soundName) {
        if (soundName == null || soundName.trim().isEmpty()) return null;
        soundName = soundName.trim();
        if (soundName.startsWith("minecraft:")) {
            soundName = soundName.substring(10);
        }

        try {
            NamespacedKey key = NamespacedKey.fromString(soundName.toLowerCase(Locale.ROOT));
            if (key == null) key = NamespacedKey.minecraft(soundName.toLowerCase(Locale.ROOT));
            Sound sound = Registry.SOUND_EVENT.get(key);
            if (sound != null) return sound;
        } catch (Throwable ignored) {
        }

        Sound legacy = resolveLegacy(soundName.toUpperCase(Locale.ROOT));
        if (legacy != null) return legacy;
        return resolveLegacy(soundName.replace(".", "_").toUpperCase(Locale.ROOT));
    }

    private static Sound resolveLegacy(String enumName) {
        try {
            Object value = Sound.class.getMethod("valueOf", String.class).invoke(null, enumName);
            return value instanceof Sound sound ? sound : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
}
