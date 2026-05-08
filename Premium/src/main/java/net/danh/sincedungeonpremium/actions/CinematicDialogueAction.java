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
import java.util.ArrayList;
import java.util.List;

/**
 * Premium Action: Cinematic Dialogue
 * Creates immersive RPG storytelling sequences.
 * Plays synchronized Titles, Chat text, and Sound Effects sequentially with configurable tick delays.
 * Optimized: Pre-parses all frame string data into memory during start() to eliminate JIT String.split overhead during tick loops.
 */
public class CinematicDialogueAction extends DungeonAction implements Tickable {

    private final List<String> rawFrames;
    private final List<FrameData> parsedFrames = new ArrayList<>();

    private int currentFrame = 0;
    private int waitTicks = 0;

    public CinematicDialogueAction(List<String> rawFrames) {
        this.rawFrames = rawFrames;
    }

    @Override
    public void start(DungeonGame game) {
        if (rawFrames == null || rawFrames.isEmpty()) {
            forceComplete();
            return;
        }

        // JIT Optimization: Parse all strings once instead of slicing them every server tick
        for (String frameData : rawFrames) {
            String[] parts = frameData.split(";", -1);
            int delay = 40;

            try {
                if (parts.length > 0 && !parts[0].isEmpty()) delay = Integer.parseInt(parts[0].trim());
            } catch (NumberFormatException ignored) {
            }

            String title = parts.length > 1 ? parts[1] : "";
            String subtitle = parts.length > 2 ? parts[2] : "";
            String chat = parts.length > 3 ? parts[3] : "";
            String soundStr = parts.length > 4 ? parts[4] : "";

            Sound sound = SoundUtils.getSound(soundStr);
            Title objTitle = null;

            if (!title.isEmpty() || !subtitle.isEmpty()) {
                Title.Times times = Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(Math.max(1, delay / 20)), Duration.ofMillis(500));
                objTitle = Title.title(ColorUtils.parse(title), ColorUtils.parse(subtitle), times);
            }

            parsedFrames.add(new FrameData(delay, objTitle, chat, sound));
        }
    }

    @Override
    public void onTick(DungeonGame game) {
        if (completed) return;

        if (waitTicks > 0) {
            waitTicks--;
            return;
        }

        if (currentFrame >= parsedFrames.size()) {
            forceComplete();
            return;
        }

        FrameData frame = parsedFrames.get(currentFrame);
        waitTicks = frame.delay;

        for (Player p : game.getParticipants()) {
            if (p.isOnline()) {
                if (frame.title != null) p.showTitle(frame.title);
                if (!frame.chat.isEmpty()) p.sendMessage(ColorUtils.parseWithPrefix(frame.chat));
                if (frame.sound != null) p.playSound(p.getLocation(), frame.sound, 1f, 1f);
            }
        }

        currentFrame++;
    }

    @Override
    public String getObjectiveText() {
        return SinceDungeon.getPlugin().getLanguageManager().getString("objective.cinematic_dialogue");
    }

    private record FrameData(int delay, Title title, String chat, Sound sound) {
    }
}