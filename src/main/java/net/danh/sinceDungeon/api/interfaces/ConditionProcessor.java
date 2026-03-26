package net.danh.sinceDungeon.api.interfaces;

import org.bukkit.entity.Player;

@FunctionalInterface
public interface ConditionProcessor {
    /**
     * Kiểm tra điều kiện của người chơi.
     *
     * @param player Người chơi cần kiểm tra
     * @param value  Giá trị điều kiện từ config
     * @return true nếu thỏa mãn, ngược lại false
     */
    boolean check(Player player, String value);
}