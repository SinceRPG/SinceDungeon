package net.danh.sinceDungeon.reward;

import java.util.List;

/**
 * Data record representing a single potential reward object.
 */
public record DungeonReward(String type, String value, double chance, String displayName, List<String> lore) {
}