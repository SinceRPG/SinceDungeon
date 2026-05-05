package net.danh.sinceDungeon.managers;

import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.api.interfaces.RewardSystem;

/**
 * Manages the active RewardSystem implementation for SinceDungeon.
 * Follows the Strategy Design Pattern.
 */
public class RewardManager {

    private final SinceDungeon plugin;
    private RewardSystem activeSystem;

    public RewardManager(SinceDungeon plugin) {
        this.plugin = plugin;
    }

    /**
     * Dynamically swaps the current reward system.
     * Automatically handles the cleanup of the old system and initialization of the new one.
     *
     * @param system The new RewardSystem implementation.
     */
    public void setRewardSystem(RewardSystem system) {
        if (this.activeSystem != null) {
            this.activeSystem.cleanup();
        }
        this.activeSystem = system;
        this.activeSystem.initialize();

        String logMsg = plugin.getLanguageManager().getString("admin.log.reward_system_set", "[API] Reward System overwritten by: <system>");
        plugin.getLogger().info(logMsg.replace("<system>", system.getClass().getSimpleName()));
    }

    /**
     * Retrieves the currently active reward system.
     *
     * @return The active RewardSystem instance.
     */
    public RewardSystem getRewardSystem() {
        return activeSystem;
    }
}