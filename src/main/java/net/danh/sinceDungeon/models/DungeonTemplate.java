package net.danh.sinceDungeon.models;

import java.util.List;
import java.util.Map;

public record DungeonTemplate(String id, String templateWorld, boolean isPublic,
                              List<Condition> conditions,
                              Map<Integer, Integer> rewardTiers,
                              List<DungeonReward> rewardPool,
                              Map<Integer, StageData> stages,
                              Settings settings) {

    public record Condition(String id, String name, String requirement, String failMessage) {
    }

    public record StageData(double chance, List<Map<String, Object>> actions) {
    }

    public record Settings(boolean keepInventoryOnDeath, boolean preventItemDropping,
                           boolean blockEnderPearls, int kickDelayAfterFinish,
                           boolean forceDaylightAndClearWeather, boolean saveAndRestoreStats,
                           String deathAction, boolean clearMobDrops,
                           int requiredLivesToJoin, int livesDeductedPerDeath,
                           boolean randomizeStages, int maxPlayers,
                           int cooldownSeconds, List<String> onStartCmds,
                           List<String> onFinishCmds, List<String> onStageCompleteCmds) {
        /**
         * Added commands triggering system and cooldown limits natively to the template object.
         */
    }
}