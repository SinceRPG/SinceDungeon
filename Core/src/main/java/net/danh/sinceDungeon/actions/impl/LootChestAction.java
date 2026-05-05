package net.danh.sinceDungeon.actions.impl;

import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.actions.DungeonAction;
import net.danh.sinceDungeon.actions.Tickable;
import net.danh.sinceDungeon.models.DungeonGame;
import net.danh.sinceDungeon.utils.ColorUtils;
import net.danh.sinceDungeon.utils.ItemBuilder;
import net.danh.sinceDungeon.utils.SoundUtils;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.*;

/**
 * Handles spawning a lootable chest that players must empty to proceed.
 * Supports both Shared (Physical) and Instanced (Per-Player Virtual) loot modes.
 * Optionally requires a specific Key item to unlock the chest.
 */
public class LootChestAction extends DungeonAction implements Tickable {
    private final Vector chestLocation;
    private final Map<Integer, String> itemsConfig;
    private final boolean perPlayer;
    private final String requiredKey;

    // Data for Per-Player Instanced Loot
    private final Map<UUID, Inventory> personalInventories = new HashMap<>();
    private final Set<UUID> finishedPlayers = new HashSet<>();
    private boolean isOpened = false;
    private Block chestBlock = null;

    public LootChestAction(Vector location, Map<Integer, String> itemsConfig, boolean perPlayer, String requiredKey) {
        this.chestLocation = location;
        this.itemsConfig = itemsConfig;
        this.perPlayer = perPlayer;
        this.requiredKey = requiredKey;
    }

    @Override
    public String getObjectiveText() {
        return SinceDungeon.getPlugin().getLanguageManager().getString("objective.loot_chest", "<gold>Loot the chest to proceed");
    }

    @Override
    public void cleanup(DungeonGame game) {
        if (chestBlock != null && !completed) {
            chestBlock.setType(Material.AIR);
        }
        personalInventories.clear();
        finishedPlayers.clear();
    }

    @Override
    public void start(DungeonGame game) {
        if (game.getWorld() == null) return;
        Location loc = new Location(game.getWorld(), chestLocation.getBlockX(), chestLocation.getBlockY(), chestLocation.getBlockZ());
        Block b = loc.getBlock();
        b.setType(Material.CHEST);
        b.getState().update(true, false);
        this.chestBlock = b;

        if (!perPlayer) {
            if (b.getState() instanceof Chest chest) {
                Inventory inv = chest.getBlockInventory();
                inv.clear();

                for (Map.Entry<Integer, String> entry : itemsConfig.entrySet()) {
                    if (isValidSlot(entry.getKey(), inv)) {
                        ItemStack is = ItemBuilder.parseDynamicItem(entry.getValue());
                        if (is != null) {
                            inv.setItem(entry.getKey(), is);
                        }
                    }
                }
            }
        }
        game.sendActionMessage(this, "init", "action.chest_appear");
    }

    @Override
    public void onTick(DungeonGame game) {
        if (completed || !isOpened || chestBlock == null) return;

        if (perPlayer) {
            Set<UUID> activePlayers = new HashSet<>();
            for (Player p : game.getParticipants()) {
                if (p.isOnline() && !p.isDead() && p.getGameMode() != GameMode.SPECTATOR && p.getWorld().equals(game.getWorld())) {
                    activePlayers.add(p.getUniqueId());
                }
            }

            if (!activePlayers.isEmpty() && finishedPlayers.containsAll(activePlayers)) {
                completeChestLogic(game, null);
            }
        } else {
            if (chestBlock.getState() instanceof Chest chest) {
                if (isInventoryEmpty(chest.getBlockInventory())) {
                    boolean cursorHasItem = false;
                    for (HumanEntity viewer : chest.getBlockInventory().getViewers()) {
                        if (viewer.getItemOnCursor() != null && viewer.getItemOnCursor().getType() != Material.AIR) {
                            cursorHasItem = true;
                            break;
                        }
                    }

                    if (!cursorHasItem) {
                        completeChestLogic(game, chest.getBlockInventory());
                    }
                }
            }
        }
    }

    private boolean isValidSlot(int slot, Inventory inv) {
        return slot >= 0 && slot < inv.getSize();
    }

