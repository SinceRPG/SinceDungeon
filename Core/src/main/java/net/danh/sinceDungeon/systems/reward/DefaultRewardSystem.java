package net.danh.sinceDungeon.systems.reward;

import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.api.interfaces.RewardSystem;
import net.danh.sinceDungeon.guis.reward.RewardGUI;
import net.danh.sinceDungeon.guis.reward.RewardSession;
import net.danh.sinceDungeon.guis.reward.RewardSessionManager;
import net.danh.sinceDungeon.models.DungeonTemplate;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

/**
 * The native implementation of the RewardSystem.
 * Provides the classic multi-chest GUI experience.
 */
public class DefaultRewardSystem implements RewardSystem {

    private final SinceDungeon plugin;
    private RewardGUI rewardGUI;

    public DefaultRewardSystem(SinceDungeon plugin) {
        this.plugin = plugin;
    }

    @Override
    public void initialize() {
        RewardSessionManager.startCleanupTask(plugin);
        rewardGUI = new RewardGUI(plugin);
        Bukkit.getPluginManager().registerEvents(rewardGUI, plugin);
    }

    @Override
    public void cleanup() {
        RewardSessionManager.clearAll();
        if (rewardGUI != null) {
            HandlerList.unregisterAll(rewardGUI);
        }
    }

    @Override
    public void distributeRewards(Player player, DungeonTemplate template, int rewardAmount) {
        if (rewardAmount > 0) {
            rewardGUI.openRewardGUI(player, rewardAmount, template);
        }
    }

    @Override
    public void forceClaimPending(Player player) {
        RewardSession session = RewardSessionManager.getSession(player);
        if (session != null && session.getChestCount() > 0) {
            if (rewardGUI == null) rewardGUI = new RewardGUI(plugin);
            rewardGUI.forceClaimAll(player, session);
            RewardSessionManager.removeSession(player);
        }
    }
}