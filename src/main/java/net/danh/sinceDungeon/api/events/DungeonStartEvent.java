package net.danh.sinceDungeon.api.events;

import net.danh.sinceDungeon.manager.DungeonTemplate;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jspecify.annotations.NonNull;

import java.util.Set;

/**
 * Event called right before a dungeon instance is generated and players are teleported.
 */
public class DungeonStartEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player initiator;
    private final DungeonTemplate template;
    private final Set<Player> participants;
    private boolean isCancelled = false;

    /**
     * Constructs a new DungeonStartEvent.
     *
     * @param initiator    The player who initiated the dungeon (or Party Leader).
     * @param template     The template configuration of the dungeon.
     * @param participants The mutable set of players who will be pulled into the dungeon.
     */
    public DungeonStartEvent(Player initiator, DungeonTemplate template, Set<Player> participants) {
        this.initiator = initiator;
        this.template = template;
        this.participants = participants;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    /**
     * Gets the player who triggered the start of the dungeon.
     *
     * @return The initiating player.
     */
    public Player getInitiator() {
        return initiator;
    }

    /**
     * Gets the dungeon template configuration.
     *
     * @return The dungeon template.
     */
    public DungeonTemplate getTemplate() {
        return template;
    }

    /**
     * Gets the mutable set of players who will enter the dungeon.
     * Third-party plugins can add or remove players from this set before the dungeon starts.
     * If the set becomes empty, the dungeon generation will be aborted safely.
     *
     * @return The set of participating players.
     */
    public Set<Player> getParticipants() {
        return participants;
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
    public @NonNull HandlerList getHandlers() {
        return HANDLERS;
    }
}