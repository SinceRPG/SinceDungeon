package net.danh.sinceDungeon.manager;

import net.danh.sinceDungeon.reward.DungeonReward;

import java.util.List;
import java.util.Map;

public record DungeonTemplate(String id, String templateWorld, boolean isPublic,
                              List<Condition> conditions,
                              Map<Integer, Integer> rewardTiers,
                              List<DungeonReward> rewardPool,
                              Map<Integer, List<Map<String, Object>>> stages) { // Changed stages type

    // [CHANGED] Added name field
    public record Condition(String id, String name, String requirement, String failMessage) {
    }
}