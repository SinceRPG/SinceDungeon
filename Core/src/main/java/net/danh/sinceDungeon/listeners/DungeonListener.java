package net.danh.sinceDungeon.listeners;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.api.events.DungeonEndEvent;
import net.danh.sinceDungeon.hooks.MythicMobsHook;
import net.danh.sinceDungeon.managers.LivesManager;
import net.danh.sinceDungeon.managers.WorldManager;
import net.danh.sinceDungeon.models.DungeonGame;
import net.danh.sinceDungeon.systems.party.DefaultPartyProvider;
import net.danh.sinceDungeon.utils.ColorUtils;
import net.danh.sinceDungeon.utils.SchedulerCompat;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.*;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.*;
import org.bukkit.permissions.PermissionAttachment;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Handles all core gameplay events occurring within an active Dungeon instance.
 * Protects the dungeon environment from unauthorized destruction, manages player deaths,
 * and safely intercepts cross-world teleportations to prevent transfer lockups.
 */
public class DungeonListener implements Listener {
    private final SinceDungeon plugin;
    private final Map<UUID, PendingDeathAction> pendingDeathActions = new ConcurrentHashMap<>();
    private String worldPrefix;

    public DungeonListener(SinceDungeon plugin) {
        this.plugin = plugin;
        updateConfig();
    }

    /**
     * Updates JIT-cached properties when the plugin is reloaded.
     * Prevents extremely costly I/O lookups during rapid event execution.
     */
    public void updateConfig() {
        String prefix = plugin.getConfigFile().getString("dungeon.world-prefix", "SinceDungeon_");
        this.worldPrefix = (prefix == null || prefix.trim().isEmpty()) ? "SinceDungeon_" : prefix;
    }

    private boolean isDungeonWorld(World world) {
        return world != null
                && (world.getName().startsWith(worldPrefix) || plugin.getDungeonManager().hasGameInWorld(world.getName()));
    }

