package net.danh.sinceDungeon.listeners;

import io.lumine.mythic.bukkit.events.MythicMobDeathEvent;
import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.models.DungeonGame;
import org.bukkit.World;
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

    /**
     * Handles the death event of a MythicMob.
     * Delegates the event to the active dungeon instance to progress wave actions.
     *
     * @param e The MythicMob death event.
     */
    @EventHandler
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
}