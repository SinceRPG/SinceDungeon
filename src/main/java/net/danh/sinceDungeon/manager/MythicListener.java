package net.danh.sinceDungeon.manager;

import io.lumine.mythic.bukkit.events.MythicMobDeathEvent;
import net.danh.sinceDungeon.SinceDungeon;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class MythicListener implements Listener {
    private final SinceDungeon plugin;

    public MythicListener(SinceDungeon plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onMMDeath(MythicMobDeathEvent e) {
        if (e.getKiller() instanceof Player p) {
            plugin.getDungeonManager().dispatchEvent(p, e);
        }
    }
}