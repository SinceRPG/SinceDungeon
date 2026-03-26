package net.danh.sinceDungeon.api.events;

import net.danh.sinceDungeon.reward.DungeonReward;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class DungeonRewardClaimEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private DungeonReward reward;
    private boolean isCancelled = false;

    public DungeonRewardClaimEvent(Player player, DungeonReward reward) {
        this.player = player;
        this.reward = reward;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    public Player getPlayer() {
        return player;
    }

    public DungeonReward getReward() {
        return reward;
    }

    /**
     * Cho phép plugin khác thay đổi phần thưởng trước khi trao.
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
    public HandlerList getHandlers() {
        return HANDLERS;
    }
}