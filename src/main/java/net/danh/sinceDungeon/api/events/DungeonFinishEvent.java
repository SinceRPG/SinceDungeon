package net.danh.sinceDungeon.api.events;

import net.danh.sinceDungeon.manager.DungeonGame;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class DungeonFinishEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final DungeonGame game;
    private final int completionTimeSeconds;
    private int chestCount;

    public DungeonFinishEvent(DungeonGame game, int completionTimeSeconds, int chestCount) {
        this.game = game;
        this.completionTimeSeconds = completionTimeSeconds;
        this.chestCount = chestCount;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    public DungeonGame getGame() {
        return game;
    }

    public int getCompletionTimeSeconds() {
        return completionTimeSeconds;
    }

    public int getChestCount() {
        return chestCount;
    }

    public void setChestCount(int chestCount) {
        this.chestCount = Math.max(0, chestCount);
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }
}