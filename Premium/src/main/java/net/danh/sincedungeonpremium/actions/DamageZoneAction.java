package net.danh.sincedungeonpremium.actions;

import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.actions.DungeonAction;
import net.danh.sinceDungeon.actions.Tickable;
import net.danh.sinceDungeon.managers.DungeonLoader;
import net.danh.sinceDungeon.models.DungeonGame;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Premium Action: Damage Zone
 * Creates a hazardous area (e.g. poison gas, fire traps) that damages
 * players standing inside it on a recurring interval until it expires.
 */
public class DamageZoneAction extends DungeonAction implements Tickable {

    private final String locationStr;
    private final double radius;
    private final double damage;
    private final int damageInterval;
    private final int durationTicks;
    private final String particleStr;

    private Location centerLoc;
    private int ticksElapsed = 0;

    public DamageZoneAction(String locationStr, double radius, double damage, int damageInterval, int durationTicks, String particleStr) {
        this.locationStr = locationStr;
        this.radius = radius;
        this.damage = damage;
        this.damageInterval = damageInterval;
        this.durationTicks = durationTicks;
        this.particleStr = particleStr;
    }

    @Override
    public void start(DungeonGame game) {
        if (game.getWorld() == null) {
            this.forceComplete();
            return;
        }

        Vector vec = DungeonLoader.parseVector(locationStr);
        this.centerLoc = new Location(game.getWorld(), vec.getBlockX() + 0.5, vec.getBlockY(), vec.getBlockZ() + 0.5);
    }

    @Override
    public void onTick(DungeonGame game) {
        if (completed || centerLoc == null) return;
        ticksElapsed++;

        if (ticksElapsed >= durationTicks) {
            this.forceComplete();
            return;
        }

        // Draw the hazard particles constantly
        if (ticksElapsed % 5 == 0) {
            try {
                Particle pType = Particle.valueOf(particleStr.toUpperCase());
                double r = Math.max(1.0, radius);
                for (int i = 0; i < 360; i += 30) {
                    double angle = i * Math.PI / 180;
                    double x = r * Math.cos(angle);
                    double z = r * Math.sin(angle);
                    centerLoc.getWorld().spawnParticle(pType, centerLoc.clone().add(x, 0.5, z), 2, 0.2, 0.5, 0.2, 0);
                }
                // Fill the center slightly
                centerLoc.getWorld().spawnParticle(pType, centerLoc.clone().add(0, 0.5, 0), 10, radius / 2, 0.5, radius / 2, 0);
            } catch (Exception ignored) {
            }
        }

        // Deal damage on interval
        if (ticksElapsed % damageInterval == 0) {
            for (Player p : game.getParticipants()) {
                if (p.isOnline() && !p.isDead() && p.getLocation().distanceSquared(centerLoc) <= (radius * radius)) {
                    p.damage(damage);
                }
            }
        }
    }

    @Override
    public String getObjectiveText() {
        return SinceDungeon.getPlugin().getLanguageManager().getString("objective.damage_zone", "<dark_red>Survive the hazard!");
    }
}