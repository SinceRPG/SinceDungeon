// PartyManager.java
package net.danh.sinceDungeon.party;

import net.danh.sinceDungeon.SinceDungeon;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Thread-safe management system for Party lifecycles, invitations, and active sessions.
 * Implements passive expiry for invitations and distance-based evaluations.
 */
public class PartyManager {

    private final SinceDungeon plugin;
    private final Map<UUID, Party> activeParties = new ConcurrentHashMap<>();
    private final Map<UUID, Map<UUID, Long>> activeInvites = new ConcurrentHashMap<>();

    /**
     * Constructs the PartyManager and initializes the asynchronous cleanup loop.
     *
     * @param plugin The main plugin instance.
     */
    public PartyManager(SinceDungeon plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::purgeExpiredInvites, 1200L, 1200L);
    }

    /**
     * Creates a new party with the specified player as the leader.
     *
     * @param leader The player initiating the party.
     * @return The newly created Party instance, or null if the player is already in a party.
     */
    public Party createParty(Player leader) {
        if (getParty(leader.getUniqueId()) != null) return null;

        Party party = new Party(leader.getUniqueId());
        activeParties.put(leader.getUniqueId(), party);
        return party;
    }

    /**
     * Retrieves the party associated with a specific player UUID.
     *
     * @param uuid The UUID of the player.
     * @return The Party instance, or null if the player is unaffiliated.
     */
    public Party getParty(UUID uuid) {
        return activeParties.get(uuid);
    }

    /**
     * Safely disbands a party, removing all members and clearing it from memory.
     *
     * @param party The party to disband.
     */
    public void disbandParty(Party party) {
        if (party == null) return;
        party.getMembers().forEach(activeParties::remove);
    }

    /**
     * Issues an invitation from a party leader to a target player.
     *
     * @param leader The UUID of the party leader.
     * @param target The UUID of the player receiving the invitation.
     */
    public void invitePlayer(UUID leader, UUID target) {
        activeInvites.computeIfAbsent(target, k -> new ConcurrentHashMap<>())
                .put(leader, System.currentTimeMillis() + 60000L);
    }

    /**
     * Accepts a pending invitation, atomically transferring the player into the party.
     *
     * @param target The player accepting the invite.
     * @param leader The leader of the party they are joining.
     * @return True if successful, false if the invite is invalid or expired.
     */
    public boolean acceptInvite(Player target, UUID leader) {
        Map<UUID, Long> targetInvites = activeInvites.get(target.getUniqueId());
        if (targetInvites == null || !targetInvites.containsKey(leader)) return false;

        if (System.currentTimeMillis() > targetInvites.get(leader)) {
            targetInvites.remove(leader);
            return false;
        }

        Party party = getParty(leader);
        int maxMembers = plugin.getConfigFile().getInt("party.max-members", 4);
        if (party == null || party.getMembers().size() >= maxMembers) {
            return false;
        }

        party.addMember(target.getUniqueId());
        activeParties.put(target.getUniqueId(), party);
        targetInvites.remove(leader);
        return true;
    }

    /**
     * Handles leader election when the current leader disconnects or leaves.
     *
     * @param party The party requiring a new leader.
     */
    public void electNewLeader(Party party) {
        if (party == null || party.getMembers().size() <= 1) {
            disbandParty(party);
            return;
        }

        party.removeMember(party.getLeader());
        activeParties.remove(party.getLeader());

        UUID newLeader = party.getMembers().iterator().next();
        party.setLeader(newLeader);
        sendPartyMessage(party, "System", plugin.getMessagesFile().getString("party.new_leader").replace("<player>", Bukkit.getOfflinePlayer(newLeader).getName()));
    }

    /**
     * Retrieves all online members of a party within a specific radius of the leader.
     *
     * @param party  The party to evaluate.
     * @param radius The maximum distance (0 for map-wide).
     * @return A set of eligible online players.
     */
    public Set<Player> getEligibleMembers(Party party, double radius) {
        Player leaderObj = Bukkit.getPlayer(party.getLeader());
        if (leaderObj == null || !leaderObj.isOnline()) return Collections.emptySet();

        return party.getMembers().stream()
                .map(Bukkit::getPlayer)
                .filter(p -> p != null && p.isOnline() && !p.isDead())
                .filter(p -> radius <= 0 || (p.getWorld().equals(leaderObj.getWorld()) && p.getLocation().distanceSquared(leaderObj.getLocation()) <= radius * radius))
                .collect(Collectors.toSet());
    }

    /**
     * Broadcasts a sanitized message to all online party members.
     *
     * @param party   The target party.
     * @param sender  The name of the sender.
     * @param message The raw message to broadcast.
     */
    public void sendPartyMessage(Party party, String sender, String message) {
        if (party == null) return;
        String sanitized = MiniMessage.miniMessage().escapeTags(message);
        String format = plugin.getMessagesFile().getString("party.chat_format", "<aqua>[Party] <sender>: <white><msg>")
                .replace("<sender>", sender)
                .replace("<msg>", sanitized);

        party.getMembers().forEach(uuid -> {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                p.sendMessage(MiniMessage.miniMessage().deserialize(format));
            }
        });
    }

    private void purgeExpiredInvites() {
        long now = System.currentTimeMillis();
        activeInvites.forEach((target, invites) -> {
            invites.entrySet().removeIf(entry -> now > entry.getValue());
            if (invites.isEmpty()) activeInvites.remove(target);
        });
    }

    /**
     * Thread-safe data structure representing a group of players.
     */
    public static class Party {
        private final Set<UUID> members = Collections.newSetFromMap(new ConcurrentHashMap<>());
        private UUID leader;

        public Party(UUID leader) {
            this.leader = leader;
            this.members.add(leader);
        }

        public UUID getLeader() {
            return leader;
        }

        public void setLeader(UUID leader) {
            this.leader = leader;
        }

        public Set<UUID> getMembers() {
            return members;
        }

        public void addMember(UUID uuid) {
            members.add(uuid);
        }

        public void removeMember(UUID uuid) {
            members.remove(uuid);
        }
    }
}