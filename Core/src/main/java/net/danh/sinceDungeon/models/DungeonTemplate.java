package net.danh.sinceDungeon.models;

import java.util.List;
import java.util.Map;

/**
 * Immutable data record that holds the complete parsed structure of a Dungeon.
 * Provides rapid read access to settings preventing config I/O lag during gameplay.
 */
public record DungeonTemplate(String id, String templateWorld, boolean isPublic,
                              List<Condition> conditions,
                              Map<Integer, Integer> soloRewardTiers,
                              Map<Integer, Integer> partyRewardTiers,
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

    /**
     * Defines the gameplay settings and restrictions for the dungeon instance.
     * Maps perfectly to the internal YAML configurations for dynamic checking.
     */
    public record Settings(boolean keepInventoryOnDeath, boolean preventItemDropping,
                           boolean blockEnderPearls, int kickDelayAfterFinish,
                           boolean forceDaylightAndClearWeather, boolean saveAndRestoreStats,
                           String deathAction, boolean clearMobDrops,
                           int requiredLivesToJoin, int livesDeductedPerDeath,
                           int livesDeductedOnLeave, int livesDeductedOnFail,
                           int livesDeductedOnClear,
                           boolean randomizeStages, int maxPlayers,
                           int cooldownSeconds, boolean cooldownOnLeave,
                           List<String> onStartCmds, List<String> onFinishCmds,
                           List<String> onFirstFinishCmds, String requiredItem,
                           boolean consumeRequiredItem, String startLocation) {
    }
}
