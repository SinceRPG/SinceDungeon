package net.danh.sinceDungeon.reward;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Global cache storing active reward sessions mapped by player UUID.
 * Upgraded to ConcurrentHashMap to prevent thread-safety issues during asynchronous events or rapid inventory clicks.
 */
public class RewardSessionManager {
    // VÁ LỖI AN TOÀN LUỒNG: Sử dụng ConcurrentHashMap thay vì HashMap
    private static final Map<UUID, RewardSession> sessions = new ConcurrentHashMap<>();

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
    }

    public static Map<UUID, RewardSession> getSessions() {
        return sessions;
    }
}