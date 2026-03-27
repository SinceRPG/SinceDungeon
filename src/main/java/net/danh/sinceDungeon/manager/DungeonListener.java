package net.danh.sinceDungeon.manager;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.api.events.DungeonEndEvent;
import net.danh.sinceDungeon.party.PartyManager;
import net.danh.sinceDungeon.party.PartyManager.Party;
import net.danh.sinceDungeon.system.WorldManager;
import net.danh.sinceDungeon.utils.ColorUtils;
import net.danh.sinceDungeon.utils.ServerVersion;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.*;

import java.util.List;

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

    // ==========================================
    // LỚP KHIÊN BẢO VỆ MÔI TRƯỜNG & KHỐI
    // ==========================================

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        String prefix = plugin.getConfigFile().getString("dungeon.world-prefix", "SinceDungeon_");
        if (e.getLocation().getWorld().getName().startsWith(prefix)) {
            e.blockList().clear();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        String prefix = plugin.getConfigFile().getString("dungeon.world-prefix", "SinceDungeon_");
        if (e.getBlock().getWorld().getName().startsWith(prefix)) {
            e.blockList().clear();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockIgnite(BlockIgniteEvent e) {
        String prefix = plugin.getConfigFile().getString("dungeon.world-prefix", "SinceDungeon_");
        if (e.getBlock().getWorld().getName().startsWith(prefix)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent e) {
        String prefix = plugin.getConfigFile().getString("dungeon.world-prefix", "SinceDungeon_");
        if (e.getBlock().getWorld().getName().startsWith(prefix)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent e) {
        String prefix = plugin.getConfigFile().getString("dungeon.world-prefix", "SinceDungeon_");
        if (e.getBlock().getWorld().getName().startsWith(prefix)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent e) {
        if (plugin.getDungeonManager().getGame(e.getPlayer().getUniqueId()) != null) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent e) {
        if (plugin.getDungeonManager().getGame(e.getPlayer().getUniqueId()) != null) {
            e.setCancelled(true);
        }
    }

    // ==========================================
    // LỚP KHIÊN BẢO VỆ VẬT THỂ TRANG TRÍ (DECORATION)
    // ==========================================

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakEvent e) {
        String prefix = plugin.getConfigFile().getString("dungeon.world-prefix", "SinceDungeon_");
        if (e.getEntity().getWorld().getName().startsWith(prefix)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamageDecor(EntityDamageEvent e) {
        String prefix = plugin.getConfigFile().getString("dungeon.world-prefix", "SinceDungeon_");
        if (e.getEntity().getWorld().getName().startsWith(prefix)) {
            if (e.getEntity() instanceof ArmorStand || e.getEntity() instanceof ItemFrame ||
                    e.getEntity() instanceof Painting || e.getEntity() instanceof Minecart ||
                    e.getEntity() instanceof Boat || e.getEntity() instanceof LeashHitch) {

                EntityDamageEvent.DamageCause cause = e.getCause();
                if (cause == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION ||
                        cause == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION ||
                        cause == EntityDamageEvent.DamageCause.FIRE ||
                        cause == EntityDamageEvent.DamageCause.FIRE_TICK ||
                        cause == EntityDamageEvent.DamageCause.LAVA ||
                        cause == EntityDamageEvent.DamageCause.HOT_FLOOR ||
                        cause == EntityDamageEvent.DamageCause.SUFFOCATION ||
                        cause == EntityDamageEvent.DamageCause.FALLING_BLOCK) {

                    e.setCancelled(true);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteractEntityDecor(PlayerInteractEntityEvent e) {
        if (plugin.getDungeonManager().getGame(e.getPlayer().getUniqueId()) != null) {
            if (e.getRightClicked() instanceof ArmorStand || e.getRightClicked() instanceof ItemFrame ||
                    e.getRightClicked() instanceof Painting || e.getRightClicked() instanceof LeashHitch) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteractAtEntityDecor(PlayerInteractAtEntityEvent e) {
        if (plugin.getDungeonManager().getGame(e.getPlayer().getUniqueId()) != null) {
            if (e.getRightClicked() instanceof ArmorStand) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArmorStandManipulate(PlayerArmorStandManipulateEvent e) {
        String prefix = plugin.getConfigFile().getString("dungeon.world-prefix", "SinceDungeon_");
        if (e.getPlayer().getWorld().getName().startsWith(prefix)) {
            e.setCancelled(true);
        }
    }

    // ==========================================
    // LỚP BẢO VỆ CHỐNG THOÁT MAP BẰNG LỖ HỔNG (PORTALS, LỆNH)
    // ==========================================

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommandPreprocess(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (plugin.getDungeonManager().getGame(p.getUniqueId()) != null) {
            boolean blockCommands = plugin.getConfigFile().getBoolean("dungeon.gameplay.block-commands", true);
            if (blockCommands && !p.hasPermission("SinceDungeon.admin")) {
                String cmd = e.getMessage().split(" ")[0].toLowerCase();
                List<String> allowed = plugin.getConfigFile().getStringList("dungeon.gameplay.allowed-commands");

                if (!allowed.contains(cmd)) {
                    e.setCancelled(true);
                    p.sendMessage(ColorUtils.parseWithPrefix(plugin.getMessagesFile().getString("error.command_blocked", "<red>Bạn không được phép dùng lệnh này trong Dungeon!")));
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityTransform(EntityTransformEvent e) {
        String prefix = plugin.getConfigFile().getString("dungeon.world-prefix", "SinceDungeon_");
        if (e.getEntity().getWorld().getName().startsWith(prefix)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityPortal(EntityPortalEvent e) {
        String prefix = plugin.getConfigFile().getString("dungeon.world-prefix", "SinceDungeon_");
        if (e.getEntity().getWorld().getName().startsWith(prefix)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerPortal(PlayerPortalEvent e) {
        if (plugin.getDungeonManager().getGame(e.getPlayer().getUniqueId()) != null) {
            e.setCancelled(true);
        }
    }

    // ==========================================
    // CÁC SỰ KIỆN TƯƠNG TÁC THÔNG THƯỜNG & CHIẾN ĐẤU
    // ==========================================

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDamage(EntityDamageByEntityEvent e) {
        String prefix = plugin.getConfigFile().getString("dungeon.world-prefix", "SinceDungeon_");
        if (e.getEntity().getWorld().getName().startsWith(prefix)) {
            if (e.getEntity() instanceof ArmorStand || e.getEntity() instanceof ItemFrame || e.getEntity() instanceof Minecart) {
                if (getRealAttacker(e.getDamager()) != null) {
                    e.setCancelled(true);
                    return;
                }
            }
        }

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
        plugin.getPartyManager().updatePlayerName(p);

        String prefix = plugin.getConfigFile().getString("dungeon.world-prefix", "SinceDungeon_");

        if (p.getLocation().getWorld() != null && p.getLocation().getWorld().getName().startsWith(prefix)) {
            World ghostWorld = p.getLocation().getWorld();
            plugin.getLogger().warning("Rescuing ghosted player " + p.getName() + " from deleted instance.");

            p.teleportAsync(Bukkit.getWorlds().get(0).getSpawnLocation()).thenAccept(success -> {
                if (success) {
                    String msg = plugin.getMessagesFile().getString("admin.ghost_rescued", "<yellow>Hệ thống đã giải cứu bạn khỏi một Dungeon bị lỗi/đóng cửa.");
                    p.sendMessage(ColorUtils.parseWithPrefix(msg));

                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (ghostWorld.getPlayers().isEmpty()) {
                            plugin.getLogger().info("Ghost World " + ghostWorld.getName() + " is now empty. Deleting permanently...");
                            WorldManager.forceUnloadAndDelete(plugin, ghostWorld);
                        }
                    }, 40L);
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
        Player p = e.getPlayer();
        DungeonGame game = plugin.getDungeonManager().getGame(p.getUniqueId());
        if (game != null && game.getWorld().equals(p.getWorld())) {
            if (e.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK && e.getItem() != null) {
                Material mat = e.getItem().getType();
                if (mat.name().contains("BOAT") || mat.name().contains("MINECART")) {
                    e.setCancelled(true);
                    return;
                }
            }
        }
        pass(p, e);
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
    public void onInvClick(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player p) {
            DungeonGame game = plugin.getDungeonManager().getGame(p.getUniqueId());
            if (game != null && game.getWorld() != null && game.getWorld().equals(p.getWorld())) {
                boolean preventDrop = true;
                if (game.getTemplate() != null) {
                    preventDrop = game.getTemplate().settings().preventItemDropping();
                }
                if (preventDrop) {
                    String act = e.getAction().name();
                    if (act.contains("DROP")) {
                        e.setCancelled(true);
                        p.sendMessage(ColorUtils.parseWithPrefix(plugin.getMessagesFile().getString("error.can_not_drop")));
                    }
                }
                pass(p, e);
            }
        }
    }

    @EventHandler
    public void onInvDrag(InventoryDragEvent e) {
        if (e.getWhoClicked() instanceof Player p) pass(p, e);
    }

    @EventHandler
    public void onCloseInv(InventoryCloseEvent e) {
        if (e.getPlayer() instanceof Player p) pass(p, e);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onKill(EntityDeathEvent e) {
        String prefix = plugin.getConfigFile().getString("dungeon.world-prefix", "SinceDungeon_");

        if (e.getEntity().getWorld().getName().startsWith(prefix)) {
            if (!(e.getEntity() instanceof Player)) {
                boolean clearDrops = plugin.getConfigFile().getBoolean("dungeon.clear-mob-drops", true);
                DungeonGame targetGame = null;

                for (DungeonGame g : plugin.getDungeonManager().getActiveGames().values()) {
                    if (g.getWorld() != null && g.getWorld().equals(e.getEntity().getWorld())) {
                        targetGame = g;
                        break;
                    }
                }

                if (targetGame != null && targetGame.getTemplate() != null) {
                    clearDrops = targetGame.getTemplate().settings().clearMobDrops();
                }

                if (clearDrops) {
                    e.getDrops().clear();
                    e.setDroppedExp(0);
                }
            }

            for (DungeonGame game : plugin.getDungeonManager().getActiveGames().values()) {
                if (game.getWorld() != null && game.getWorld().equals(e.getEntity().getWorld())) {
                    game.onEvent(e);
                    break;
                }
            }
        }
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
            boolean keepInv = true;
            if (game.getTemplate() != null) {
                keepInv = game.getTemplate().settings().keepInventoryOnDeath();
            }

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

                    String deathAction = "RESPAWN";
                    if (game.getTemplate() != null && game.getTemplate().settings().deathAction() != null) {
                        deathAction = game.getTemplate().settings().deathAction();
                    }

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

            boolean blockPearls = true;
            if (game.getTemplate() != null) {
                blockPearls = game.getTemplate().settings().blockEnderPearls();
            }

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
            boolean preventDrop = true;
            if (game.getTemplate() != null) {
                preventDrop = game.getTemplate().settings().preventItemDropping();
            }
            if (preventDrop) {
                e.setCancelled(true);
                p.sendMessage(ColorUtils.parseWithPrefix(plugin.getMessagesFile().getString("error.can_not_drop")));
            }
        }
    }
}