    private boolean isDungeonLocation(Location location) {
        return location != null
                && location.getWorld() != null
                && (location.getWorld().getName().startsWith(worldPrefix) || plugin.getDungeonManager().getGameByLocation(location) != null);
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

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        if (isDungeonLocation(e.getLocation())) {
            e.blockList().clear();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        if (isDungeonWorld(e.getBlock().getWorld())) {
            e.blockList().clear();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockIgnite(BlockIgniteEvent e) {
        if (isDungeonWorld(e.getBlock().getWorld())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent e) {
        if (isDungeonWorld(e.getBlock().getWorld())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent e) {
        if (isDungeonWorld(e.getBlock().getWorld())) {
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

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakEvent e) {
        if (isDungeonWorld(e.getEntity().getWorld())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamageDecor(EntityDamageEvent e) {
        if (isDungeonWorld(e.getEntity().getWorld())) {
            if (e.getEntity() instanceof ArmorStand || e.getEntity() instanceof ItemFrame || e.getEntity() instanceof Painting || e.getEntity() instanceof Minecart || e.getEntity() instanceof Boat || e.getEntity() instanceof LeashHitch) {

                if (Bukkit.getPluginManager().isPluginEnabled("MythicMobs")) {
                    if (MythicMobsHook.isMythicMob(e.getEntity())) return;
                }
                e.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteractEntityDecor(PlayerInteractEntityEvent e) {
        if (plugin.getDungeonManager().getGame(e.getPlayer().getUniqueId()) != null) {
            if (e.getRightClicked() instanceof ArmorStand || e.getRightClicked() instanceof ItemFrame || e.getRightClicked() instanceof Painting || e.getRightClicked() instanceof LeashHitch) {
                e.setCancelled(true);
            }
            pass(e.getPlayer(), e);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteractAtEntityDecor(PlayerInteractAtEntityEvent e) {
        if (plugin.getDungeonManager().getGame(e.getPlayer().getUniqueId()) != null) {
            if (e.getRightClicked() instanceof ArmorStand) {
                e.setCancelled(true);
            }
            pass(e.getPlayer(), e);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArmorStandManipulate(PlayerArmorStandManipulateEvent e) {
        if (isDungeonWorld(e.getPlayer().getWorld())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommandPreprocess(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        DungeonGame game = plugin.getDungeonManager().getGame(p.getUniqueId());
        if (game != null && game.getWorld() != null && game.getWorld().equals(p.getWorld())) {
            boolean blockCommands = plugin.getConfigFile().getBoolean("dungeon.gameplay.block-commands", true);
            if (blockCommands && !p.hasPermission("SinceDungeon.admin")) {
                String cmd = e.getMessage().split(" ")[0].toLowerCase();
                if (cmd.contains(":")) {
                    String[] parts = cmd.split(":");
                    if (parts.length > 1) {
                        cmd = "/" + parts[1];
                    }
                }
                List<String> allowed = plugin.getConfigFile().getStringList("dungeon.gameplay.allowed-commands");
                if (!allowed.contains(cmd)) {
                    e.setCancelled(true);
                    p.sendMessage(ColorUtils.parseWithPrefix(plugin.getLanguageManager().getString("error.command_blocked")));
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityTransform(EntityTransformEvent e) {
        if (isDungeonWorld(e.getEntity().getWorld())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSlimeSplit(SlimeSplitEvent e) {
        if (isDungeonWorld(e.getEntity().getWorld())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntitySpawn(EntitySpawnEvent e) {
        if (isDungeonWorld(e.getEntity().getWorld())) {
            if (e.getEntity() instanceof FallingBlock) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityPortal(EntityPortalEvent e) {
        if (isDungeonWorld(e.getEntity().getWorld())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerPortal(PlayerPortalEvent e) {
        if (plugin.getDungeonManager().getGame(e.getPlayer().getUniqueId()) != null) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDamage(EntityDamageByEntityEvent e) {
        if (isDungeonWorld(e.getEntity().getWorld())) {
            if (e.getEntity() instanceof ArmorStand || e.getEntity() instanceof ItemFrame || e.getEntity() instanceof Minecart) {
                if (Bukkit.getPluginManager().isPluginEnabled("MythicMobs")) {
                    if (MythicMobsHook.isMythicMob(e.getEntity())) return;
                }
                e.setCancelled(true);
                return;
            }
        }

        Player trueVictim = null;
        if (e.getEntity() instanceof Player pVictim) {
            trueVictim = pVictim;
        } else if (e.getEntity() instanceof Tameable pet && pet.isTamed() && pet.getOwner() instanceof Player owner) {
            trueVictim = owner;
        }

        if (trueVictim != null) {
            Player attacker = getRealAttacker(e.getDamager());
            if (attacker != null && !attacker.equals(trueVictim)) {
                boolean sameParty = false;

                if (plugin.getPartyManager().getProvider().hasParty(trueVictim.getUniqueId())) {
                    Set<UUID> members = plugin.getPartyManager().getProvider().getMembers(trueVictim.getUniqueId());
                    if (members != null && members.contains(attacker.getUniqueId())) {
                        sameParty = true;
                    }
                }

                DungeonGame game = plugin.getDungeonManager().getGame(trueVictim.getUniqueId());
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
            pass(trueVictim, e);
        }

        Player attacker = getRealAttacker(e.getDamager());
        if (attacker != null) pass(attacker, e);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();

        if (plugin.getPartyManager().getProvider() instanceof DefaultPartyProvider defaultParty) {
            defaultParty.updatePlayerName(p);
        }

        plugin.getLivesManager().loadPlayer(p.getUniqueId());

        if (plugin.getConfigFile().getBoolean("cross-server.enabled", false)) {
            plugin.getDungeonManager().checkPendingCrossServerJoin(p);
        }

        // MVI FIX: Ghost Rescue Bypass conditionally verifies Multi-Verse implementation before overriding configurations.
        if (isDungeonWorld(p.getLocation().getWorld())) {
            World ghostWorld = p.getLocation().getWorld();
            String logMsg = plugin.getLanguageManager().getString("admin.log.rescuing_ghost", "Rescuing ghosted player <player> from deleted instance.");
            plugin.getLogger().warning(logMsg.replace("<player>", p.getName()));

            boolean mviEnabled = Bukkit.getPluginManager().isPluginEnabled("Multiverse-Inventories")
                    || Bukkit.getPluginManager().isPluginEnabled("Multiverse-Core");

            PermissionAttachment attachment = null;
            if (mviEnabled) {
                attachment = p.addAttachment(plugin);
                List<String> bypassPerms = plugin.getConfigFile().getStringList("settings.mvi-bypass-permissions");
                if (bypassPerms == null || bypassPerms.isEmpty()) {
                    bypassPerms = Arrays.asList("mvinv.bypass.*", "Multiverse-Inventories.bypass.*");
                }
                for (String perm : bypassPerms) {
                    attachment.setPermission(perm, true);
                }
                p.recalculatePermissions();
            }

            final PermissionAttachment finalAttachment = attachment;

            p.teleportAsync(Bukkit.getWorlds().get(0).getSpawnLocation()).thenAccept(success -> {
                if (success) {
                    String msg = plugin.getLanguageManager().getString("admin.ghost_rescued", "&eThe system rescued you from a deleted or corrupted Dungeon instance.");
                    p.sendMessage(ColorUtils.parseWithPrefix(msg));

                    SchedulerCompat.runGlobalLater(plugin, () -> {
                        try {
                            if (finalAttachment != null) {
                                p.removeAttachment(finalAttachment);
                                p.recalculatePermissions();
                            }
                        } catch (Exception ignored) {
                        }

                        if (ghostWorld.getPlayers().isEmpty()) {
                            String delLog = plugin.getLanguageManager().getString("admin.log.deleting_ghost_world", "Ghost World <world> is now empty. Deleting permanently...");
                            plugin.getLogger().info(delLog.replace("<world>", ghostWorld.getName()));
                            WorldManager.forceUnloadAndDelete(plugin, ghostWorld);
                        }
                    }, 40L);
                }
            });
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        DungeonGame game = plugin.getDungeonManager().getGame(p.getUniqueId());
        if (game != null && game.getWorld().equals(p.getWorld())) {
            if (e.getAction() == Action.RIGHT_CLICK_BLOCK && e.getItem() != null) {
                Material mat = e.getItem().getType();
                if (mat.name().contains("BOAT") || mat.name().contains("MINECART")) {
                    e.setCancelled(true);
                    return;
                }
            }
        }
        pass(p, e);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBreak(BlockBreakEvent e) {
        if (plugin.getDungeonManager().getGame(e.getPlayer().getUniqueId()) != null) {
            e.setCancelled(true);
        }
        pass(e.getPlayer(), e);
    }

    @EventHandler(priority = EventPriority.LOWEST)
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
                        p.sendMessage(ColorUtils.parseWithPrefix(plugin.getLanguageManager().getString("error.can_not_drop")));
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
        if (isDungeonLocation(e.getEntity().getLocation())) {
            if (!(e.getEntity() instanceof Player)) {
                boolean clearDrops = plugin.getConfigFile().getBoolean("dungeon.clear-mob-drops", true);

                DungeonGame targetGame = plugin.getDungeonManager().getGameByEntity(e.getEntity());

                if (targetGame != null && targetGame.getTemplate() != null) {
                    clearDrops = targetGame.getTemplate().settings().clearMobDrops();
                }

                if (clearDrops) {
                    e.getDrops().clear();
                    e.setDroppedExp(0);
                }
            }

            DungeonGame game = plugin.getDungeonManager().getGameByEntity(e.getEntity());
            if (game != null) {
                game.onEvent(e);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAsyncChat(AsyncChatEvent e) {
        Player p = e.getPlayer();
        if (plugin.getPartyManager().getProvider().isPartyChatEnabled(p.getUniqueId())) {
            if (plugin.getPartyManager().getProvider().hasParty(p.getUniqueId())) {
                e.setCancelled(true);
                String msg = PlainTextComponentSerializer.plainText().serialize(e.message());
                plugin.getPartyManager().getProvider().sendPartyMessage(p.getUniqueId(), p.getName(), msg);
            } else {
                plugin.getPartyManager().getProvider().handleDisconnect(p);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        boolean debug = plugin.getConfigFile().getBoolean("settings.debug", false);
        pendingDeathActions.remove(p.getUniqueId());

        if (debug) {
            String msg = plugin.getLanguageManager().getString("admin.debug.quit_triggered", "[Debug] PlayerQuitEvent triggered for <player>");
            plugin.getLogger().info(msg.replace("<player>", p.getName()));
        }

        try {
            plugin.getDungeonManager().cancelPendingRequest(p.getUniqueId());
            if (debug) {
                String logMsg = plugin.getLanguageManager().getString("admin.debug.quit_cancel_request", "[Debug] Cancelled pending requests for <player>");
                plugin.getLogger().info(logMsg.replace("<player>", p.getName()));
            }
        } catch (Exception ex) {
            String errorMsg = plugin.getLanguageManager().getString("admin.debug.quit_error", "[Debug] Exception in <task>: <error>")
                    .replace("<task>", "cancelPendingRequest")
                    .replace("<error>", ex.getMessage());
            plugin.getLogger().severe(errorMsg);
            plugin.getLogger().log(Level.SEVERE, errorMsg, ex);
        }

        try {
            DungeonGame game = plugin.getDungeonManager().getGame(p.getUniqueId());
            if (game != null) {
                game.handlePlayerDisconnect(p, true);
                if (debug) {
                    String logMsg = plugin.getLanguageManager().getString("admin.debug.quit_dungeon_handled", "[Debug] Handled Dungeon disconnect for <player>");
                    plugin.getLogger().info(logMsg.replace("<player>", p.getName()));
                }
            }
        } catch (Exception ex) {
            String errorMsg = plugin.getLanguageManager().getString("admin.debug.quit_error", "[Debug] Exception in <task>: <error>")
                    .replace("<task>", "handlePlayerDisconnect")
                    .replace("<error>", ex.getMessage());
            plugin.getLogger().severe(errorMsg);
            plugin.getLogger().log(Level.SEVERE, errorMsg, ex);
        }

        try {
            plugin.getPartyManager().getProvider().handleDisconnect(p);
            if (debug) {
                String logMsg = plugin.getLanguageManager().getString("admin.debug.quit_party_handled", "[Debug] Handled Party disconnect for <player>");
                plugin.getLogger().info(logMsg.replace("<player>", p.getName()));
            }
        } catch (Exception ex) {
            String errorMsg = plugin.getLanguageManager().getString("admin.debug.quit_error", "[Debug] Exception in <task>: <error>")
                    .replace("<task>", "PartyManager")
                    .replace("<error>", ex.getMessage());
            plugin.getLogger().severe(errorMsg);
            plugin.getLogger().log(Level.SEVERE, errorMsg, ex);
        }

        try {
            plugin.getLivesManager().unloadPlayer(p.getUniqueId());
            if (debug) {
                String logMsg = plugin.getLanguageManager().getString("admin.debug.quit_lives_handled", "[Debug] Handled Lives unload for <player>");
                plugin.getLogger().info(logMsg.replace("<player>", p.getName()));
            }
        } catch (Exception ex) {
            String errorMsg = plugin.getLanguageManager().getString("admin.debug.quit_error", "[Debug] Exception in <task>: <error>")
                    .replace("<task>", "LivesManager")
                    .replace("<error>", ex.getMessage());
            plugin.getLogger().severe(errorMsg);
            plugin.getLogger().log(Level.SEVERE, errorMsg, ex);
        }
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();

        if (isDungeonWorld(p.getWorld())) {
            DungeonGame game = plugin.getDungeonManager().getGame(p.getUniqueId());
            if (game == null || !p.getWorld().equals(game.getWorld())) {

                if (p.hasPermission("SinceDungeon.admin") && p.getGameMode() == GameMode.SPECTATOR) {
                    return;
                }

                String logMsg = plugin.getLanguageManager().getString("admin.log.unauthorized_entry", "Intercepted unauthorized entry by <player> into <world>");
                plugin.getLogger().warning(logMsg.replace("<player>", p.getName()).replace("<world>", p.getWorld().getName()));

                String blockMsg = plugin.getLanguageManager().getString("error.dungeon_sealed_entry", "<red>Area sealed. Unauthorized entry detected!");
                p.sendMessage(ColorUtils.parseWithPrefix(blockMsg));

                p.teleportAsync(Bukkit.getWorlds().get(0).getSpawnLocation());
                return;
            }
        }

        DungeonGame game = plugin.getDungeonManager().getGame(p.getUniqueId());
        if (game != null && game.getWorld() != null && !p.getWorld().equals(game.getWorld())) {
            String logLeave = plugin.getLanguageManager().getString("admin.log.leave_dungeon", " left the dungeon. Stopping game.");
            plugin.getLogger().info(p.getName() + logLeave);
            game.handlePlayerDisconnect(p, false);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        DungeonGame game = plugin.getDungeonManager().getGame(p.getUniqueId());
        if (game != null && game.getWorld() != null) {
            Location spawnLoc = game.getRespawnLocation();
            e.setRespawnLocation(spawnLoc);
        }

        PendingDeathAction action = pendingDeathActions.remove(p.getUniqueId());
        if (action == null) return;

        SchedulerCompat.runGlobalLater(plugin, () -> {
            if (!p.isOnline()) return;

            DungeonGame checkGame = plugin.getDungeonManager().getGame(p.getUniqueId());
            if (checkGame == null || !checkGame.isRunning()) return;

            switch (action) {
                case RESPAWN -> finishDungeonRespawn(p);
                case SPECTATE -> {
                    p.sendMessage(ColorUtils.parseWithPrefix(plugin.getLanguageManager().getString("game.death_spectate")));
                    p.setGameMode(GameMode.SPECTATOR);
                    checkGame.checkWipeout();
                }
                case OUT_OF_LIVES_SPECTATE -> {
                    p.sendMessage(ColorUtils.parseWithPrefix(plugin.getLanguageManager().getString("lives.out_of_lives_spectate")));
                    p.setGameMode(GameMode.SPECTATOR);
                    checkGame.checkWipeout();
                }
                case FAIL -> checkGame.stop(true, DungeonEndEvent.EndReason.FAILED);
                case OUT_OF_LIVES_FAIL -> {
                    p.sendMessage(ColorUtils.parseWithPrefix(plugin.getLanguageManager().getString("lives.out_of_lives_kick")));
                    checkGame.stop(true, DungeonEndEvent.EndReason.FAILED);
                }
                case OUT_OF_LIVES_KICK -> {
                    p.sendMessage(ColorUtils.parseWithPrefix(plugin.getLanguageManager().getString("lives.out_of_lives_kick")));
                    checkGame.handlePlayerDisconnect(p, false);
                }
            }
        }, 1L);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        DungeonGame game = plugin.getDungeonManager().getGame(p.getUniqueId());

        if (game != null && p.getWorld().equals(game.getWorld())) {
            boolean keepInv = true;
            int deductLives = 1;

            if (game.getTemplate() != null) {
                keepInv = game.getTemplate().settings().keepInventoryOnDeath();
                deductLives = game.getTemplate().settings().livesDeductedPerDeath();
            }

            if (keepInv) {
                e.setKeepInventory(true);
                e.getDrops().clear();
                e.setDroppedExp(0);
                e.setKeepLevel(true);
            }

            game.broadcastMessage("game.death", "<player>", p.getName());

            boolean outOfLives = false;
            if (deductLives > 0) {
                plugin.getLivesManager().removeLives(p.getUniqueId(), deductLives);
                LivesManager.PlayerLives livesData = plugin.getLivesManager().getLives(p.getUniqueId());
                int current = livesData != null ? livesData.getCurrentLives() : 0;
                int max = livesData != null ? livesData.getMaxLives() : 0;

                String lossMsg = plugin.getLanguageManager().getString("lives.deducted").replace("<amount>", String.valueOf(deductLives)).replace("<current>", String.valueOf(current)).replace("<max>", String.valueOf(max));
                p.sendMessage(ColorUtils.parseWithPrefix(lossMsg));

                if (current <= 0) {
                    outOfLives = true;
                }
            }

            pendingDeathActions.put(p.getUniqueId(), resolvePendingDeathAction(game, outOfLives));
        }
    }

    private PendingDeathAction resolvePendingDeathAction(DungeonGame game, boolean outOfLives) {
        if (outOfLives) {
            String outOfLivesAction = plugin.getConfigFile().getString("dungeon.out-of-lives-action", "SPECTATE");
            if (outOfLivesAction.equalsIgnoreCase("SPECTATE")) return PendingDeathAction.OUT_OF_LIVES_SPECTATE;
            if (outOfLivesAction.equalsIgnoreCase("FAIL")) return PendingDeathAction.OUT_OF_LIVES_FAIL;
            return PendingDeathAction.OUT_OF_LIVES_KICK;
        }

        String deathAction = "RESPAWN";
        if (game.getTemplate() != null && game.getTemplate().settings().deathAction() != null) {
            deathAction = game.getTemplate().settings().deathAction();
        }

        if (deathAction.equalsIgnoreCase("FAIL")) return PendingDeathAction.FAIL;
        if (deathAction.equalsIgnoreCase("SPECTATE")) return PendingDeathAction.SPECTATE;
        return PendingDeathAction.RESPAWN;
    }

    private void finishDungeonRespawn(Player p) {
        AttributeInstance maxHealth = p.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            p.setHealth(maxHealth.getValue());
        }
        p.setFoodLevel(20);
        p.setFireTicks(0);
        p.setFallDistance(0);
        p.setNoDamageTicks(40);
        p.setGameMode(GameMode.SURVIVAL);
    }

    private enum PendingDeathAction {
        RESPAWN,
        SPECTATE,
        FAIL,
        OUT_OF_LIVES_SPECTATE,
        OUT_OF_LIVES_FAIL,
        OUT_OF_LIVES_KICK
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        Player p = e.getPlayer();

        if (e.getTo() != null && isDungeonLocation(e.getTo())) {
            DungeonGame targetGame = plugin.getDungeonManager().getGameByLocation(e.getTo());

            if (targetGame == null || !targetGame.getParticipants().contains(p)) {
                if (p.hasPermission("SinceDungeon.admin") && p.getGameMode() == GameMode.SPECTATOR) {
                    return;
                }

                e.setCancelled(true);
                String blockMsg = plugin.getLanguageManager().getString("error.dungeon_sealed_teleport", "<red>Area sealed. Teleportation magic nullified!");
                p.sendMessage(ColorUtils.parseWithPrefix(blockMsg));
                return;
            }
        }

        DungeonGame game = plugin.getDungeonManager().getGame(p.getUniqueId());
        if (game != null && game.getWorld() != null && game.getWorld().equals(p.getWorld())) {
            PlayerTeleportEvent.TeleportCause cause = e.getCause();
            PlayerTeleportEvent.TeleportCause consumableEffect = null;
            for (PlayerTeleportEvent.TeleportCause c : PlayerTeleportEvent.TeleportCause.values()) {
                if (c.name().equals("CONSUMABLE_EFFECT") || c.name().equals("CHORUS_FRUIT")) {
                    consumableEffect = c;
                    break;
                }
            }

            boolean blockPearls = true;
            if (game.getTemplate() != null) {
                blockPearls = game.getTemplate().settings().blockEnderPearls();
            }

            boolean blockCommands = plugin.getConfigFile().getBoolean("dungeon.gameplay.block-teleport-commands", false);

            if ((blockPearls && (cause == PlayerTeleportEvent.TeleportCause.ENDER_PEARL || cause == consumableEffect)) || (blockCommands && cause == PlayerTeleportEvent.TeleportCause.COMMAND) || cause == PlayerTeleportEvent.TeleportCause.SPECTATE) {

                if (p.hasPermission("SinceDungeon.admin") && p.getGameMode() == GameMode.SPECTATOR) {
                    return;
                }

                e.setCancelled(true);
                p.sendMessage(ColorUtils.parseWithPrefix(plugin.getLanguageManager().getString("error.can_not_teleport")));
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
                p.sendMessage(ColorUtils.parseWithPrefix(plugin.getLanguageManager().getString("error.can_not_drop")));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityTarget(EntityTargetEvent e) {
        if (isDungeonLocation(e.getEntity().getLocation())) {
            DungeonGame game = plugin.getDungeonManager().getGameByEntity(e.getEntity());
            if (game != null) {
                game.onEvent(e);
            }
        }
    }
}
