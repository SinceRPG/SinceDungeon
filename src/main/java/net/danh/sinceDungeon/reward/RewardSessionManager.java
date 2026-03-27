package net.danh.sinceDungeon.reward;

import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

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
    private static BukkitTask cleanupTask;

    public static void startCleanupTask(SinceDungeon plugin) {
        cleanupTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            RewardGUI gui = new RewardGUI(plugin);

            for (Map.Entry<UUID, RewardSession> entry : sessions.entrySet()) {
                if (now - entry.getValue().getCreationTime() > 300000L) {
                    Player p = Bukkit.getPlayer(entry.getKey());
                    if (p != null && p.isOnline()) {
                        gui.forceClaimAll(p, entry.getValue());
                        p.sendMessage(ColorUtils.parseWithPrefix(plugin.getMessagesFile().getString("reward.messages.auto_claimed_expired", "<yellow>Phần thưởng Dungeon đã được tự động mở do hết thời gian chờ.")));
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