package net.danh.sincedungeonpremium.actions;

import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.actions.DungeonAction;
import net.danh.sinceDungeon.models.DungeonGame;
import net.danh.sinceDungeon.utils.SoundUtils;
import net.danh.sincedungeonpremium.SinceDungeonPremium;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * Premium Action: Play Sound
 * Triggers a global sound effect for all players in the dungeon.
 * Used for atmospheric queues, boss spawns, or solving puzzles.
 */
public class PlaySoundAction extends DungeonAction {

    private final String soundName;
    private final float volume;
    private final float pitch;

    public PlaySoundAction(String soundName, float volume, float pitch) {
        this.soundName = soundName;
        this.volume = volume;
        this.pitch = pitch;
    }

    @Override
    public void start(DungeonGame game) {
        Sound sound = SoundUtils.getSound(soundName);

        if (sound != null) {
            for (Player p : game.getParticipants()) {
                if (p.isOnline() && !p.isDead() && p.getWorld().equals(game.getWorld())) {
                    p.playSound(p.getLocation(), sound, volume, pitch);
                }
            }
        } else {
            String logMsg = SinceDungeonPremium.getInstance().getFileManager().getMessageRaw("log.play_sound_fail");
            SinceDungeon.getPlugin().getLogger().warning(logMsg.replace("<sound>", soundName));
        }

        forceComplete();
    }

    @Override
    public String getObjectiveText() {
        return SinceDungeon.getPlugin().getLanguageManager().getString("objective.play_sound");
    }
}