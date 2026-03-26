package net.danh.sinceDungeon.reward;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/**
 * Hardened InventoryHolder to prevent Title-Spoofing exploits in Reward GUIs.
 * Now fully supports dynamic pagination.
 */
public class RewardHolder implements InventoryHolder {
    private final RewardSession session;
    private final int page;

    public RewardHolder(RewardSession session, int page) {
        this.session = session;
        this.page = page;
    }

    public RewardSession getSession() {
        return session;
    }

    public int getPage() {
        return page;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return null; // Trả về null để khóa chặt các Event dò rỉ
    }
}