package net.danh.sinceDungeon.guis.top;

import net.danh.sinceDungeon.managers.TopManager;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/**
 * Hardened InventoryHolder for the Leaderboard GUI to prevent Title-Spoofing exploits.
 * Stores the current pagination state, dungeon ID, and selected leaderboard category.
 */
public record TopHolder(String dungeonId, TopManager.TopCategory category, int page) implements InventoryHolder {

    @Override
    public @NotNull Inventory getInventory() {
        return null;
    }
}