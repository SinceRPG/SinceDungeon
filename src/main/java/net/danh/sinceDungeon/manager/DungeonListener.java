package net.danh.sinceDungeon.manager;

import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.utils.ColorUtils;
import net.danh.sinceDungeon.utils.ServerVersion;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.*;

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

    @EventHandler(priority = EventPriority.HIGHEST)
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

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        Player p = e.getPlayer();
        DungeonGame game = plugin.getDungeonManager().getGame(p.getUniqueId());

        if (game != null && game.getWorld() != null && game.getWorld().equals(p.getWorld())) {
            PlayerTeleportEvent.TeleportCause cause = e.getCause();
            PlayerTeleportEvent.TeleportCause consumableEffect = ServerVersion.isAtMost(1, 21, 5) ? PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT : PlayerTeleportEvent.TeleportCause.CONSUMABLE_EFFECT;

            if (cause == PlayerTeleportEvent.TeleportCause.ENDER_PEARL ||
                    cause == consumableEffect ||
                    cause == PlayerTeleportEvent.TeleportCause.COMMAND) {

                e.setCancelled(true);
                String msg = plugin.getMessagesFile().getString("error.can_not_teleport", "<red>Không thể dịch chuyển theo cách này trong Dungeon!");
                p.sendMessage(ColorUtils.parse(msg));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDropItem(PlayerDropItemEvent e) {
        Player p = e.getPlayer();
        DungeonGame game = plugin.getDungeonManager().getGame(p.getUniqueId());

        if (game != null && game.getWorld() != null && game.getWorld().equals(p.getWorld())) {
            e.setCancelled(true);
            String msg = plugin.getMessagesFile().getString("error.can_not_drop", "<red>Không thể vứt vật phẩm trong Dungeon để tránh mất đồ!");
            p.sendMessage(ColorUtils.parse(msg));
        }
    }
}