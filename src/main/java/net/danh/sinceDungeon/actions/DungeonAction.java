package net.danh.sinceDungeon.actions;

import net.danh.sinceDungeon.manager.DungeonGame;
import net.danh.sinceDungeon.utils.ColorUtils;
import org.bukkit.event.Event;

import java.util.ArrayList;
import java.util.List;

public abstract class DungeonAction {
    public boolean completed = false;
    private List<String> startMessages = new ArrayList<>();

    public abstract void start(DungeonGame game);

    public void onEvent(DungeonGame game, Event event) {
    }

    public boolean isCompleted() {
        return completed;
    }

    public void forceComplete() {
        this.completed = true;
    }

    public void setStartMessages(List<String> startMessages) {
        this.startMessages = startMessages;
    }

    public void announceStart(DungeonGame game) {
        if (startMessages == null || startMessages.isEmpty()) return;
        for (String line : startMessages) {
            game.getPlayer().sendMessage(ColorUtils.parse(line));
        }
    }

    public abstract String getObjectiveText();
}