package net.danh.sincedungeonpremium.actions;

import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.actions.DungeonAction;
import net.danh.sinceDungeon.actions.Tickable;
import net.danh.sinceDungeon.managers.DungeonLoader;
import net.danh.sinceDungeon.models.DungeonGame;
import net.danh.sinceDungeon.utils.MathCache;
import net.danh.sinceDungeon.utils.SoundUtils;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.Locale;

/**
 * Premium Action: Save Checkpoint
 * Generates a physical ring boundary that updates the dungeon's respawn point
 * when crossed, preventing players from running from the very beginning.
 * Optimized: Uses a cached location pointer to eliminate JIT GC pressure.
 */
public class CheckpointAction extends DungeonAction implements Tickable {

    private final String locationStr;
    private final double radius;
    private final String soundStr;
    private final String particleStr;
    // JIT Optimization: Reusable Location pointer for particle rendering
    private final Location particleLoc = new Location(null, 0, 0, 0);
    private Location centerLoc;
    private int ticksElapsed = 0;
    private Particle cachedParticle;

    public CheckpointAction(String locationStr, double radius, String soundStr, String particleStr) {
        this.locationStr = locationStr;
        this.radius = radius;
        this.soundStr = soundStr;
        this.particleStr = particleStr;
    }

    @Override
    public void start(DungeonGame game) {
        if (game.getWorld() == null) {
            this.forceComplete();
            return;
        }
        Vector vec = DungeonLoader.parseVector(locationStr);
        this.centerLoc = new Location(game.getWorld(), vec.getBlockX() + 0.5, vec.getBlockY() + 1, vec.getBlockZ() + 0.5);
        this.particleLoc.setWorld(game.getWorld());

        try {
            this.cachedParticle = Particle.valueOf(particleStr.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            this.cachedParticle = Particle.TOTEM_OF_UNDYING;
        }

        game.sendActionMessage(this, "init", "action.checkpoint_start");
    }

    @Override
    public void onTick(DungeonGame game) {
        if (completed || centerLoc == null) return;
        ticksElapsed++;

        // Render the physical ring visually with pre-calculated Trig and mutable location vectors
        if (ticksElapsed % 5 == 0) {
            double r = Math.max(1.0, radius);

            for (int i = 0; i < 360; i += 30) {
                double x = r * MathCache.COS[i];
                double z = r * MathCache.SIN[i];
                particleLoc.set(centerLoc.getX() + x, centerLoc.getY() + 0.5, centerLoc.getZ() + z);
                centerLoc.getWorld().spawnParticle(cachedParticle, particleLoc, 1, 0, 0, 0, 0);
            }
        }

        for (Player p : game.getParticipants()) {
            if (p.isOnline() && !p.isDead() && p.getLocation().distanceSquared(centerLoc) <= (radius * radius)) {
                // Set the specific respawn location so Core handles it
                game.getWorld().setSpawnLocation(centerLoc);

                Sound sound = SoundUtils.getSound(soundStr);
                if (sound != null) {
                    for (Player participant : game.getParticipants()) {
                        participant.playSound(participant.getLocation(), sound, 1.0f, 1.0f);
                    }
                }

                game.getWorld().spawnParticle(cachedParticle, centerLoc, 50, 0.5, 1, 0.5, 0.1);

                game.sendActionMessage(this, "complete", "action.checkpoint_complete");
                this.forceComplete();
                return;
            }
        }
    }

    @Override
    public String getObjectiveText() {
        return SinceDungeon.getPlugin().getLanguageManager().getString("objective.save_checkpoint", "Reach the Checkpoint!");
    }
}