    private Inventory generatePersonalLoot() {
        String title = SinceDungeon.getPlugin().getLanguageManager().getString("objective.loot_chest_title", "Treasure Chest");
        Inventory inv = Bukkit.createInventory(new VirtualLootHolder(), 27, ColorUtils.parse(title));

        for (Map.Entry<Integer, String> entry : itemsConfig.entrySet()) {
            if (isValidSlot(entry.getKey(), inv)) {
                ItemStack is = ItemBuilder.parseDynamicItem(entry.getValue());
                if (is != null) {
                    inv.setItem(entry.getKey(), is);
                }
            }
        }
        return inv;
    }

    /**
     * Emits a rejection sound if the player fails a key verification check.
     */
    private void playDenySound(Player p) {
        String soundLocked = SinceDungeon.getPlugin().getConfigFile().getString("sounds.door_locked", "block.chest.locked");
        if (soundLocked != null) {
            p.playSound(p.getLocation(), SoundUtils.getSound(soundLocked), 1f, 1f);
        }
    }

    @Override
    public void onEvent(DungeonGame game, Event event) {
        if (event instanceof PlayerInteractEvent e) {
            if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
            if (!e.hasBlock()) return;

            Player p = e.getPlayer();
            if (p.getGameMode() == GameMode.SPECTATOR) return;

            Block b = e.getClickedBlock();
            if (isTargetChest(b)) {

                // --- KEY CHECK LOGIC ---
                if (this.requiredKey != null && !this.requiredKey.equalsIgnoreCase("NONE") && !isOpened) {
                    ItemStack handItem = e.getItem();
                    if (handItem == null || handItem.getType() == Material.AIR) {
                        e.setCancelled(true);
                        String msg = SinceDungeon.getPlugin().getLanguageManager().getString("error.key_not_found", "&cYou need a Key!");
                        p.sendMessage(ColorUtils.parseWithPrefix(msg));
                        playDenySound(p);
                        return;
                    }

                    NamespacedKey keyTag = new NamespacedKey(SinceDungeon.getPlugin(), "dungeon_key_id");
                    if (!ItemBuilder.hasTag(handItem, keyTag, PersistentDataType.STRING) ||
                            !this.requiredKey.equals(ItemBuilder.getTag(handItem, keyTag, PersistentDataType.STRING))) {
                        e.setCancelled(true);
                        String msg = SinceDungeon.getPlugin().getLanguageManager().getString("error.wrong_key", "&cWrong key!");
                        p.sendMessage(ColorUtils.parseWithPrefix(msg));
                        playDenySound(p);
                        return;
                    }

                    // Consume the key and play unlock sound
                    handItem.setAmount(handItem.getAmount() - 1);
                    String soundUnlock = SinceDungeon.getPlugin().getConfigFile().getString("sounds.door_unlock", "block.iron_door.open");
                    p.playSound(p.getLocation(), SoundUtils.getSound(soundUnlock), 1f, 1f);
                }
                // --- END KEY CHECK LOGIC ---

                if (perPlayer) {
                    e.setCancelled(true);

                    if (finishedPlayers.contains(p.getUniqueId())) {
                        String msg = SinceDungeon.getPlugin().getLanguageManager().getString("action.chest_already_looted", "&cYou have already collected your share of the loot!");
                        p.sendMessage(ColorUtils.parseWithPrefix(msg));
                        return;
                    }

                    Inventory inv = personalInventories.computeIfAbsent(p.getUniqueId(), k -> generatePersonalLoot());
                    p.openInventory(inv);

                    if (!isOpened) {
                        isOpened = true;
                        game.sendActionMessage(this, "progress", "action.chest_found");
                    }
                } else {
                    if (!isOpened) {
                        isOpened = true;
                        game.sendActionMessage(this, "progress", "action.chest_found");
                    }
                }
            }
        } else if (event instanceof InventoryCloseEvent e) {
            Inventory inv = e.getInventory();
            if (perPlayer) {
                // Note: Avoid inv.getHolder() if the inventory has a location matching the target.
                if (inv.getLocation() == null && inv.getHolder() instanceof VirtualLootHolder) {
                    if (isInventoryEmpty(inv)) {
                        finishedPlayers.add(e.getPlayer().getUniqueId());
                        game.sendActionMessage(this, "progress", "action.chest_personal_looted", "<player>", e.getPlayer().getName());
                    } else if (!completed) {
                        game.sendActionMessage(this, "warning", "action.chest_not_empty");
                    }
                }
            } else {
                Location invLoc = inv.getLocation();
                if (invLoc != null && isTargetChest(invLoc.getBlock())) {
                    if (isInventoryEmpty(inv) && !completed) {
                        completeChestLogic(game, inv);
                    } else if (!completed) {
                        game.sendActionMessage(this, "warning", "action.chest_not_empty");
                    }
                }
            }
        } else if (event instanceof InventoryClickEvent e) {
            Inventory inv = e.getInventory();

            boolean isTarget = false;

            // Note: Avoid inv.getHolder() on world blocks to prevent BlockState snapshot lag.
            Location invLoc = inv.getLocation();
            if (invLoc != null && isTargetChest(invLoc.getBlock())) {
                isTarget = true;
            } else if (inv.getLocation() == null && inv.getHolder() instanceof VirtualLootHolder) {
                isTarget = true;
            }

            if (isTarget) {
                if (e.getWhoClicked() instanceof Player p && p.getGameMode() == GameMode.SPECTATOR) {
                    e.setCancelled(true);
                    return;
                }

                boolean blockAction = false;

                if (e.getClick() == ClickType.NUMBER_KEY || e.getClick() == ClickType.SWAP_OFFHAND) {
                    blockAction = true;
                } else if (e.getAction().name().contains("DROP") || e.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
                    blockAction = true;
                } else if (e.getClickedInventory() == e.getView().getTopInventory()) {
                    if (e.getCursor() != null && e.getCursor().getType() != Material.AIR) {
                        blockAction = true;
                    }
                } else if (e.getClickedInventory() == e.getView().getBottomInventory()) {
                    if (e.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
                        blockAction = true;
                    }
                }

                if (blockAction) {
                    e.setCancelled(true);
                    if (e.getWhoClicked() instanceof Player p) {
                        String msg = SinceDungeon.getPlugin().getLanguageManager().getString("error.cannot_store_in_lootchest", "<red>You cannot store items here!");
                        p.sendMessage(ColorUtils.parseWithPrefix(msg));
                    }
                }
            }
        } else if (event instanceof InventoryDragEvent e) {
            Inventory inv = e.getInventory();
            boolean isTarget = false;

            Location invLoc = inv.getLocation();
            if (invLoc != null && isTargetChest(invLoc.getBlock())) {
                isTarget = true;
            } else if (inv.getLocation() == null && inv.getHolder() instanceof VirtualLootHolder) {
                isTarget = true;
            }

            if (isTarget) {
                for (int slot : e.getRawSlots()) {
                    if (slot < inv.getSize()) {
                        e.setCancelled(true);
                        break;
                    }
                }
            }
        }
    }

