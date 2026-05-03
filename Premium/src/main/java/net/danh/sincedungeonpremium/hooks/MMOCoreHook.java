package net.danh.sincedungeonpremium.hooks;

import net.Indyuce.mmocore.api.player.PlayerData;
import org.bukkit.entity.Player;

/**
 * Premium-Exclusive Hook: MMOCore Integration
 * Responsibilities:
 * - Safely isolates MMOCore API calls from the main plugin class.
 * - Prevents JVM NoClassDefFoundError crashes if MMOCore is not installed.
 */
public class MMOCoreHook {

    /**
     * Safely retrieves the player's class ID from MMOCore.
     *
     * @param player The target player.
     * @return The MMOCore Class ID, or null if an error occurs.
     */
    public static String getPlayerClass(Player player) {
        try {
            return PlayerData.get(player).getProfess().getId();
        } catch (Exception | NoClassDefFoundError e) {
            return null;
        }
    }
}