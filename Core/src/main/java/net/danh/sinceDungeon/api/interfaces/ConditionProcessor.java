package net.danh.sinceDungeon.api.interfaces;

import org.bukkit.entity.Player;

/**
 * Functional interface for custom condition processors.
 */
@FunctionalInterface
public interface ConditionProcessor {
    /**
     * Checks if the condition is met for the player.
     *
     * @param player The player to check.
     * @param value  The condition value string from the config.
     * @return True if the condition is met, false otherwise.
     */
    boolean check(Player player, String value);
}