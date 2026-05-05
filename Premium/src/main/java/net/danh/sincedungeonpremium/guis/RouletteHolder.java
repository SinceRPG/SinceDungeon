package net.danh.sincedungeonpremium.guis;

import net.danh.sinceDungeon.guis.reward.RewardSession;
import net.danh.sinceDungeon.models.DungeonReward;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/**
 * Custom InventoryHolder to handle safe exits from the Roulette GUI.
 * Secures the pending reward against inventory closures.
 */
public class RouletteHolder implements InventoryHolder {

    private final RewardSession session;
    private final DungeonReward pendingReward;
    private boolean claimed = false;

    public RouletteHolder(RewardSession session, DungeonReward pendingReward) {
        this.session = session;
        this.pendingReward = pendingReward;
    }

    public RewardSession getSession() {
        return session;
    }

    public DungeonReward getPendingReward() {
        return pendingReward;
    }

    public boolean isClaimed() {
        return claimed;
    }

    public void setClaimed(boolean claimed) {
        this.claimed = claimed;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return null;
    }
}