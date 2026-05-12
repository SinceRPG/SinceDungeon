package net.danh.sinceDungeon.actions.impl;

import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.actions.DungeonAction;
import net.danh.sinceDungeon.actions.Tickable;
import net.danh.sinceDungeon.models.DungeonGame;
import net.danh.sinceDungeon.utils.MathCache;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Objective mandating players to step into a predefined circular radius.
 * Periodically renders border markers to indicate the checkpoint threshold.
 */
public class ReachLocationAction extends DungeonAction implements Tickable {
    private final Vector target;
    private final double radiusSq;
    private Location centerLoc;
    private int ticks = 0;

    private Particle idleParticle;
    private Particle completeParticle;

    public ReachLocationAction(Vector target, double radius) {
        this.target = target;
        this.radiusSq = radius * radius;
    }

    @Override
    public String getObjectiveText() {
        return SinceDungeon.getPlugin().getLanguageManager().getString("objective.reach_location", "<green>Move to the designated coordinates");
    }

    @Override
    public void start(DungeonGame game) {
        this.centerLoc = game.resolveLocation(target, 0.5, 0.5, 0.5);

        // JIT Optimization: Cache particle config lookup
        String pName = SinceDungeon.getPlugin().getConfigFile().getString("particles.reach_location_idle", "HAPPY_VILLAGER");
        try {
            this.idleParticle = Particle.valueOf(pName.toUpperCase());
        } catch (Exception ignored) {
            this.idleParticle = Particle.HAPPY_VILLAGER;
        }

        String pComplete = SinceDungeon.getPlugin().getConfigFile().getString("particles.reach_location_complete", "TOTEM_OF_UNDYING");
        try {
            this.completeParticle = Particle.valueOf(pComplete.toUpperCase());
        } catch (Exception ignored) {
            this.completeParticle = Particle.TOTEM_OF_UNDYING;
        }

        game.sendActionMessage(this, "init", "action.reach_start", "<x>", String.valueOf(target.getBlockX()), "<z>", String.valueOf(target.getBlockZ()), "<y>", String.valueOf(target.getBlockY()));
    }

    @Override
    public void onTick(DungeonGame game) {
        if (completed) return;
        ticks++;

        if (centerLoc != null && ticks % 5 == 0) {
            double r = Math.sqrt(radiusSq) > 0 ? Math.sqrt(radiusSq) : 1.5;
            double yOffset = Math.sin(ticks * 0.1) * 0.3;

            // JIT Optimization: Reusable Location pointer and cached Trig values
            Location particleLoc = centerLoc.clone();
            for (int i = 0; i < 360; i += 30) {
                double x = r * MathCache.COS[i];
                double z = r * MathCache.SIN[i];
                particleLoc.set(centerLoc.getX() + x, centerLoc.getY() + yOffset, centerLoc.getZ() + z);
                centerLoc.getWorld().spawnParticle(idleParticle, particleLoc, 1, 0, 0, 0, 0);
            }
        }

        for (Player p : game.getParticipants()) {
            Location loc = p.getLocation();
            if (loc.getWorld() != null && loc.getWorld().equals(game.getWorld()) && !p.isDead() && p.getGameMode() != GameMode.SPECTATOR) {
                double distSq2D = Math.pow(loc.getX() - centerLoc.getX(), 2) + Math.pow(loc.getZ() - centerLoc.getZ(), 2);
                double yDiff = loc.getY() - (centerLoc.getY() - 0.5);

                if (distSq2D <= radiusSq && yDiff >= -0.5 && yDiff <= 3.5) {
                    this.completed = true;
                    game.sendActionMessage(this, "complete", "action.reach_complete");
                    game.getWorld().spawnParticle(completeParticle, centerLoc.clone().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.1);
                    break;
                }
            }
        }
    }
}
