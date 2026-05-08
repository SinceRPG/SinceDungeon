package net.danh.sincedungeonpremium.actions;

import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.actions.DungeonAction;
import net.danh.sinceDungeon.actions.Tickable;
import net.danh.sinceDungeon.managers.DungeonLoader;
import net.danh.sinceDungeon.models.DungeonGame;
import net.danh.sinceDungeon.utils.MathCache;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.Locale;

/**
 * Premium Action: Damage Zone
 * Creates a hazardous area (e.g. poison gas, fire traps) that damages
 * players standing inside it on a recurring interval until it expires.
 * Optimized: Utilizes mutable Location pointers to prevent Garbage Collection spikes.
 */
public class DamageZoneAction extends DungeonAction implements Tickable {

    private final String locationStr;
    private final double radius;
    private final double damage;
    private final int damageInterval;
    private final int durationTicks;
    private final String particleStr;
    // JIT Optimization: Reusable object to avoid allocating 12 objects per tick
    private final Location pointerLoc = new Location(null, 0, 0, 0);
    private Location centerLoc;
    private int ticksElapsed = 0;
    private Particle cachedParticle;

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
        this.pointerLoc.setWorld(game.getWorld());

        try {
            this.cachedParticle = Particle.valueOf(particleStr.toUpperCase(Locale.ROOT));
        } catch (Exception ignored) {
            this.cachedParticle = Particle.CAMPFIRE_COSY_SMOKE;
        }
    }

    @Override
    public void onTick(DungeonGame game) {
        if (completed || centerLoc == null) return;
        ticksElapsed++;

        if (ticksElapsed >= durationTicks) {
            this.forceComplete();
            return;
        }

        if (ticksElapsed % 5 == 0) {
            double r = Math.max(1.0, radius);

            for (int i = 0; i < 360; i += 30) {
                double x = r * MathCache.COS[i];
                double z = r * MathCache.SIN[i];
                pointerLoc.set(centerLoc.getX() + x, centerLoc.getY() + 0.5, centerLoc.getZ() + z);
                centerLoc.getWorld().spawnParticle(cachedParticle, pointerLoc, 2, 0.2, 0.5, 0.2, 0);
            }

            pointerLoc.set(centerLoc.getX(), centerLoc.getY() + 0.5, centerLoc.getZ());
            centerLoc.getWorld().spawnParticle(cachedParticle, pointerLoc, 10, radius / 2, 0.5, radius / 2, 0);
        }

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