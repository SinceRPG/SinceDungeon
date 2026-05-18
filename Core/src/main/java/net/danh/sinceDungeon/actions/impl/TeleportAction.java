package net.danh.sinceDungeon.actions.impl;

import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.actions.DungeonAction;
import net.danh.sinceDungeon.managers.DungeonLoader;
import net.danh.sinceDungeon.models.DungeonGame;
import net.danh.sinceDungeon.utils.SoundUtils;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Action to seamlessly teleport all participants to a specific location within the current dungeon.
 * Uses asynchronous chunk-safe teleportation.
 */
public class TeleportAction extends DungeonAction {

    private final String targetLocationStr;
    private final String soundStr;

    public TeleportAction(String targetLocationStr, String soundStr) {
        this.targetLocationStr = targetLocationStr;
        this.soundStr = soundStr;
    }

    @Override
    public void start(DungeonGame game) {
        if (game.getWorld() == null) {
            this.forceComplete();
            return;
        }

        Vector targetVec = DungeonLoader.parseVector(targetLocationStr);
        Location targetLoc = game.resolveLocation(targetVec, 0.5, 0, 0.5);
        Sound sound = SoundUtils.getSound(soundStr);

        for (Player p : game.getParticipants()) {
            if (p.isOnline() && !p.isDead()) {
                p.teleportAsync(targetLoc).thenAccept(success -> {
                    if (success && sound != null) {
                        p.playSound(targetLoc, sound, 1.0f, 1.0f);
                    }
                });
            }
        }

        // This is an instant execution action, so we mark it as complete immediately.
        this.forceComplete();
    }

    @Override
    public String getObjectiveText() {
        return SinceDungeon.getPlugin().getLanguageManager().getString("objective.teleport", "Teleporting...");
    }
}
