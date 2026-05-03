package net.danh.sinceDungeon.actions.impl;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/**
 * A secure holder for Per-Player Virtual Loot Chests.
 * Ensures the inventory system can identify and protect these generated GUIs.
 */
public record VirtualLootHolder() implements InventoryHolder {

    @Override
    public @NotNull Inventory getInventory() {
        return null;
    }
}