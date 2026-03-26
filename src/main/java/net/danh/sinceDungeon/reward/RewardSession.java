package net.danh.sinceDungeon.reward;

import net.danh.sinceDungeon.manager.DungeonTemplate;

/**
 * Maintains the session state for a player actively opening reward chests.
 */
public class RewardSession {
    private final DungeonTemplate template;
    private int chestCount;
    private boolean revealed = false;

    /**
     * Constructs a new RewardSession.
     *
     * @param chestCount The initial count of chests available to claim.
     * @param template   The template storing the pool data.
     */
    public RewardSession(int chestCount, DungeonTemplate template) {
        this.chestCount = chestCount;
        this.template = template;
    }

    public int getChestCount() {
        return chestCount;
    }

    /**
     * Decreases the available chest claim count by one.
     */
    public void decreaseChestCount() {
        if (this.chestCount > 0) {
            this.chestCount--;
        }
    }

    public DungeonTemplate getTemplate() {
        return template;
    }

    public boolean isRevealed() {
        return revealed;
    }

    public void setRevealed(boolean revealed) {
        this.revealed = revealed;
    }
}