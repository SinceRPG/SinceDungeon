package net.danh.sinceDungeon.reward;

import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Global cache storing active reward sessions mapped by player UUID.
 */
public class RewardSessionManager {
    private static final Map<UUID, RewardSession> sessions = new HashMap<>();

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