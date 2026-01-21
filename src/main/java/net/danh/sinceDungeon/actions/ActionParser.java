package net.danh.sinceDungeon.actions;

import java.util.Map;

@FunctionalInterface
public interface ActionParser {
    // [FIX] Đổi từ Map<?, ?> thành Map<String, Object>
    DungeonAction parse(Map<String, Object> data);
}