package net.danh.sinceDungeon.editor;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/**
 * Hardened InventoryHolder to prevent Title-Spoofing exploits in Editor GUIs.
 * Now supports Pagination state tracking.
 */
public record EditorHolder(EditorSession session, String menuType, int page) implements InventoryHolder {

    @Override
    public @NotNull Inventory getInventory() {
        return null;
    }
}