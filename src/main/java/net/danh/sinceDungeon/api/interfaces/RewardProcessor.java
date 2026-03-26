package net.danh.sinceDungeon.api.interfaces;

import org.bukkit.entity.Player;

@FunctionalInterface
public interface RewardProcessor {
    /**
     * Xử lý trao phần thưởng cho người chơi.
     *
     * @param player      Người chơi nhận thưởng
     * @param value       Giá trị cấu hình từ config (vd: "DIAMOND:5" hoặc "give %player% 100")
     * @param displayName Tên hiển thị của phần thưởng (nếu có)
     */
    void giveReward(Player player, String value, String displayName);
}