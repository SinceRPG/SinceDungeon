package net.danh.sincedungeonpremium.actions;

import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.actions.DungeonAction;
import net.danh.sinceDungeon.actions.Tickable;
import net.danh.sinceDungeon.managers.DungeonLoader;
import net.danh.sinceDungeon.models.DungeonGame;
import net.danh.sinceDungeon.utils.SoundUtils;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Premium Action: Save Checkpoint
 * Generates a physical ring boundary that updates the dungeon's respawn point
 * when crossed, preventing players from running from the very beginning.
 */
public class CheckpointAction extends DungeonAction implements Tickable {

    private final String locationStr;
    private final double radius;
    private final String soundStr;
    private final String particleStr;

    private Location centerLoc;
    private int ticksElapsed = 0;

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
        game.sendActionMessage(this, "init", "action.checkpoint_start");
    }

    @Override
    public void onTick(DungeonGame game) {
        if (completed || centerLoc == null) return;
        ticksElapsed++;

        // Render the physical ring visually
        if (ticksElapsed % 5 == 0) {
            try {
                Particle pType = Particle.valueOf(particleStr.toUpperCase());
                double r = Math.max(1.0, radius);
                for (int i = 0; i < 360; i += 30) {
                    double angle = i * Math.PI / 180;
                    double x = r * Math.cos(angle);
                    double z = r * Math.sin(angle);
                    centerLoc.getWorld().spawnParticle(pType, centerLoc.clone().add(x, 0.5, z), 1, 0, 0, 0, 0);
                }
            } catch (IllegalArgumentException ignored) {
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

                try {
                    Particle pType = Particle.valueOf(particleStr.toUpperCase());
                    game.getWorld().spawnParticle(pType, centerLoc, 50, 0.5, 1, 0.5, 0.1);
                } catch (IllegalArgumentException ignored) {
                }

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