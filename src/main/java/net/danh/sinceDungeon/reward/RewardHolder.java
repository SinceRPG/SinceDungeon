package net.danh.sinceDungeon.reward;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/**
 * Hardened InventoryHolder to prevent Title-Spoofing exploits in Reward GUIs.
 * Now fully supports dynamic pagination.
 */
public record RewardHolder(RewardSession session, int page) implements InventoryHolder {

    @Override
    public @NotNull Inventory getInventory() {
        return null;
    }
}