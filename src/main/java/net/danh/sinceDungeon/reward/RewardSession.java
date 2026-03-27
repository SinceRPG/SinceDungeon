package net.danh.sinceDungeon.reward;

import net.danh.sinceDungeon.manager.DungeonTemplate;

import java.util.*;

/**
 * Maintains the session state for a player actively opening reward chests.
 * Now upgraded with Multi-Page Gacha Logic, Switching Protection and RAM Expiration.
 */
public class RewardSession {
    private final DungeonTemplate template;
    private final Map<Integer, Map<Integer, Boolean>> chestPages = new HashMap<>();
    private final long creationTime;
    private int chestCount;
    private boolean revealed = false;
    private int totalPages = 1;
    private boolean switchingPage = false;

    public RewardSession(int chestCount, DungeonTemplate template) {
        this.chestCount = chestCount;
        this.template = template;
        this.creationTime = System.currentTimeMillis();
    }

    public long getCreationTime() {
        return creationTime;
    }

    public int getChestCount() {
        return chestCount;
    }

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

    public Map<Integer, Map<Integer, Boolean>> getChestPages() {
        return chestPages;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public boolean isSwitchingPage() {
        return switchingPage;
    }

    public void setSwitchingPage(boolean switchingPage) {
        this.switchingPage = switchingPage;
    }

    public void setupPagination(int guiSize) {
        int prevSlot = guiSize < 18 ? guiSize - 2 : guiSize - 9;
        int nextSlot = guiSize - 1;

        int maxPerPage = guiSize - 2;

        this.totalPages = (int) Math.ceil((double) chestCount / maxPerPage);
        if (this.totalPages == 0) this.totalPages = 1;

        Random rand = new Random();
        int chestsToDistribute = this.chestCount;

        for (int p = 0; p < totalPages; p++) {
            Map<Integer, Boolean> pageMap = new HashMap<>();
            List<Integer> availableSlots = new ArrayList<>();

            for (int i = 0; i < guiSize; i++) {
                if (totalPages > 1 && (i == prevSlot || i == nextSlot)) continue;
                availableSlots.add(i);
            }

            int chestsOnPage = Math.min(chestsToDistribute, availableSlots.size());
            for (int i = 0; i < chestsOnPage; i++) {
                int slot = availableSlots.remove(rand.nextInt(availableSlots.size()));
                pageMap.put(slot, false);
            }
            chestPages.put(p, pageMap);
            chestsToDistribute -= chestsOnPage;
        }
    }

    public boolean claimChest(int page, int slot) {
        Map<Integer, Boolean> pageMap = chestPages.get(page);
        if (pageMap != null && pageMap.containsKey(slot) && !pageMap.get(slot)) {
            pageMap.put(slot, true);
            decreaseChestCount();
            return true;
        }
        return false;
    }
}