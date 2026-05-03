package net.danh.sinceDungeon.api.events;

import net.danh.sinceDungeon.models.DungeonGame;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jspecify.annotations.NonNull;

/**
 * Event called when a dungeon game is completely finished.
 */
public class DungeonFinishEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final DungeonGame game;
    private final int completionTimeSeconds;
    private int chestCount;

    /**
     * Constructs a new DungeonFinishEvent.
     *
     * @param game                  The dungeon game instance.
     * @param completionTimeSeconds The total time taken in seconds.
     * @param chestCount            The amount of reward chests granted.
     */
    public DungeonFinishEvent(DungeonGame game, int completionTimeSeconds, int chestCount) {
        this.game = game;
        this.completionTimeSeconds = completionTimeSeconds;
        this.chestCount = chestCount;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    /**
     * Gets the dungeon game instance.
     *
     * @return The dungeon game.
     */
    public DungeonGame getGame() {
        return game;
    }

    /**
     * Gets the total completion time in seconds.
     *
     * @return The completion time.
     */
    public int getCompletionTimeSeconds() {
        return completionTimeSeconds;
    }

    /**
     * Gets the amount of reward chests the player will receive.
     *
     * @return The chest count.
     */
    public int getChestCount() {
        return chestCount;
    }

    /**
     * Modifies the amount of reward chests the player will receive.
     *
     * @param chestCount The new chest count.
     */
    public void setChestCount(int chestCount) {
        this.chestCount = Math.max(0, chestCount);
    }

    @Override
    public @NonNull HandlerList getHandlers() {
        return HANDLERS;
    }
}