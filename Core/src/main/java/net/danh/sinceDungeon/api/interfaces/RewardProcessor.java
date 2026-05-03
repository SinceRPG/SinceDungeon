package net.danh.sinceDungeon.api.interfaces;

import org.bukkit.entity.Player;

/**
 * Functional interface for custom reward processors.
 */
@FunctionalInterface
public interface RewardProcessor {
    /**
     * Processes and grants a custom reward to a player.
     *
     * @param player      The player receiving the reward.
     * @param value       The raw value string from the config.
     * @param displayName The display name of the reward (nullable).
     */
    void giveReward(Player player, String value, String displayName);
}