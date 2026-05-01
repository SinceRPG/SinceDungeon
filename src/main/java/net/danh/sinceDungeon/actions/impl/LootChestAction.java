package net.danh.sinceDungeon.actions.impl;

import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.actions.DungeonAction;
import net.danh.sinceDungeon.actions.Tickable;
import net.danh.sinceDungeon.hooks.MMOItemsHook;
import net.danh.sinceDungeon.models.DungeonGame;
import net.danh.sinceDungeon.utils.ColorUtils;
import net.danh.sinceDungeon.utils.ItemBuilder;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;

/**
 * Handles spawning a lootable chest that players must empty to proceed.
 * Supports Vanilla items, MMOItems, and dynamically generated Custom Dungeon Keys.
 */
public class LootChestAction extends DungeonAction implements Tickable {
    private final Vector chestLocation;
    private final Map<Integer, String> dynamicItemsConfig = new HashMap<>();
    private final Map<Integer, ItemStack> cachedVanillaItems = new HashMap<>();
    private boolean isOpened = false;
    private Block chestBlock = null;

    public LootChestAction(Vector location, Map<Integer, String> itemsConfig) {
        this.chestLocation = location;
        for (Map.Entry<Integer, String> entry : itemsConfig.entrySet()) {
            String data = entry.getValue();
            if (data.toUpperCase().startsWith("MMOITEMS") || data.toUpperCase().startsWith("KEY:")) {
                dynamicItemsConfig.put(entry.getKey(), data);
            } else {
                ItemStack is = parseVanilla(data);
                if (is != null) {
                    cachedVanillaItems.put(entry.getKey(), is);
                }
            }
        }
    }

    @Override
    public String getObjectiveText() {
        return SinceDungeon.getPlugin().getMessagesFile().getString("objective.loot_chest", "<gold>Loot the chest to proceed");
    }

    @Override
    public void cleanup(DungeonGame game) {
        /**
         * Safely removes the physical chest block if the action is aborted or timed out.
         */
        if (chestBlock != null && !completed) {
            chestBlock.setType(Material.AIR);
        }
    }

    @Override
    public void start(DungeonGame game) {
        if (game.getWorld() == null) return;
        Location loc = new Location(game.getWorld(), chestLocation.getBlockX(), chestLocation.getBlockY(), chestLocation.getBlockZ());
        Block b = loc.getBlock();
        b.setType(Material.CHEST);
        b.getState().update(true, false);
        this.chestBlock = b;

        if (b.getState() instanceof Chest chest) {
            Inventory inv = chest.getBlockInventory();
            inv.clear();

            for (Map.Entry<Integer, ItemStack> entry : cachedVanillaItems.entrySet()) {
                if (isValidSlot(entry.getKey(), inv)) {
                    inv.setItem(entry.getKey(), entry.getValue().clone());
                }
            }

            for (Map.Entry<Integer, String> entry : dynamicItemsConfig.entrySet()) {
                ItemStack item = parseDynamic(entry.getValue());
                if (item != null && isValidSlot(entry.getKey(), inv)) {
                    inv.setItem(entry.getKey(), item);
                }
            }
            game.sendActionMessage(this, "init", "action.chest_appear");
        }
    }

    @Override
    public void onTick(DungeonGame game) {
        if (completed || !isOpened || chestBlock == null) return;

        if (chestBlock.getState() instanceof Chest chest) {
            if (isInventoryEmpty(chest.getBlockInventory())) {

                boolean cursorHasItem = false;
                for (org.bukkit.entity.HumanEntity viewer : chest.getBlockInventory().getViewers()) {
                    if (viewer.getItemOnCursor() != null && viewer.getItemOnCursor().getType() != Material.AIR) {
                        cursorHasItem = true;
                        break;
                    }
                }

                if (!cursorHasItem) {
                    completeChestLogic(game, chest);
                }
            }
        }
    }

    private boolean isValidSlot(int slot, Inventory inv) {
        return slot >= 0 && slot < inv.getSize();
    }

    private ItemStack parseVanilla(String data) {
        try {
            String cleanData = data.replace(" ", "");
            String[] parts = cleanData.split(":");
            if (parts.length < 1) return null;
            Material mat = Material.matchMaterial(parts[0]);
            if (mat != null) {
                int amount = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;
                return new ItemStack(mat, amount);
            }
        } catch (Exception e) {
            String msg = SinceDungeon.getPlugin().getMessagesFile().getString("admin.warning.vanilla_parse_fail", "Cannot parse Vanilla item: <data>");
            SinceDungeon.getPlugin().getLogger().warning(msg.replace("<data>", data));
        }
        return null;
    }

