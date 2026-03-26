package net.danh.sinceDungeon.api.events;

import net.danh.sinceDungeon.reward.DungeonReward;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jspecify.annotations.NonNull;

/**
 * Event called when a player claims a reward from a dungeon chest.
 */
public class DungeonRewardClaimEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private DungeonReward reward;
    private boolean isCancelled = false;

    /**
     * Constructs a new DungeonRewardClaimEvent.
     *
     * @param player The player claiming the reward.
     * @param reward The reward object being claimed.
     */
    public DungeonRewardClaimEvent(Player player, DungeonReward reward) {
        this.player = player;
        this.reward = reward;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    /**
     * Gets the player claiming the reward.
     *
     * @return The player.
     */
    public Player getPlayer() {
        return player;
    }

    /**
     * Gets the reward being claimed.
     *
     * @return The dungeon reward.
     */
    public DungeonReward getReward() {
        return reward;
    }

    /**
     * Sets a new reward to be granted instead.
     *
     * @param reward The new reward.
     */
    public void setReward(DungeonReward reward) {
        this.reward = reward;
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