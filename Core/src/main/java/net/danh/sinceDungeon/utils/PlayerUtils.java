package net.danh.sinceDungeon.utils;

import org.bukkit.entity.Player;

import java.lang.reflect.InvocationTargetException;

public final class PlayerUtils {

    private PlayerUtils() {
    }

    public static void respawn(Player player) {
        if (player == null || !player.isDead()) return;
        try {
            Object spigot = Player.class.getMethod("spigot").invoke(player);
            spigot.getClass().getMethod("respawn").invoke(spigot);
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException ignored) {
        }
    }
}
