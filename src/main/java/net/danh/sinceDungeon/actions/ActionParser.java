package net.danh.sinceDungeon.actions;

import java.util.Map;

@FunctionalInterface
public interface ActionParser {
    DungeonAction parse(Map<String, Object> data);
}