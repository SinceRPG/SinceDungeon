package net.danh.sinceDungeon.actions.impl;

import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.actions.DungeonAction;
import net.danh.sinceDungeon.actions.Tickable;
import net.danh.sinceDungeon.manager.DungeonGame;
import org.bukkit.Location;
import org.bukkit.Particle;
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
        this.centerLoc = new Location(game.getWorld(), target.getX() + 0.5, target.getY() + 0.1, target.getZ() + 0.5);
        game.sendMessage("action.reach_start", "<x>", String.valueOf(target.getBlockX()), "<z>", String.valueOf(target.getBlockZ()));
    }

    @Override
    public void onTick(DungeonGame game) {
        if (completed) return;

        // UX: Tạo vòng xoáy Particle (Hào quang) quanh điểm đến để người chơi dễ nhận diện
        ticks++;
        if (centerLoc != null && ticks % 5 == 0) {
            double r = Math.sqrt(radiusSq) > 0 ? Math.sqrt(radiusSq) : 1.5;
            for (int i = 0; i < 360; i += 30) {
                double angle = i * Math.PI / 180;
                double x = r * Math.cos(angle);
                double z = r * Math.sin(angle);
                centerLoc.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, centerLoc.clone().add(x, 0, z), 1, 0, 0, 0, 0);
            }
        }

        Location loc = game.getPlayer().getLocation();
        if (loc.getWorld() != null && loc.getWorld().equals(game.getWorld())) {
            double distSq2D = Math.pow(loc.getX() - (target.getX() + 0.5), 2) +
                    Math.pow(loc.getZ() - (target.getZ() + 0.5), 2);
            double yDiff = Math.abs(loc.getY() - target.getY());

            if (distSq2D <= radiusSq && yDiff <= 2.5) {
                this.completed = true;
                game.sendMessage("action.reach_complete");
                // Tung hoa giấy chúc mừng khi tới nơi
                game.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, centerLoc.clone().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.1);
            }
        }
    }
}