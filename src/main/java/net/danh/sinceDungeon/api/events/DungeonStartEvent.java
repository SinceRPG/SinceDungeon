package net.danh.sinceDungeon.api.events;

import net.danh.sinceDungeon.manager.DungeonTemplate;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class DungeonStartEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final DungeonTemplate template;
    private boolean isCancelled = false;

    public DungeonStartEvent(Player player, DungeonTemplate template) {
        this.player = player;
        this.template = template;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    public Player getPlayer() {
        return player;
    }

    public DungeonTemplate getTemplate() {
        return template;
    }

    @Override
    public boolean isCancelled() {
        return isCancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.isCancelled = cancel;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }
}