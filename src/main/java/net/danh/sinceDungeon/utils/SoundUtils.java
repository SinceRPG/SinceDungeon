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

        if (ServerVersion.isAtLeast(1, 21, 3)) {
            try {
                NamespacedKey key = NamespacedKey.fromString(soundName.toLowerCase(Locale.ROOT));
                if (key == null) key = NamespacedKey.minecraft(soundName.toLowerCase(Locale.ROOT));
                Sound sound = Registry.SOUND_EVENT.get(key);
                if (sound != null) return sound;
                return (Sound) Sound.class.getField(soundName.toUpperCase(Locale.ROOT)).get(null);
            } catch (Throwable ignored) {
            }
        } else {
            try {
                return Sound.valueOf(soundName.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException | IncompatibleClassChangeError e) {
                try {
                    String legacyName = soundName.replace(".", "_").toUpperCase(Locale.ROOT);
                    return Sound.valueOf(legacyName);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        return null;
    }
}