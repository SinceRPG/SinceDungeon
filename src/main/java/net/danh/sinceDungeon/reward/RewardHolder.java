package net.danh.sinceDungeon.reward;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/**
 * Hardened InventoryHolder to prevent Title-Spoofing exploits in Reward GUIs.
 */
public class RewardHolder implements InventoryHolder {
    private final RewardSession session;

    public RewardHolder(RewardSession session) {
        this.session = session;
    }

    public RewardSession getSession() {
        return session;
    }

    @Override
    public @NotNull Inventory getInventory() {
        // Return null or throw exception as we don't need to return the physical inventory here
        return null;
    }
}