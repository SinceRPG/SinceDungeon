package net.danh.sinceDungeon.actions.impl;

import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.actions.DungeonAction;
import net.danh.sinceDungeon.actions.Tickable;
import net.danh.sinceDungeon.manager.DungeonGame;
import org.bukkit.util.Vector;

/**
 * Represents an action that requires the player to reach a specific coordinate.
 */
public class ReachLocationAction extends DungeonAction implements Tickable {
    private final Vector target;
    private final double radiusSq;

    /**
     * Constructs a new ReachLocationAction.
     *
     * @param target The target vector coordinate.
     * @param radius The radius within which the location is considered reached.
     */
    public ReachLocationAction(Vector target, double radius) {
        this.target = target;
        this.radiusSq = radius * radius;
    }

    @Override
    public String getObjectiveText() {
        return SinceDungeon.getPlugin().getMessagesFile().getString("objective.reach_location", "<green>Move to the designated coordinates");
    }

    @Override
    public void start(DungeonGame game) {
        game.sendMessage("action.reach_start", "<x>", String.valueOf(target.getBlockX()), "<z>", String.valueOf(target.getBlockZ()));
    }

    @Override
    public void onTick(DungeonGame game) {
        org.bukkit.Location loc = game.getPlayer().getLocation();
        if (loc.getWorld() != null && loc.getWorld().equals(game.getWorld())) {
            double distSq2D = Math.pow(loc.getX() - (target.getX() + 0.5), 2) +
                    Math.pow(loc.getZ() - (target.getZ() + 0.5), 2);
            double yDiff = Math.abs(loc.getY() - target.getY());

            if (distSq2D <= radiusSq && yDiff <= 2.5) {
                this.completed = true;
                game.sendMessage("action.reach_complete");
            }
        }
    }
}