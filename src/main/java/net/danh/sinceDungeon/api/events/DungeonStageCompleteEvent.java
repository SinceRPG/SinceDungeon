package net.danh.sinceDungeon.api.events;

import net.danh.sinceDungeon.manager.DungeonGame;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class DungeonStageCompleteEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final DungeonGame game;
    private final int stageIndex;

    public DungeonStageCompleteEvent(DungeonGame game, int stageIndex) {
        this.game = game;
        this.stageIndex = stageIndex;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    public DungeonGame getGame() {
        return game;
    }

    public int getStageIndex() {
        return stageIndex;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }
}