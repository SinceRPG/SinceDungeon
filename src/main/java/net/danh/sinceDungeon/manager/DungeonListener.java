package net.danh.sinceDungeon.manager;

import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.party.PartyManager.Party;
import net.danh.sinceDungeon.utils.ColorUtils;
import net.danh.sinceDungeon.utils.ServerVersion;
import org.bukkit.Bukkit;
import org.bukkit.entity.*;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.*;

/**
 * Global listener routing Bukkit events to active dungeon instances.
 * Implements Ghost Rescue mechanisms and strict Friendly-Fire mitigation.
 */
public class DungeonListener implements Listener {
    private final SinceDungeon plugin;

    /**
     * Constructs the DungeonListener.
     *
     * @param plugin The main plugin instance.
     */
    public DungeonListener(SinceDungeon plugin) {
        this.plugin = plugin;
    }

    private void pass(Player p, Event e) {
        if (p == null) return;
        DungeonGame game = plugin.getDungeonManager().getGame(p.getUniqueId());
        if (game != null && game.getWorld() != null && game.getWorld().equals(p.getWorld())) {
            game.onEvent(e);
        }
    }

    /**
     * Resolves the true attacker behind a damage event, tracing projectiles and explosives.
     *
     * @param damager The immediate entity causing damage.
     * @return The Player responsible, or null.
     */
    private Player getRealAttacker(Entity damager) {
        if (damager instanceof Player p) return p;
        if (damager instanceof Projectile proj && proj.getShooter() instanceof Player p) return p;
        if (damager instanceof AreaEffectCloud cloud && cloud.getSource() instanceof Player p) return p;
        if (damager instanceof TNTPrimed tnt && tnt.getSource() instanceof Player p) return p;
        return null;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDamage(EntityDamageByEntityEvent e) {
        if (e.getEntity() instanceof Player victim) {
            Player attacker = getRealAttacker(e.getDamager());

            if (attacker != null && !attacker.equals(victim)) {
                Party party = plugin.getPartyManager().getParty(victim.getUniqueId());
                if (party != null && party.getMembers().contains(attacker.getUniqueId())) {
                    boolean allowFF = plugin.getConfigFile().getBoolean("party.allow-friendly-fire");
                    if (!allowFF) {
                        e.setCancelled(true);
                        return;
                    }
                }
            }
            pass(victim, e);
        }

        Player attacker = getRealAttacker(e.getDamager());
        if (attacker != null) pass(attacker, e);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        String prefix = plugin.getConfigFile().getString("dungeon.world-prefix", "SinceDungeon_");

        if (p.getLocation().getWorld() != null && p.getLocation().getWorld().getName().startsWith(prefix)) {
            plugin.getLogger().warning("Rescuing ghosted player " + p.getName() + " from deleted instance.");
            p.teleportAsync(Bukkit.getWorlds().get(0).getSpawnLocation()).thenAccept(success -> {
                if (success && !p.isDead()) {
                    p.setHealth(0);
                    p.spigot().respawn();
                }
            });
        }
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
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        pass(e.getPlayer(), e);
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        pass(e.getPlayer(), e);
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent e) {
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
    public void onProjectileLaunch(ProjectileLaunchEvent e) {
        if (e.getEntity().getShooter() instanceof Player p) pass(p, e);
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent e) {
        if (e.getEntity().getShooter() instanceof Player p) pass(p, e);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        DungeonGame game = plugin.getDungeonManager().getGame(p.getUniqueId());
        if (game != null) game.stop(true);

        Party party = plugin.getPartyManager().getParty(p.getUniqueId());
        if (party != null && party.getLeader().equals(p.getUniqueId())) {
            plugin.getPartyManager().electNewLeader(party);
        }
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        DungeonGame game = plugin.getDungeonManager().getGame(p.getUniqueId());
        if (game != null && !p.getWorld().equals(game.getWorld())) {
            plugin.getLogger().info(p.getName() + plugin.getMessagesFile().getString("admin.log.leave_dungeon"));
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

            game.broadcastMessage("game.death", "<player>", p.getName());

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                game.restorePlayerState(p);
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
                p.sendMessage(ColorUtils.parseWithPrefix(plugin.getMessagesFile().getString("error.can_not_teleport")));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDropItem(PlayerDropItemEvent e) {
        Player p = e.getPlayer();
        DungeonGame game = plugin.getDungeonManager().getGame(p.getUniqueId());

        if (game != null && game.getWorld() != null && game.getWorld().equals(p.getWorld())) {
            e.setCancelled(true);
            p.sendMessage(ColorUtils.parseWithPrefix(plugin.getMessagesFile().getString("error.can_not_drop")));
        }
    }
}