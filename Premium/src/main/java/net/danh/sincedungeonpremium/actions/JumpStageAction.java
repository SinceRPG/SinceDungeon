package net.danh.sincedungeonpremium.actions;

import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.actions.DungeonAction;
import net.danh.sinceDungeon.models.DungeonGame;
import net.danh.sincedungeonpremium.SinceDungeonPremium;

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
            game.jumpToStage(targetStage);
            this.forceComplete();
        } catch (Exception e) {
            String logMsg = SinceDungeonPremium.getInstance().getFileManager().getMessageRaw("log.jump_stage_fail");
            SinceDungeonPremium.getInstance().getLogger().severe(logMsg.replace("<error>", e.getMessage()));
            this.forceComplete();
        }
    }

    @Override
    public String getObjectiveText() {
        return SinceDungeon.getPlugin().getLanguageManager().getString("objective.jump_stage", "Jumping to next stage...");
    }
}