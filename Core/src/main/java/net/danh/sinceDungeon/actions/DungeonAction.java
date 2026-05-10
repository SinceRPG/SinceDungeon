package net.danh.sinceDungeon.actions;

import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.models.DungeonGame;
import net.danh.sinceDungeon.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;

import java.util.*;

/**
 * Base abstract class for all dungeon actions/objectives.
 * Handles unified execution states, entity cleanup, and objective UI rendering.
 */
public abstract class DungeonAction {
    protected final Set<UUID> spawnedEntities = new HashSet<>();
    protected final Set<Entity> activeEntities = new HashSet<>();
    public boolean completed = false;
    private List<String> startMessages = new ArrayList<>();
    private Map<String, Boolean> notifications = new HashMap<>();
    private String actionType = "UNKNOWN";

    private int timeLimitSeconds = -1;
    private int timeLimitPenalty = 1;
    private long startTimeMillis = -1;

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
     * Cleans up entities spawned during this action.
     * OPTIMIZATION: Bypasses expensive Bukkit.getEntity(UUID) scans by utilizing direct memory references.
     *
     * @param game The current active dungeon game instance.
     */
    public void cleanup(DungeonGame game) {
        boolean autoClear = SinceDungeon.getPlugin().getConfigFile().getBoolean("settings.clear-remaining-mobs-on-action-complete", true);

        if (autoClear && !activeEntities.isEmpty()) {
            for (Entity entity : activeEntities) {
                if (entity != null && entity.isValid() && !entity.isDead()) {
                    entity.remove();
                }
            }
        }

        // Clear memory references to allow Garbage Collection
        activeEntities.clear();
        spawnedEntities.clear();
    }

    /**
     * Retrieves the set of entity UUIDs currently tracked by this action.
     *
     * @return The Set of tracked entity UUIDs.
     */
    public Set<UUID> getSpawnedEntities() {
        return spawnedEntities;
    }

    /**
     * Adds a newly spawned child entity to the tracking list.
     *
     * @param uuid         The UUID of the newly spawned child entity.
     * @param loc          The spawn location.
     * @param internalName The internal identifier name (if applicable).
     */
    public void trackChildEntity(UUID uuid, Location loc, String internalName) {
        this.spawnedEntities.add(uuid);
        Entity e = Bukkit.getEntity(uuid);
        if (e != null) {
            this.activeEntities.add(e);
        }
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
     * Set internal action type (like SPAWN_WAVE, LOOT_CHEST) for logging and toggles.
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
        this.notifications = notifications != null ? notifications : new HashMap<>();
    }

    public int getTimeLimitSeconds() {
        return timeLimitSeconds;
    }

    public void setTimeLimitSeconds(int timeLimitSeconds) {
        this.timeLimitSeconds = timeLimitSeconds;
    }

    public int getTimeLimitPenalty() {
        return timeLimitPenalty;
    }

    public void setTimeLimitPenalty(int timeLimitPenalty) {
        this.timeLimitPenalty = timeLimitPenalty;
    }

    public long getStartTimeMillis() {
        return startTimeMillis;
    }

    public void setStartTimeMillis(long startTimeMillis) {
        this.startTimeMillis = startTimeMillis;
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