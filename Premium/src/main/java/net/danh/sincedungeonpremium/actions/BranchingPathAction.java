package net.danh.sincedungeonpremium.actions;

import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.actions.DungeonAction;
import net.danh.sinceDungeon.actions.Tickable;
import net.danh.sinceDungeon.managers.DungeonLoader;
import net.danh.sinceDungeon.models.DungeonGame;
import net.danh.sincedungeonpremium.SinceDungeonPremium;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public class BranchingPathAction extends DungeonAction implements Tickable {

    private final String pathAStr;
    private final String pathBStr;
    private final int stageA;
    private final int stageB;
    private final double radius;

    private Location locA;
    private Location locB;

    public BranchingPathAction(String pathAStr, String pathBStr, int stageA, int stageB, double radius) {
        this.pathAStr = pathAStr;
        this.pathBStr = pathBStr;
        this.stageA = stageA;
        this.stageB = stageB;
        this.radius = radius;
    }

    @Override
    public void start(DungeonGame game) {
        if (game.getWorld() == null) {
            this.forceComplete();
            return;
        }
        Vector vecA = DungeonLoader.parseVector(pathAStr);
        Vector vecB = DungeonLoader.parseVector(pathBStr);

        this.locA = new Location(game.getWorld(), vecA.getX() + 0.5, vecA.getY(), vecA.getZ() + 0.5);
        this.locB = new Location(game.getWorld(), vecB.getX() + 0.5, vecB.getY(), vecB.getZ() + 0.5);
    }

    @Override
    public void onTick(DungeonGame game) {
        if (completed || locA == null || locB == null) return;

        if (game.getWorld().getTime() % 10 == 0) {
            game.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, locA, 10, 0.5, 1, 0.5, 0);
            game.getWorld().spawnParticle(Particle.FLAME, locB, 10, 0.5, 1, 0.5, 0);
        }

        for (Player p : game.getParticipants()) {
            if (p.isOnline() && !p.isDead()) {
                if (p.getLocation().distanceSquared(locA) <= (radius * radius)) {
                    jumpToStage(game, stageA);
                    return;
                } else if (p.getLocation().distanceSquared(locB) <= (radius * radius)) {
                    jumpToStage(game, stageB);
                    return;
                }
            }
        }
    }

    /**
     * Utilizes Core's native jumpToStage method to transition paths.
     */
    private void jumpToStage(DungeonGame game, int targetStage) {
        try {
            game.jumpToStage(targetStage);

            this.forceComplete();

            game.broadcastMessage("action.branch_path_chosen", "<stage>", String.valueOf(targetStage));
        } catch (Exception e) {
            SinceDungeonPremium.getInstance().getLogger().severe("Failed to execute BranchingPath natively: " + e.getMessage());
            this.forceComplete();
        }
    }

    @Override
    public String getObjectiveText() {
        return SinceDungeon.getPlugin().getLanguageManager().getString("objective.branching_path");
    }
}