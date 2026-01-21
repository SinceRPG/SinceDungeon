package net.danh.sinceDungeon.reward;

import net.danh.sinceDungeon.manager.DungeonTemplate;

public class RewardSession {
    private final DungeonTemplate template;
    // Không để final chestCount nữa vì cần thay đổi
    private int chestCount;
    private boolean revealed = false;

    public RewardSession(int chestCount, DungeonTemplate template) {
        this.chestCount = chestCount;
        this.template = template;
    }

    public int getChestCount() {
        return chestCount;
    }

    // [MỚI] Hàm giảm số lượng khi nhận quà
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