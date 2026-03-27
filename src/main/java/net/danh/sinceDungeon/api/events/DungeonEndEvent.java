package net.danh.sinceDungeon.api.events;

import net.danh.sinceDungeon.manager.DungeonGame;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jspecify.annotations.NonNull;

/**
 * Event called whenever a DungeonGame session is completely terminated and about to be deleted.
 * This fires regardless of whether the players won, lost, or the server forced a shutdown.
 * Highly useful for external plugins to clean up memory caches.
 */
public class DungeonEndEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final DungeonGame game;
    private final EndReason reason;

    /**
     * Constructs a new DungeonEndEvent.
     *
     * @param game   The dungeon game instance being terminated.
     * @param reason The reason for the termination.
     */
    public DungeonEndEvent(DungeonGame game, EndReason reason) {
        this.game = game;
        this.reason = reason;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    /**
     * Gets the dungeon game instance that is ending.
     *
     * @return The dungeon game.
     */
    public DungeonGame getGame() {
        return game;
    }

    /**
     * Gets the reason why this dungeon is ending.
     *
     * @return The end reason.
     */
    public EndReason getReason() {
        return reason;
    }

    @Override
    public @NonNull HandlerList getHandlers() {
        return HANDLERS;
    }

    /**
     * Enum representing the circumstances under which a dungeon session ended.
     */
    public enum EndReason {
        /**
         * The dungeon was successfully completed.
         */
        CLEARED,
        /**
         * The players failed the dungeon (e.g., all died or ran out of time).
         */
        FAILED,
        /**
         * The dungeon was forcefully stopped via command, API, or server shutdown.
         */
        FORCE_STOPPED
    }
}