    private void completeChestLogic(DungeonGame game, Inventory chestInv) {
        this.completed = true;
        game.sendActionMessage(this, "complete", "action.loot_complete");

        if (chestInv != null) {
            for (HumanEntity viewer : new ArrayList<>(chestInv.getViewers())) {
                viewer.closeInventory();
            }
        } else {
            for (Player p : game.getParticipants()) {
                if (p.isOnline() && p.getOpenInventory().getTopInventory().getHolder() instanceof VirtualLootHolder) {
                    p.closeInventory();
                }
            }
        }

        Bukkit.getScheduler().runTaskLater(SinceDungeon.getPlugin(), () -> {
            if (chestBlock != null) chestBlock.setType(Material.AIR);
            Location soundLoc = new Location(game.getWorld(), chestLocation.getBlockX(), chestLocation.getBlockY(), chestLocation.getBlockZ());
            game.getWorld().playSound(soundLoc, Sound.ENTITY_ITEM_PICKUP, 1f, 1f);
        }, 1L);
    }

    private boolean isTargetChest(Block b) {
        return b != null && b.getType() == Material.CHEST
                && b.getX() == chestLocation.getBlockX()
                && b.getY() == chestLocation.getBlockY()
                && b.getZ() == chestLocation.getBlockZ();
    }

    private boolean isInventoryEmpty(Inventory inv) {
        for (ItemStack item : inv.getContents()) {
            if (item != null && item.getType() != Material.AIR) return false;
        }
        return true;
    }
}