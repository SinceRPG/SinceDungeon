package net.danh.sinceDungeon.actions.impl;

import net.danh.sinceDungeon.actions.DungeonAction;
import net.danh.sinceDungeon.actions.Tickable;
import net.danh.sinceDungeon.manager.DungeonGame;
import org.bukkit.Location;
import org.bukkit.util.Vector;

public class ReachLocationAction extends DungeonAction implements Tickable {
    private final Vector target;
    private final double radiusSq;

    public ReachLocationAction(Vector target, double radius) {
        this.target = target;
        this.radiusSq = radius * radius;
    }

    @Override
    public void start(DungeonGame game) {
        game.sendMessage("action.reach_start", "<x>", String.valueOf(target.getBlockX()), "<z>", String.valueOf(target.getBlockZ()));
    }

    @Override
    public void onTick(DungeonGame game) {
        Location loc = game.getPlayer().getLocation();
        // Ensure player is in the correct world
        if (loc.getWorld() != null && loc.getWorld().equals(game.getWorld())) {
            double distSq = Math.pow(loc.getX() - target.getX(), 2) +
                    Math.pow(loc.getY() - target.getY(), 2) +
                    Math.pow(loc.getZ() - target.getZ(), 2);
            if (distSq <= radiusSq) {
                this.completed = true;
                game.sendMessage("action.reach_complete");
            }
        }
    }
}