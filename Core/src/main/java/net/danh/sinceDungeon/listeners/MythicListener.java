package net.danh.sinceDungeon.listeners;

import io.lumine.mythic.bukkit.events.MythicMobDeathEvent;
import io.lumine.mythic.bukkit.events.MythicMobSpawnEvent;
import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.hooks.MythicMobsHook;
import net.danh.sinceDungeon.models.DungeonGame;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.UUID;

/**
 * Specifically listens to events originating from the MythicMobs plugin.
 * Handles death events and tracks newly summoned child/phased mobs for mechanics.
 */
public class MythicListener implements Listener {
    private final SinceDungeon plugin;

    public MythicListener(SinceDungeon plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onMMDeath(MythicMobDeathEvent e) {
        if (e.getEntity() != null) {
            World w = e.getEntity().getWorld();
            for (DungeonGame game : plugin.getDungeonManager().getActiveGames().values()) {
                if (game.getWorld() != null && game.getWorld().equals(w)) {
                    game.onEvent(e);
                    break;
                }
            }
        }
    }

    /**
     * Intercepts MythicMobs spawns inside Dungeon instances.
     * Links child mobs and multi-phase transformations to their respective stages.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onMMSpawn(MythicMobSpawnEvent e) {
        if (e.getEntity() != null) {
            if (!(e.getEntity() instanceof LivingEntity) || e.getEntity() instanceof ArmorStand || e.getEntity() instanceof Player) {
                return;
            }

            World w = e.getEntity().getWorld();
            for (DungeonGame game : plugin.getDungeonManager().getActiveGames().values()) {
                if (game.getWorld() != null && game.getWorld().equals(w)) {
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        // Check for direct parent/child summoning skills
                        UUID parentId = MythicMobsHook.getParentUUID(e.getEntity().getUniqueId());
                        if (parentId != null) {
                            game.trackChildEntity(parentId, e.getEntity().getUniqueId(), e.getEntity().getLocation(), e.getMobType().getInternalName());
                        }
                        // Check for phase transitions and target validations
                        game.checkAndTrackMythicMob(e.getEntity().getUniqueId(), e.getEntity().getLocation(), e.getMobType().getInternalName());
                    }, 1L);
                    break;
                }
            }
        }
    }
}