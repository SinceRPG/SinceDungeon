package net.danh.sinceDungeon.actions;

import java.util.Map;

/**
 * Functional interface for parsing generic map data into a DungeonAction.
 */
@FunctionalInterface
public interface ActionParser {
    /**
     * Parses the given map data into a valid DungeonAction.
     *
     * @param data The configuration map data.
     * @return The parsed DungeonAction.
     */
    DungeonAction parse(Map<String, Object> data);
}