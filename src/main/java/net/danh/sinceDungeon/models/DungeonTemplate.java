package net.danh.sinceDungeon.models;

import java.util.List;
import java.util.Map;

/**
 * Immutable data record that holds the complete parsed structure of a Dungeon.
 */
public record DungeonTemplate(String id, String templateWorld, boolean isPublic,
                              List<Condition> conditions,
                              Map<Integer, Integer> rewardTiers,
                              List<DungeonReward> rewardPool,
                              Map<Integer, StageData> stages,
                              Settings settings) {

    public record Condition(String id, String name, String requirement, String failMessage) {
    }

    /**
     * Holds the configuration for a specific dungeon stage.
     * Contains the spawn chance, stage-specific completion commands, and actions.
     */
    public record StageData(double chance, List<String> commands, List<Map<String, Object>> actions) {
    }

    public record Settings(boolean keepInventoryOnDeath, boolean preventItemDropping,
                           boolean blockEnderPearls, int kickDelayAfterFinish,
                           boolean forceDaylightAndClearWeather, boolean saveAndRestoreStats,
                           String deathAction, boolean clearMobDrops,
                           int requiredLivesToJoin, int livesDeductedPerDeath,
                           boolean randomizeStages, int maxPlayers,
                           int cooldownSeconds, List<String> onStartCmds,
                           List<String> onFinishCmds, List<String> onFirstFinishCmds) {
    }
}