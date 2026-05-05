package net.danh.sinceDungeon.api.interfaces;

import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;

/**
 * Interface defining the strict contract for Party and Matchmaking systems.
 * Implementing this allows third-party plugins (e.g., MMOProfiles, Guilds, Parties)
 * to completely override the built-in party logic.
 */
public interface PartyProvider {

    void initialize();

    void cleanup();

    boolean hasParty(UUID playerId);

    boolean isLeader(UUID playerId);

    UUID getLeader(UUID playerId);

    Set<UUID> getMembers(UUID playerId);

    String getMemberName(UUID memberId);

    void sendPartyMessage(UUID playerId, String senderName, String message);

    void handleDisconnect(Player player);

    boolean isPartyChatEnabled(UUID playerId);

    void disbandParty(UUID leaderId);
}