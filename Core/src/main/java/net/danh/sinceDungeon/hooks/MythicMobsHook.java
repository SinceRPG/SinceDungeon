package net.danh.sinceDungeon.hooks;

import io.lumine.mythic.api.mobs.MythicMob;
import io.lumine.mythic.bukkit.BukkitAdapter;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.mobs.ActiveMob;
import org.bukkit.Location;
import org.bukkit.entity.Entity;

import java.util.UUID;

/**
 * Centralized bridge for MythicMobs API.
 * Strictly isolates all imports to prevent NoClassDefFoundError
 * when the MythicMobs plugin is not installed on the server.
 */
public class MythicMobsHook {

    /**
     * Checks if a given Bukkit entity is a MythicMob.
     */
    public static boolean isMythicMob(Entity entity) {
        try {
            return MythicBukkit.inst().getMobManager().getMythicMobInstance(entity) != null;
        } catch (Exception | NoClassDefFoundError e) {
            return false;
        }
    }

    /**
     * Validates if a MythicMob internal name actually exists in the registry.
     */
    public static boolean isValidMythicMob(String internalName) {
        try {
            return MythicBukkit.inst().getMobManager().getMythicMob(internalName).isPresent();
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
            MythicMob mythicMob = MythicBukkit.inst().getMobManager().getMythicMob(mobId).orElse(null);
            if (mythicMob != null) {
                loc.getChunk().load(true);
                ActiveMob am = mythicMob.spawn(BukkitAdapter.adapt(loc), level);
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
            return MythicBukkit.inst().getMobManager().getActiveMob(uuid)
                    .map(ActiveMob::getDisplayName).orElse(null);
        } catch (Exception | NoClassDefFoundError e) {
            return null;
        }
    }
}