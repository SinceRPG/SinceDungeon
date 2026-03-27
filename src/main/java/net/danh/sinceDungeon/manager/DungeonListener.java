package net.danh.sinceDungeon.manager;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.api.events.DungeonEndEvent;
import net.danh.sinceDungeon.party.PartyManager;
import net.danh.sinceDungeon.party.PartyManager.Party;
import net.danh.sinceDungeon.utils.ColorUtils;
import net.danh.sinceDungeon.utils.ServerVersion;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
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

public class DungeonListener implements Listener {
    private final SinceDungeon plugin;

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

                boolean sameParty = false;

                Party party = plugin.getPartyManager().getParty(victim.getUniqueId());
                if (party != null && party.getMembers().contains(attacker.getUniqueId())) {
                    sameParty = true;
                }

                DungeonGame game = plugin.getDungeonManager().getGame(victim.getUniqueId());
                if (game != null && game.getParticipants().contains(attacker)) {
                    sameParty = true;
                }

                if (sameParty) {
                    boolean allowFF = plugin.getConfigFile().getConfig().getBoolean("party.allow-friendly-fire");
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
        plugin.getPartyManager().updatePlayerName(p);

        String prefix = plugin.getConfigFile().getString("dungeon.world-prefix", "SinceDungeon_");

        if (p.getLocation().getWorld() != null && p.getLocation().getWorld().getName().startsWith(prefix)) {
            plugin.getLogger().warning("Rescuing ghosted player " + p.getName() + " from deleted instance.");
            p.teleportAsync(Bukkit.getWorlds().get(0).getSpawnLocation()).thenAccept(success -> {
                if (success) {
                    String msg = plugin.getMessagesFile().getString("admin.ghost_rescued", "<yellow>Hệ thống đã giải cứu bạn khỏi một Dungeon bị lỗi/đóng cửa.");
                    p.sendMessage(ColorUtils.parseWithPrefix(msg));
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

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (plugin.getDungeonManager().getGame(e.getPlayer().getUniqueId()) != null) {
            e.setCancelled(true);
        }
        pass(e.getPlayer(), e);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (plugin.getDungeonManager().getGame(e.getPlayer().getUniqueId()) != null) {
            e.setCancelled(true);
        }
        pass(e.getPlayer(), e);
    }

    @EventHandler
    public void onCloseInv(InventoryCloseEvent e) {
        if (e.getPlayer() instanceof Player p) pass(p, e);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onKill(EntityDeathEvent e) {
        if (!(e.getEntity() instanceof Player)) {
            String prefix = plugin.getConfigFile().getString("dungeon.world-prefix", "SinceDungeon_");
            if (e.getEntity().getWorld().getName().startsWith(prefix)) {
                boolean clearDrops = plugin.getConfigFile().getConfig().getBoolean("dungeon.clear-mob-drops", true);
                if (clearDrops) {
                    e.getDrops().clear();
                    e.setDroppedExp(0);
                }
            }
        }

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

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAsyncChat(AsyncChatEvent e) {
        Player p = e.getPlayer();
        if (plugin.getPartyManager().isPartyChatEnabled(p.getUniqueId())) {
            PartyManager.Party party = plugin.getPartyManager().getParty(p.getUniqueId());
            if (party != null) {
                e.setCancelled(true);
                String msg = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(e.message());
                plugin.getPartyManager().sendPartyMessage(party, p.getName(), msg);
            } else {
                plugin.getPartyManager().removePlayerFromCache(p.getUniqueId());
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        DungeonGame game = plugin.getDungeonManager().getGame(p.getUniqueId());

        if (game != null) {
            game.handlePlayerDisconnect(p);
        }

        plugin.getPartyManager().handlePlayerDisconnect(p);
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        DungeonGame game = plugin.getDungeonManager().getGame(p.getUniqueId());

        if (game != null && game.isRunning() && !p.getWorld().equals(game.getWorld())) {
            plugin.getLogger().info(p.getName() + plugin.getMessagesFile().getString("admin.log.leave_dungeon"));
            game.handlePlayerDisconnect(p);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        DungeonGame game = plugin.getDungeonManager().getGame(p.getUniqueId());

        if (game != null && p.getWorld().equals(game.getWorld())) {
            // LẤY TỪ TEMPLATE THAY VÌ CONFIG
            boolean keepInv = game.getTemplate().settings().keepInventoryOnDeath();

            if (keepInv) {
                e.setKeepInventory(true);
                e.getDrops().clear();
                e.setDroppedExp(0);
                e.setKeepLevel(true);
            }

            game.broadcastMessage("game.death", "<player>", p.getName());

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (p.isOnline()) {
                    p.spigot().respawn();

                    String deathAction = plugin.getConfigFile().getString("dungeon.death-action", "RESPAWN");

                    if (deathAction.equalsIgnoreCase("FAIL")) {
                        game.stop(true, DungeonEndEvent.EndReason.FAILED);
                    } else {
                        p.teleportAsync(game.getWorld().getSpawnLocation().add(0.5, 1, 0.5));
                        p.setHealth(p.getAttribute(Attribute.MAX_HEALTH).getValue());
                        p.setFoodLevel(20);
                    }
                }
            }, 2L);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        Player p = e.getPlayer();
        DungeonGame game = plugin.getDungeonManager().getGame(p.getUniqueId());

        if (game != null && game.getWorld() != null && game.getWorld().equals(p.getWorld())) {
            PlayerTeleportEvent.TeleportCause cause = e.getCause();
            PlayerTeleportEvent.TeleportCause consumableEffect = ServerVersion.isAtMost(1, 21, 5) ? PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT : PlayerTeleportEvent.TeleportCause.CONSUMABLE_EFFECT;

            // LẤY TỪ TEMPLATE THAY VÌ CONFIG
            boolean blockPearls = game.getTemplate().settings().blockEnderPearls();

            if ((blockPearls && (cause == PlayerTeleportEvent.TeleportCause.ENDER_PEARL || cause == consumableEffect)) ||
                    cause == PlayerTeleportEvent.TeleportCause.COMMAND ||
                    cause == PlayerTeleportEvent.TeleportCause.SPECTATE) {

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
            // LẤY TỪ TEMPLATE THAY VÌ CONFIG
            boolean preventDrop = game.getTemplate().settings().preventItemDropping();
            if (preventDrop) {
                e.setCancelled(true);
                p.sendMessage(ColorUtils.parseWithPrefix(plugin.getMessagesFile().getString("error.can_not_drop")));
            }
        }
    }
}