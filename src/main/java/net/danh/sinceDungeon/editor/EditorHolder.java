package net.danh.sinceDungeon.editor;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/**
 * Hardened InventoryHolder to prevent Title-Spoofing exploits in Editor GUIs.
 */
public class EditorHolder implements InventoryHolder {
    private final EditorSession session;
    private final String menuType;

    public EditorHolder(EditorSession session, String menuType) {
        this.session = session;
        this.menuType = menuType;
    }

    public EditorSession getSession() {
        return session;
    }

    public String getMenuType() {
        return menuType;
    }

    @Override
    public @NotNull Inventory getInventory() {
        // Return null as we strictly use this for instance checking
        return null;
    }
}