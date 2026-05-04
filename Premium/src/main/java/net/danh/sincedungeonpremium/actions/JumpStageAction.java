package net.danh.sincedungeonpremium.actions;

import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.actions.DungeonAction;
import net.danh.sinceDungeon.models.DungeonGame;
import net.danh.sincedungeonpremium.SinceDungeonPremium;

import java.lang.reflect.Field;

/**
 * Premium Action: Jump Stage
 * Explicitly terminates the current stage branch and forcibly jumps execution
 * to another defined stage index to bypass overlapping stages.
 */
public class JumpStageAction extends DungeonAction {

    private final int targetStage;

    public JumpStageAction(int targetStage) {
        this.targetStage = targetStage;
    }

    @Override
    public void start(DungeonGame game) {
        try {
            Field stageIndexField = DungeonGame.class.getDeclaredField("currentStageIndex");
            stageIndexField.setAccessible(true);
            stageIndexField.set(game, targetStage - 2);

            Field actionIndexField = DungeonGame.class.getDeclaredField("currentActionIndex");
            actionIndexField.setAccessible(true);
            actionIndexField.set(game, 9999);

            this.forceComplete();
        } catch (Exception e) {
            SinceDungeonPremium.getInstance().getLogger().severe("Failed to jump stage: " + e.getMessage());
            this.forceComplete();
        }
    }

    @Override
    public String getObjectiveText() {
        return SinceDungeon.getPlugin().getLanguageManager().getString("objective.jump_stage", "Jumping to next stage...");
    }
}