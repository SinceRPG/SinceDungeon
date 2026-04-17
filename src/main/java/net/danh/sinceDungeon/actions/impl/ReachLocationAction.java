package net.danh.sinceDungeon.actions.impl;

import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.actions.DungeonAction;
import net.danh.sinceDungeon.actions.Tickable;
import net.danh.sinceDungeon.manager.DungeonGame;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public class ReachLocationAction extends DungeonAction implements Tickable {
    private final Vector target;
    private final double radiusSq;
    private Location centerLoc;
    private int ticks = 0;

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
        this.centerLoc = new Location(game.getWorld(), target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
        game.sendActionMessage(this, "init", "action.reach_start", "<x>", String.valueOf(target.getBlockX()), "<z>", String.valueOf(target.getBlockZ()), "<y>", String.valueOf(target.getBlockY()));
    }

    @Override
    public void onTick(DungeonGame game) {
        if (completed) return;

        ticks++;
        if (centerLoc != null && ticks % 5 == 0) {
            double r = Math.sqrt(radiusSq) > 0 ? Math.sqrt(radiusSq) : 1.5;
            double yOffset = Math.sin(ticks * 0.1) * 0.3;

            for (int i = 0; i < 360; i += 30) {
                double angle = i * Math.PI / 180;
                double x = r * Math.cos(angle);
                double z = r * Math.sin(angle);
                centerLoc.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, centerLoc.clone().add(x, yOffset, z), 1, 0, 0, 0, 0);
            }
        }

        for (Player p : game.getParticipants()) {
            Location loc = p.getLocation();
            if (loc.getWorld() != null && loc.getWorld().equals(game.getWorld()) && !p.isDead() && p.getGameMode() != GameMode.SPECTATOR) {
                double distSq2D = Math.pow(loc.getX() - (target.getX() + 0.5), 2) +
                        Math.pow(loc.getZ() - (target.getZ() + 0.5), 2);

                double yDiff = loc.getY() - target.getY();

                if (distSq2D <= radiusSq && yDiff >= -0.5 && yDiff <= 3.5) {
                    this.completed = true;
                    game.sendActionMessage(this, "complete", "action.reach_complete");
                    game.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, centerLoc.clone().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.1);
                    break;
                }
            }
        }
    }
}