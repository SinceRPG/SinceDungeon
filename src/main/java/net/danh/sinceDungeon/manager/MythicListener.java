package net.danh.sinceDungeon.manager;

import io.lumine.mythic.bukkit.events.MythicMobDeathEvent;
import net.danh.sinceDungeon.SinceDungeon;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * Specifically listens to events originating from the MythicMobs plugin.
 */
public class MythicListener implements Listener {
    private final SinceDungeon plugin;

    public MythicListener(SinceDungeon plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onMMDeath(MythicMobDeathEvent e) {
        if (e.getEntity() != null && e.getEntity() != null) {
            org.bukkit.World w = e.getEntity().getWorld();
            for (DungeonGame game : plugin.getDungeonManager().getActiveGames().values()) {
                if (game.getWorld() != null && game.getWorld().equals(w)) {
                    game.onEvent(e);
                    break;
                }
            }
        }
    }
}