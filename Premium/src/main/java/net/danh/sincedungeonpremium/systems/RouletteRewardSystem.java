package net.danh.sincedungeonpremium.systems;

import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.api.interfaces.RewardSystem;
import net.danh.sinceDungeon.guis.reward.RewardGUI;
import net.danh.sinceDungeon.guis.reward.RewardSession;
import net.danh.sinceDungeon.guis.reward.RewardSessionManager;
import net.danh.sinceDungeon.models.DungeonTemplate;
import net.danh.sincedungeonpremium.SinceDungeonPremium;
import net.danh.sincedungeonpremium.managers.RouletteManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

/**
 * Premium Implementation of the RewardSystem interface.
 * Forces the players to utilize the Roulette Spin GUI instead of the standard chest GUI.
 */
public class RouletteRewardSystem implements RewardSystem {

    private final SinceDungeonPremium plugin;
    private RouletteManager rouletteManager;
    private RewardGUI fallbackRewardGUI;

    public RouletteRewardSystem(SinceDungeonPremium plugin) {
        this.plugin = plugin;
    }

    @Override
    public void initialize() {
        RewardSessionManager.startCleanupTask(SinceDungeon.getPlugin());
        rouletteManager = new RouletteManager(plugin);
        fallbackRewardGUI = new RewardGUI(SinceDungeon.getPlugin());
        Bukkit.getPluginManager().registerEvents(fallbackRewardGUI, SinceDungeon.getPlugin());
    }

    @Override
    public void cleanup() {
        RewardSessionManager.clearAll();
        if (rouletteManager != null) {
            HandlerList.unregisterAll(rouletteManager);
        }
        if (fallbackRewardGUI != null) {
            HandlerList.unregisterAll(fallbackRewardGUI);
        }
    }

    @Override
    public void distributeRewards(Player player, DungeonTemplate template, int rewardAmount) {
        boolean useRoulette = plugin.getFileManager().getConfig().getBoolean("roulette.enabled", false);

        if (!useRoulette) {
            // Fallback to standard core logic if disabled but system is actively registered
            RewardSession session = new RewardSession(rewardAmount, template);
            RewardSessionManager.addSession(player, session);
            fallbackRewardGUI.openRewardGUI(player, rewardAmount, template);
            return;
        }

        RewardSession session = new RewardSession(rewardAmount, template);
        RewardSessionManager.addSession(player, session);
        rouletteManager.openRoulette(player, session);
    }

    @Override
    public void forceClaimPending(Player player) {
        RewardSession session = RewardSessionManager.getSession(player);
        if (session != null && session.getChestCount() > 0) {
            // Roulette forces a visual spin. For offline/abrupt claims, we fallback to the instant generic auto-claimer.
            fallbackRewardGUI.forceClaimAll(player, session);
            RewardSessionManager.removeSession(player);
        }
    }
}
