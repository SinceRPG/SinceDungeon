package net.danh.sinceDungeon.actions;

import net.danh.sinceDungeon.manager.DungeonGame;
import net.danh.sinceDungeon.utils.ColorUtils;
import org.bukkit.event.Event;

import java.util.ArrayList;
import java.util.List;

/**
 * Base abstract class for all dungeon actions/objectives.
 */
public abstract class DungeonAction {
    public boolean completed = false;
    private List<String> startMessages = new ArrayList<>();

    /**
     * Starts the action within the provided dungeon game instance.
     *
     * @param game The current dungeon game.
     */
    public abstract void start(DungeonGame game);

    /**
     * Handles specific Bukkit events relevant to the action.
     *
     * @param game  The current dungeon game.
     * @param event The triggered event.
     */
    public void onEvent(DungeonGame game, Event event) {
    }

    /**
     * Checks whether the action has been successfully completed.
     *
     * @return True if completed, false otherwise.
     */
    public boolean isCompleted() {
        return completed;
    }

    /**
     * Forces the action to complete immediately.
     */
    public void forceComplete() {
        this.completed = true;
    }

    /**
     * Sets the messages to be sent when this action starts.
     *
     * @param startMessages A list of messages.
     */
    public void setStartMessages(List<String> startMessages) {
        this.startMessages = startMessages;
    }

    /**
     * Announces the start messages to the player.
     *
     * @param game The current dungeon game.
     */
    public void announceStart(DungeonGame game) {
        if (startMessages == null || startMessages.isEmpty()) return;
        for (String line : startMessages) {
            game.getPlayer().sendMessage(ColorUtils.parse(line));
        }
    }

    /**
     * Retrieves the objective text shown in the action bar.
     *
     * @return The formatted objective string.
     */
    public abstract String getObjectiveText();
}