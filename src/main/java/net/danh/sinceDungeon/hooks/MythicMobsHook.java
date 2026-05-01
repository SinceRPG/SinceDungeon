package net.danh.sinceDungeon.hooks;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

import java.util.UUID;

/**
 * Centralized bridge for MythicMobs API.
 * Strictly isolates all io.lumine.mythic.* imports to prevent NoClassDefFoundError
 * when the MythicMobs plugin is not installed on the server.
 */
public class MythicMobsHook {

    /**
     * Checks if a given Bukkit entity is a MythicMob.
     */
    public static boolean isMythicMob(Entity entity) {
        try {
            return io.lumine.mythic.bukkit.MythicBukkit.inst().getMobManager().getMythicMobInstance(entity) != null;
        } catch (Exception | NoClassDefFoundError e) {
            return false;
        }
    }

    /**
     * Validates if a MythicMob internal name actually exists in the registry.
     */
    public static boolean isValidMythicMob(String internalName) {
        try {
            return io.lumine.mythic.bukkit.MythicBukkit.inst().getMobManager().getMythicMob(internalName).isPresent();
        } catch (Exception | NoClassDefFoundError e) {
            return false;
        }
    }

    /**
     * Spawns a MythicMob and returns its Bukkit Entity counterpart.
     * Returns null if the mob fails to spawn or if MythicMobs is missing.
     */
    public static Entity spawnMythicMob(Location loc, String mobId, int level) {
        try {
            io.lumine.mythic.api.mobs.MythicMob mythicMob = io.lumine.mythic.bukkit.MythicBukkit.inst().getMobManager().getMythicMob(mobId).orElse(null);
            if (mythicMob != null) {
                // Ensure chunk is loaded before spawning
                loc.getChunk().load(true);
                io.lumine.mythic.core.mobs.ActiveMob am = mythicMob.spawn(io.lumine.mythic.bukkit.BukkitAdapter.adapt(loc), level);
                if (am != null && am.getEntity() != null) {
                    return am.getEntity().getBukkitEntity();
                }
            }
        } catch (Exception | NoClassDefFoundError e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Retrieves the custom display name of an active MythicMob via its UUID.
     */
    public static String getActiveMobName(UUID uuid) {
        try {
            return io.lumine.mythic.bukkit.MythicBukkit.inst().getMobManager().getActiveMob(uuid)
                    .map(io.lumine.mythic.core.mobs.ActiveMob::getDisplayName).orElse(null);
        } catch (Exception | NoClassDefFoundError e) {
            return null;
        }
    }
}