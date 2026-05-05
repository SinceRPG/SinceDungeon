package net.danh.sinceDungeon.api.interfaces;

import net.danh.sinceDungeon.models.DungeonTemplate;
import org.bukkit.entity.Player;

/**
 * Interface defining the strict contract for dungeon reward distribution.
 * Implementing this allows third-party plugins to completely override and
 * customize how rewards are given (e.g., standard GUI, Roulette, Mailbox).
 */
public interface RewardSystem {

    /**
     * Called when the reward system is registered or the server starts.
     * Use this to register listeners and background tasks.
     */
    void initialize();

    /**
     * Called when the reward system is unregistered or the server shuts down.
     * Use this to unregister listeners, cancel tasks, and clean memory.
     */
    void cleanup();

    /**
     * Distributes the earned rewards to the player.
     *
     * @param player       The player receiving the rewards.
     * @param template     The completed dungeon template.
     * @param rewardAmount The amount of reward chests/spins earned.
     */
    void distributeRewards(Player player, DungeonTemplate template, int rewardAmount);

    /**
     * Forcefully resolves any pending or unclaimed rewards for a player.
     * Commonly triggered when a player disconnects unexpectedly.
     *
     * @param player The player to force claim for.
     */
    void forceClaimPending(Player player);
}