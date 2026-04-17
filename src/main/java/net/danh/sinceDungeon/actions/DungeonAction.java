package net.danh.sinceDungeon.actions;

import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.manager.DungeonGame;
import net.danh.sinceDungeon.utils.ColorUtils;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Base abstract class for all dungeon actions/objectives.
 */
public abstract class DungeonAction {
    public boolean completed = false;
    private List<String> startMessages = new ArrayList<>();
    private Map<String, Boolean> notifications = new java.util.HashMap<>();
    private String actionType = "UNKNOWN";

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

    public void cleanup(DungeonGame game) {
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

    public String getActionType() {
        return actionType;
    }

    /**
     * Set internal action type (like SPAWN_WAVE, LOOT_CHEST) for logging and toggles
     *
     * @param actionType The exact type ID of this action.
     */
    public void setActionType(String actionType) {
        this.actionType = actionType;
    }

    public Map<String, Boolean> getNotifications() {
        return notifications;
    }

    public void setNotifications(Map<String, Boolean> notifications) {
        this.notifications = notifications != null ? notifications : new java.util.HashMap<>();
    }

    /**
     * Announces the start messages to the player.
     *
     * @param game The current dungeon game.
     */
    public void announceStart(DungeonGame game) {
        if (startMessages == null || startMessages.isEmpty()) return;

        boolean canShow = SinceDungeon.getPlugin().getConfigFile().getBoolean("action-notifications." + actionType.toLowerCase() + ".custom_start", true);
        if (notifications.containsKey("custom_start")) {
            canShow = notifications.get("custom_start");
        }
        
        if (!canShow) return;

        for (String line : startMessages) {
            for (Player p : game.getParticipants()) {
                if (p.isOnline() && p.getWorld().equals(game.getWorld())) {
                    p.sendMessage(ColorUtils.parseWithPrefix(line));
                }
            }
        }
    }

    /**
     * Retrieves the objective text shown in the action bar.
     *
     * @return The formatted objective string.
     */
    public abstract String getObjectiveText();
}