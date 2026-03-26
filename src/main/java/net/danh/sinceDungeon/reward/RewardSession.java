package net.danh.sinceDungeon.reward;

import net.danh.sinceDungeon.manager.DungeonTemplate;

import java.util.*;

/**
 * Maintains the session state for a player actively opening reward chests.
 * Now upgraded with Multi-Page Gacha Logic.
 */
public class RewardSession {
    private final DungeonTemplate template;
    // Bộ đệm lưu trữ vị trí rương: Page -> (Slot -> isClaimed)
    private final Map<Integer, Map<Integer, Boolean>> chestPages = new HashMap<>();
    private int chestCount;
    private boolean revealed = false;
    private int totalPages = 1;

    public RewardSession(int chestCount, DungeonTemplate template) {
        this.chestCount = chestCount;
        this.template = template;
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

    /**
     * Khởi tạo thuật toán phân bổ rương ngẫu nhiên qua các trang.
     * Tự động tính toán Slot dựa trên kích thước GUI.
     */
    public void setupPagination(int guiSize) {
        int prevSlot = guiSize < 18 ? guiSize - 2 : guiSize - 9;
        int nextSlot = guiSize - 1;

        int maxPerPage = guiSize - 2; // Dành 2 ô cho nút Chuyển trang

        this.totalPages = (int) Math.ceil((double) chestCount / maxPerPage);
        if (this.totalPages == 0) this.totalPages = 1;

        Random rand = new Random();
        int chestsToDistribute = this.chestCount;

        for (int p = 0; p < totalPages; p++) {
            Map<Integer, Boolean> pageMap = new HashMap<>();
            List<Integer> availableSlots = new ArrayList<>();

            for (int i = 0; i < guiSize; i++) {
                // Nếu có nhiều trang, phải chừa lại slot cho 2 nút điều hướng
                if (totalPages > 1 && (i == prevSlot || i == nextSlot)) continue;
                availableSlots.add(i);
            }

            int chestsOnPage = Math.min(chestsToDistribute, availableSlots.size());
            for (int i = 0; i < chestsOnPage; i++) {
                // Rải rương ngẫu nhiên vào các ô trống
                int slot = availableSlots.remove(rand.nextInt(availableSlots.size()));
                pageMap.put(slot, false); // false = Chưa mở
            }
            chestPages.put(p, pageMap);
            chestsToDistribute -= chestsOnPage;
        }
    }

    /**
     * Xác nhận mở rương tại một trang và slot cụ thể.
     *
     * @return true nếu rương tồn tại và chưa bị mở.
     */
    public boolean claimChest(int page, int slot) {
        Map<Integer, Boolean> pageMap = chestPages.get(page);
        if (pageMap != null && pageMap.containsKey(slot) && !pageMap.get(slot)) {
            pageMap.put(slot, true); // Đánh dấu đã mở
            decreaseChestCount();
            return true;
        }
        return false;
    }
}