    /**
     * Parses dynamic item configurations, specifically MMOItems and Internal Dungeon Keys.
     */
    private ItemStack parseDynamic(String data) {
        try {
            String cleanData = data.replace(" ", "");
            String[] parts = cleanData.split(":");

            // Generate Custom Dungeon Key using Builder
            if (parts[0].equalsIgnoreCase("KEY") && parts.length >= 2) {
                String keyId = parts[1];
                int amount = parts.length >= 3 ? Integer.parseInt(parts[2]) : 1;

                NamespacedKey keyTag = new NamespacedKey(SinceDungeon.getPlugin(), "dungeon_key_id");
                ConfigurationSection cfg = SinceDungeon.getPlugin().getConfigFile().getConfig().getConfigurationSection("dungeon-items.key");

                return ItemBuilder.fromConfig(SinceDungeon.getPlugin(), "dungeon-items.key", "TRIPWIRE_HOOK")
                        .amount(amount)
                        .applyConfig(cfg, "&6&lDungeon Key", "<id>", keyId)
                        .setTag(keyTag, PersistentDataType.STRING, keyId)
                        .build();
            }

            if (parts.length >= 3 && parts[0].equalsIgnoreCase("MMOITEMS")) {
                if (Bukkit.getPluginManager().isPluginEnabled("MMOItems")) {
                    int amount = parts.length > 3 ? Integer.parseInt(parts[3]) : 1;
                    return MMOItemsHook.getMMOItem(parts[1], parts[2], amount);
                } else {
                    String msg = SinceDungeon.getPlugin().getMessagesFile().getString("admin.warning.mmoitems_missing", "MMOItems is missing for item: <data>");
                    SinceDungeon.getPlugin().getLogger().warning(msg.replace("<data>", data));
                }
            }
        } catch (Throwable e) {
            String msg = SinceDungeon.getPlugin().getMessagesFile().getString("admin.warning.mmoitems_parse_fail", "Cannot parse dynamic item: <data>");
            SinceDungeon.getPlugin().getLogger().warning(msg.replace("<data>", data));
        }
        return null;
    }

    @Override
    public void onEvent(DungeonGame game, Event event) {
        if (event instanceof PlayerInteractEvent e) {
            if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
            if (!e.hasBlock()) return;

            if (e.getPlayer().getGameMode() == GameMode.SPECTATOR) return;

            Block b = e.getClickedBlock();
            if (isTargetChest(b) && !isOpened) {
                isOpened = true;
                game.sendActionMessage(this, "progress", "action.chest_found");
            }
        } else if (event instanceof InventoryCloseEvent e) {
            Inventory inv = e.getInventory();
            if (inv.getHolder() instanceof Chest chest) {
                if (isTargetChest(chest.getBlock())) {
                    if (isInventoryEmpty(inv) && !completed) {
                        completeChestLogic(game, chest);
                    } else if (!completed) {
                        game.sendActionMessage(this, "warning", "action.chest_not_empty");
                    }
                }
            }
        } else if (event instanceof InventoryClickEvent e) {
            Inventory inv = e.getInventory();
            if (inv.getHolder() instanceof Chest chest && isTargetChest(chest.getBlock())) {

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
                        String msg = SinceDungeon.getPlugin().getMessagesFile().getString("error.cannot_store_in_lootchest", "<red>You cannot store items here!");
                        p.sendMessage(ColorUtils.parseWithPrefix(msg));
                    }
                }
            }
        } else if (event instanceof InventoryDragEvent e) {
            Inventory inv = e.getInventory();
            if (inv.getHolder() instanceof Chest chest && isTargetChest(chest.getBlock())) {
                for (int slot : e.getRawSlots()) {
                    if (slot < inv.getSize()) {
                        e.setCancelled(true);
                        break;
                    }
                }
            }
        }
    }

    private void completeChestLogic(DungeonGame game, Chest chest) {
        this.completed = true;
        game.sendActionMessage(this, "complete", "action.loot_complete");

        for (org.bukkit.entity.HumanEntity viewer : new java.util.ArrayList<>(chest.getBlockInventory().getViewers())) {
            viewer.closeInventory();
        }

        Bukkit.getScheduler().runTaskLater(SinceDungeon.getPlugin(), () -> {
            chest.getBlock().setType(Material.AIR);
            game.getWorld().playSound(chest.getLocation(), org.bukkit.Sound.ENTITY_ITEM_PICKUP, 1f, 1f);
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