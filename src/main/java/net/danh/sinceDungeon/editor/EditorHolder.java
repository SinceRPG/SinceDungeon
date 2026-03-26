package net.danh.sinceDungeon.editor;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/**
 * Hardened InventoryHolder to prevent Title-Spoofing exploits in Editor GUIs.
 * Now supports Pagination state tracking.
 */
public class EditorHolder implements InventoryHolder {
    private final EditorSession session;
    private final String menuType;
    private final int page;

    public EditorHolder(EditorSession session, String menuType, int page) {
        this.session = session;
        this.menuType = menuType;
        this.page = page;
    }

    public EditorSession getSession() {
        return session;
    }

    public String getMenuType() {
        return menuType;
    }

    public int getPage() {
        return page;
    }

    @Override
    public @NotNull Inventory getInventory() {
        // Return null as we strictly use this for instance checking
        return null;
    }
}