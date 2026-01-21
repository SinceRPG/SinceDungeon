package net.danh.sinceDungeon.reward;

import java.util.List;

public record DungeonReward(String type, String value, double chance, String displayName, List<String> lore) {
}