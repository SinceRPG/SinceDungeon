package net.danh.sincedungeonpremium.actions;

import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.actions.DungeonAction;
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
 * Updates the dungeon's respawn point so players do not have to walk
 * from the very beginning if they die during later stages.
 */
public class CheckpointAction extends DungeonAction {

    private final String locationStr;
    private final String soundStr;
    private final String particleStr;

    public CheckpointAction(String locationStr, String soundStr, String particleStr) {
        this.locationStr = locationStr;
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
        Location loc = new Location(game.getWorld(), vec.getBlockX() + 0.5, vec.getBlockY() + 1, vec.getBlockZ() + 0.5);

        // Update the world's spawn location so Core's respawn logic automatically uses it
        game.getWorld().setSpawnLocation(loc);

        // Visual and Audio Feedback
        Sound sound = SoundUtils.getSound(soundStr);
        if (sound != null) {
            for (Player p : game.getParticipants()) {
                p.playSound(p.getLocation(), sound, 1.0f, 1.0f);
            }
        }

        try {
            Particle particle = Particle.valueOf(particleStr.toUpperCase());
            game.getWorld().spawnParticle(particle, loc, 50, 0.5, 1, 0.5, 0.1);
        } catch (IllegalArgumentException ignored) {
            // Failsafe if particle string is invalid
        }

        this.forceComplete();
    }

    @Override
    public String getObjectiveText() {
        return SinceDungeon.getPlugin().getLanguageManager().getString("objective.save_checkpoint", "Checkpoint Reached!");
    }
}