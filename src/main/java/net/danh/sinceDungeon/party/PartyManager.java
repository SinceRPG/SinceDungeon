package net.danh.sinceDungeon.party;

import net.danh.sinceDungeon.SinceDungeon;
import net.kyori.adventure.text.Component;
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
 */
public class PartyManager {

    private final SinceDungeon plugin;
    private final Map<UUID, Party> activeParties = new ConcurrentHashMap<>();
    private final Map<UUID, Map<UUID, Long>> activeInvites = new ConcurrentHashMap<>();
    private final Set<UUID> partyChatToggled = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public PartyManager(SinceDungeon plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::purgeExpiredInvites, 1200L, 1200L);
    }

    public Party createParty(Player leader) {
        if (getParty(leader.getUniqueId()) != null) return null;
        Party party = new Party(leader.getUniqueId());
        activeParties.put(leader.getUniqueId(), party);
        return party;
    }

    public Party getParty(UUID uuid) {
        return activeParties.get(uuid);
    }

    public void disbandParty(Party party) {
        if (party == null) return;
        party.getMembers().forEach(uuid -> {
            activeParties.remove(uuid);
            partyChatToggled.remove(uuid);
        });
    }

    public void invitePlayer(UUID leader, UUID target) {
        long timeoutSeconds = plugin.getConfigFile().getInt("party.invite-timeout", 60);
        activeInvites.computeIfAbsent(target, k -> new ConcurrentHashMap<>())
                .put(leader, System.currentTimeMillis() + (timeoutSeconds * 1000L));
    }

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

    public void electNewLeader(Party party) {
        if (party == null || party.getMembers().size() <= 1) {
            disbandParty(party);
            return;
        }

        party.removeMember(party.getLeader());
        activeParties.remove(party.getLeader());
        partyChatToggled.remove(party.getLeader());

        UUID newLeader = party.getMembers().iterator().next();
        party.setLeader(newLeader);

        String sysName = plugin.getConfigFile().getString("party.system-name", "System");
        sendPartyMessage(party, sysName, plugin.getMessagesFile().getString("party.new_leader").replace("<player>", Bukkit.getOfflinePlayer(newLeader).getName()));
    }

    public void promoteMember(Party party, UUID newLeader) {
        if (party == null || !party.getMembers().contains(newLeader)) return;
        party.setLeader(newLeader);
        String sysName = plugin.getConfigFile().getString("party.system-name", "System");
        sendPartyMessage(party, sysName, plugin.getMessagesFile().getString("party.promoted").replace("<player>", Bukkit.getOfflinePlayer(newLeader).getName()));
    }

    public Set<Player> getEligibleMembers(Party party, double radius) {
        Player leaderObj = Bukkit.getPlayer(party.getLeader());
        if (leaderObj == null || !leaderObj.isOnline()) return Collections.emptySet();

        return party.getMembers().stream()
                .map(Bukkit::getPlayer)
                .filter(p -> p != null && p.isOnline() && !p.isDead())
                .filter(p -> radius <= 0 || (p.getWorld().equals(leaderObj.getWorld()) && p.getLocation().distanceSquared(leaderObj.getLocation()) <= radius * radius))
                .collect(Collectors.toSet());
    }

    public void sendPartyMessage(Party party, String sender, String message) {
        if (party == null) return;
        String sanitized = MiniMessage.miniMessage().escapeTags(message);
        String format = plugin.getMessagesFile().getString("party.chat_format", "<aqua>[Party] <sender>: <white><msg>")
                .replace("<sender>", sender)
                .replace("<msg>", sanitized);

        Component finalComponent = MiniMessage.miniMessage().deserialize(format);
        party.getMembers().forEach(uuid -> {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) p.sendMessage(finalComponent);
        });
    }

    public boolean togglePartyChat(UUID uuid) {
        if (partyChatToggled.contains(uuid)) {
            partyChatToggled.remove(uuid);
            return false;
        } else {
            partyChatToggled.add(uuid);
            return true;
        }
    }

    public boolean isPartyChatEnabled(UUID uuid) {
        return partyChatToggled.contains(uuid);
    }

    public void removePlayerFromCache(UUID uuid) {
        partyChatToggled.remove(uuid);
        activeInvites.remove(uuid);
    }

    private void purgeExpiredInvites() {
        long now = System.currentTimeMillis();
        activeInvites.forEach((target, invites) -> {
            invites.entrySet().removeIf(entry -> now > entry.getValue());
            if (invites.isEmpty()) activeInvites.remove(target);
        });
    }

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