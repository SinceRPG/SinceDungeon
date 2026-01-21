package net.danh.sinceDungeon.actions;

import net.danh.sinceDungeon.manager.DungeonGame;
import org.bukkit.event.Event;

public abstract class DungeonAction {
    protected boolean completed = false;

    public abstract void start(DungeonGame game);

    public void onEvent(DungeonGame game, Event event) {
    }

    public boolean isCompleted() {
        return completed;
    }
}