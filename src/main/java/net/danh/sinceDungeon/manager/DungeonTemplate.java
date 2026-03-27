package net.danh.sinceDungeon.manager;

import net.danh.sinceDungeon.reward.DungeonReward;

import java.util.List;
import java.util.Map;

/**
 * Data record representing the configuration structure of a Dungeon map.
 */
public record DungeonTemplate(String id, String templateWorld, boolean isPublic,
                              List<Condition> conditions,
                              Map<Integer, Integer> rewardTiers,
                              List<DungeonReward> rewardPool,
                              Map<Integer, List<Map<String, Object>>> stages,
                              Settings settings) {

    /**
     * Defines an entry condition required to start the dungeon.
     */
    public record Condition(String id, String name, String requirement, String failMessage) {
    }

    /**
     * Holds per-dungeon gameplay settings.
     */
    public record Settings(boolean keepInventoryOnDeath, boolean preventItemDropping,
                           boolean blockEnderPearls, int kickDelayAfterFinish,
                           boolean forceDaylightAndClearWeather, boolean saveAndRestoreStats) {
    }
}