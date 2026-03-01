package net.danh.sinceDungeon.manager;

import io.lumine.mythic.bukkit.events.MythicMobDeathEvent;
import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class DungeonListener implements Listener {
    private final SinceDungeon plugin;

    public DungeonListener(SinceDungeon plugin) {
        this.plugin = plugin;
    }

    private void pass(Player p, org.bukkit.event.Event e) {
        plugin.getDungeonManager().dispatchEvent(p, e);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        pass(e.getPlayer(), e);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        pass(e.getPlayer(), e);
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        pass(e.getPlayer(), e);
    }

    @EventHandler
    public void onCloseInv(InventoryCloseEvent e) {
        if (e.getPlayer() instanceof Player p) pass(p, e);
    }

    @EventHandler
    public void onKill(EntityDeathEvent e) {
        if (e.getEntity().getKiller() != null) pass(e.getEntity().getKiller(), e);
    }

    @EventHandler
    public void onMMDeath(MythicMobDeathEvent e) {
        if (e.getKiller() instanceof Player p) pass(p, e);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        plugin.getDungeonManager().quitDungeon(e.getPlayer());
    }


    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        DungeonGame game = plugin.getDungeonManager().getGame(p.getUniqueId());
        if (game != null && !p.getWorld().equals(game.getWorld())) {
            plugin.getLogger().info(p.getName() + " left dungeon world via teleport. Stopping game.");
            game.stop(false);
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        DungeonGame game = plugin.getDungeonManager().getGame(p.getUniqueId());

        if (game != null && p.getWorld().equals(game.getWorld())) {
            e.setKeepInventory(true);
            e.getDrops().clear();
            e.setDroppedExp(0);
            e.setKeepLevel(true);

            game.sendMessage("game.no_reward");
            String deathMsg = plugin.getMessagesFile().getString("game.death");
            if (deathMsg != null) p.sendMessage(ColorUtils.parseWithPrefix(deathMsg));

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                p.spigot().respawn();
                game.stop(true);
            }, 1L);
        }
    }
}