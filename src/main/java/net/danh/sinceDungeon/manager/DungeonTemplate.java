package net.danh.sinceDungeon.manager;

import net.danh.sinceDungeon.reward.DungeonReward;

import java.util.List;
import java.util.Map;

public record DungeonTemplate(String id, String templateWorld, boolean isPublic, List<Condition> conditions,
                              Map<Integer, Integer> rewardTiers, List<DungeonReward> rewardPool,
                              List<List<Map<String, Object>>> rawStages) {
    public record Condition(String requirement, String failMessage) {
    }
}