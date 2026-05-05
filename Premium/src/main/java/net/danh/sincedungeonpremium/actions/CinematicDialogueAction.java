package net.danh.sincedungeonpremium.actions;

import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.actions.DungeonAction;
import net.danh.sinceDungeon.actions.Tickable;
import net.danh.sinceDungeon.models.DungeonGame;
import net.danh.sinceDungeon.utils.ColorUtils;
import net.danh.sinceDungeon.utils.SoundUtils;
import net.kyori.adventure.title.Title;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.List;

/**
 * Premium Action: Cinematic Dialogue
 * Creates immersive RPG storytelling sequences.
 * Plays synchronized Titles, Chat text, and Sound Effects sequentially with configurable tick delays.
 */
public class CinematicDialogueAction extends DungeonAction implements Tickable {

    private final List<String> frames;
    private int currentFrame = 0;
    private int waitTicks = 0;

    public CinematicDialogueAction(List<String> frames) {
        this.frames = frames;
    }

    @Override
    public void start(DungeonGame game) {
        if (frames == null || frames.isEmpty()) {
            forceComplete();
        }
    }

    @Override
    public void onTick(DungeonGame game) {
        if (completed) return;

        if (waitTicks > 0) {
            waitTicks--;
            return;
        }

        if (currentFrame >= frames.size()) {
            forceComplete();
            return;
        }

        // Parse frame format: delay;title;subtitle;chat;sound
        String frameData = frames.get(currentFrame);
        String[] parts = frameData.split(";", -1);

        int delay = 40;
        String title = "";
        String subtitle = "";
        String chat = "";
        String soundStr = "";

        try {
            if (parts.length > 0 && !parts[0].isEmpty()) delay = Integer.parseInt(parts[0].trim());
        } catch (NumberFormatException ignored) {}

        if (parts.length > 1) title = parts[1];
        if (parts.length > 2) subtitle = parts[2];
        if (parts.length > 3) chat = parts[3];
        if (parts.length > 4) soundStr = parts[4];

        waitTicks = delay;

        Sound sound = SoundUtils.getSound(soundStr);
        Title objTitle = null;

        if (!title.isEmpty() || !subtitle.isEmpty()) {
            Title.Times times = Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(Math.max(1, delay / 20)), Duration.ofMillis(500));
            objTitle = Title.title(ColorUtils.parse(title), ColorUtils.parse(subtitle), times);
        }

        for (Player p : game.getParticipants()) {
            if (p.isOnline()) {
                if (objTitle != null) p.showTitle(objTitle);
                if (!chat.isEmpty()) p.sendMessage(ColorUtils.parseWithPrefix(chat));
                if (sound != null) p.playSound(p.getLocation(), sound, 1f, 1f);
            }
        }

        currentFrame++;
    }

    @Override
    public String getObjectiveText() {
        return SinceDungeon.getPlugin().getLanguageManager().getString("objective.cinematic_dialogue");
    }
}