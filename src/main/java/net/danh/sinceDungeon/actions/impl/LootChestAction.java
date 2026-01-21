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
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.Map;

public class LootChestAction extends DungeonAction {
    private final Vector chestLocation;
    private final Map<Integer, String> itemsConfig;
    private boolean isOpened = false;

    public LootChestAction(Vector location, Map<Integer, String> itemsConfig) {
        this.chestLocation = location;
        this.itemsConfig = itemsConfig;
    }

    @Override
    public void start(DungeonGame game) {
        if (game.getWorld() == null) return;
        Location loc = new Location(game.getWorld(), chestLocation.getX(), chestLocation.getY(), chestLocation.getZ());
        Block b = loc.getBlock();
        b.setType(Material.CHEST);

        if (b.getState() instanceof Chest chest) {
            chest.getInventory().clear();
            for (Map.Entry<Integer, String> entry : itemsConfig.entrySet()) {
                ItemStack item = parseItem(entry.getValue());
                if (item != null) {
                    chest.getInventory().setItem(entry.getKey(), item);
                }
            }
            game.sendMessage("action.chest_appear");
        }
    }

    private ItemStack parseItem(String data) {
        try {
            String[] parts = data.split(":");
            if (parts.length < 2) return null;

            if (parts[0].equalsIgnoreCase("MMOITEMS")) {
                if (parts.length >= 4 && Bukkit.getPluginManager().isPluginEnabled("MMOItems")) {
                    return MMOItemsHook.getMMOItem(parts[1], parts[2], Integer.parseInt(parts[3]));
                }
            } else {
                Material mat = Material.matchMaterial(parts[0]);
                if (mat != null) {
                    return new ItemStack(mat, Integer.parseInt(parts[1]));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public void onEvent(DungeonGame game, Event event) {
        if (event instanceof PlayerInteractEvent e) {
            if (!e.hasBlock()) return;
            Block b = e.getClickedBlock();
            if (isTargetChest(b) && !isOpened) {
                isOpened = true;
                game.sendMessage("action.chest_found");
            }
        } else if (event instanceof InventoryCloseEvent e) {
            if (e.getInventory().getHolder() instanceof Chest chest) {
                if (isTargetChest(chest.getBlock())) {
                    if (isInventoryEmpty(e.getInventory())) {
                        this.completed = true;
                        game.sendMessage("action.loot_complete");
                        // Remove chest after loot
                        chest.getBlock().setType(Material.AIR);
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

    private boolean isInventoryEmpty(org.bukkit.inventory.Inventory inv) {
        for (ItemStack item : inv.getContents()) {
            if (item != null && item.getType() != Material.AIR) return false;
        }
        return true;
    }
}