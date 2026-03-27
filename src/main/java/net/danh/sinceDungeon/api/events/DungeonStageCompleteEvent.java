package net.danh.sinceDungeon.api.events;

import net.danh.sinceDungeon.manager.DungeonGame;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jspecify.annotations.NonNull;

/**
 * Event called when a specific stage inside a dungeon is completed.
 */
public class DungeonStageCompleteEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final DungeonGame game;
    private final int stageIndex;

    /**
     * Constructs a new DungeonStageCompleteEvent.
     *
     * @param game       The dungeon game instance.
     * @param stageIndex The index of the completed stage.
     */
    public DungeonStageCompleteEvent(DungeonGame game, int stageIndex) {
        this.game = game;
        this.stageIndex = stageIndex;
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
     * Gets the index of the completed stage.
     *
     * @return The stage index.
     */
    public int getStageIndex() {
        return stageIndex;
    }

    @Override
    public @NonNull HandlerList getHandlers() {
        return HANDLERS;
    }
}