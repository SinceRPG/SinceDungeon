package net.danh.sinceDungeon.guis.reward;

import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.utils.ColorUtils;
import net.danh.sinceDungeon.utils.SchedulerCompat;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Global cache storing active reward sessions mapped by player UUID.
 * Upgraded to ConcurrentHashMap to prevent thread-safety issues during asynchronous events or rapid inventory clicks.
 * Includes automated Garbage Collection for orphaned sessions.
 */
public class RewardSessionManager {
    private static final Map<UUID, RewardSession> sessions = new ConcurrentHashMap<>();
    private static SchedulerCompat.TaskHandle cleanupTask;

    /**
     * Starts the automated garbage collection task to clear orphaned or expired reward sessions.
     * Expiration time is dynamically fetched from config.yml instead of being hardcoded to 5 minutes.
     *
     * @param plugin The main plugin instance.
     */
    public static void startCleanupTask(SinceDungeon plugin) {
        if (cleanupTask != null && !cleanupTask.isCancelled()) {
            cleanupTask.cancel();
        }

        cleanupTask = SchedulerCompat.runGlobalTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            RewardGUI gui = new RewardGUI(plugin);

            int expireSeconds = plugin.getConfigFile().getInt("reward.session-expire-seconds", 300);
            long expireMillis = expireSeconds * 1000L;

            for (Map.Entry<UUID, RewardSession> entry : sessions.entrySet()) {
                if (now - entry.getValue().getCreationTime() > expireMillis) {
                    Player p = Bukkit.getPlayer(entry.getKey());
                    if (p != null && p.isOnline()) {
                        gui.forceClaimAll(p, entry.getValue());
                        p.sendMessage(ColorUtils.parseWithPrefix(
                                plugin.getLanguageManager().getString("reward.messages.auto_claimed_expired", "<yellow>Your rewards were auto-claimed due to timeout.")
                        ));
                    }
                    sessions.remove(entry.getKey());
                }
            }
        }, 1200L, 1200L);
    }

    public static void addSession(Player p, RewardSession session) {
        sessions.put(p.getUniqueId(), session);
    }

    public static RewardSession getSession(Player p) {
        return sessions.get(p.getUniqueId());
    }

    public static void removeSession(Player p) {
        sessions.remove(p.getUniqueId());
    }

    public static void clearAll() {
        sessions.clear();
        if (cleanupTask != null && !cleanupTask.isCancelled()) {
            cleanupTask.cancel();
        }
    }

    public static Map<UUID, RewardSession> getSessions() {
        return sessions;
    }
}
