package net.danh.sinceDungeon.actions.impl;

import net.danh.sinceDungeon.actions.DungeonAction;
import net.danh.sinceDungeon.manager.DungeonGame;
import net.danh.sinceDungeon.system.MMOItemsHook;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;

public class LootChestAction extends DungeonAction {
    private final Vector chestLocation;
    private final Map<Integer, String> dynamicItemsConfig = new HashMap<>();
    private final Map<Integer, ItemStack> cachedVanillaItems = new HashMap<>();
    private boolean isOpened = false;

    public LootChestAction(Vector location, Map<Integer, String> itemsConfig) {
        this.chestLocation = location;

        for (Map.Entry<Integer, String> entry : itemsConfig.entrySet()) {
            String data = entry.getValue();
            if (data.toUpperCase().startsWith("MMOITEMS")) {
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
    public void start(DungeonGame game) {
        if (game.getWorld() == null) return;
        Location loc = new Location(game.getWorld(), chestLocation.getBlockX(), chestLocation.getBlockY(), chestLocation.getBlockZ());
        Block b = loc.getBlock();
        b.setType(Material.CHEST);

        if (b.getState() instanceof Chest chest) {
            Inventory inv = chest.getInventory();
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

            chest.update();

            game.sendMessage("action.chest_appear");
        }
    }

    private boolean isValidSlot(int slot, Inventory inv) {
        return slot >= 0 && slot < inv.getSize();
    }

    private ItemStack parseVanilla(String data) {
        try {
            String[] parts = data.split(":");
            if (parts.length < 2) return null;
            Material mat = Material.matchMaterial(parts[0]);
            if (mat != null) {
                return new ItemStack(mat, Integer.parseInt(parts[1]));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private ItemStack parseDynamic(String data) {
        try {
            String[] parts = data.split(":");
            if (parts.length >= 4 && parts[0].equalsIgnoreCase("MMOITEMS")) {
                // Kiểm tra an toàn trước khi gọi Hook
                if (Bukkit.getPluginManager().isPluginEnabled("MMOItems")) {
                    return MMOItemsHook.getMMOItem(parts[1], parts[2], Integer.parseInt(parts[3]));
                }
            }
        } catch (Throwable e) { // Dùng Throwable để bắt NoClassDefFoundError nếu phát sinh
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public void onEvent(DungeonGame game, Event event) {
        if (event instanceof PlayerInteractEvent e) {
            if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
            if (!e.hasBlock()) return;

            Block b = e.getClickedBlock();
            if (isTargetChest(b) && !isOpened) {
                isOpened = true;
                game.sendMessage("action.chest_found");
            }
        } else if (event instanceof InventoryCloseEvent e) {
            Inventory inv = e.getInventory();
            if (inv.getHolder() instanceof Chest chest) {
                if (isTargetChest(chest.getBlock())) {
                    if (isInventoryEmpty(inv)) {
                        this.completed = true;
                        game.sendMessage("action.loot_complete");
                        chest.getBlock().setType(Material.AIR);
                        game.getWorld().playSound(chest.getLocation(), org.bukkit.Sound.ENTITY_ITEM_PICKUP, 1f, 1f);
                    } else {
                        game.sendMessage("action.chest_not_empty");
                    }
                }
            }
        }
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