package net.danh.sinceDungeon.systems.party;

import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.api.interfaces.PartyProvider;
import net.danh.sinceDungeon.models.DungeonGame;
import net.danh.sinceDungeon.utils.ColorUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Native implementation of the PartyProvider interface.
 * Retains all legacy party logic, ensuring backwards compatibility.
 */
public class DefaultPartyProvider implements PartyProvider {

    private final SinceDungeon plugin;
    private final Map<UUID, Party> activeParties = new ConcurrentHashMap<>();
    private final Map<UUID, Map<UUID, Long>> activeInvites = new ConcurrentHashMap<>();
    private final Set<UUID> partyChatToggled = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private BukkitTask purgeTask;

    public DefaultPartyProvider(SinceDungeon plugin) {
        this.plugin = plugin;
    }

    @Override
    public void initialize() {
        purgeTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::purgeExpiredInvites, 1200L, 1200L);
    }

    @Override
    public void cleanup() {
        if (purgeTask != null && !purgeTask.isCancelled()) {
            purgeTask.cancel();
        }
        activeParties.clear();
        activeInvites.clear();
        partyChatToggled.clear();
    }

    @Override
    public boolean hasParty(UUID playerId) {
        return activeParties.containsKey(playerId);
    }

    @Override
    public boolean isLeader(UUID playerId) {
        Party party = activeParties.get(playerId);
        return party != null && party.getLeader().equals(playerId);
    }

    @Override
    public UUID getLeader(UUID playerId) {
        Party party = activeParties.get(playerId);
        return party != null ? party.getLeader() : null;
    }

    @Override
    public Set<UUID> getMembers(UUID playerId) {
        Party party = activeParties.get(playerId);
        return party != null ? party.getMembers() : Collections.emptySet();
    }

    @Override
    public String getMemberName(UUID memberId) {
        Party party = activeParties.get(memberId);
        return party != null ? party.getMemberName(memberId) : "Unknown";
    }

    @Override
    public void sendPartyMessage(UUID playerId, String senderName, String message) {
        Party party = activeParties.get(playerId);
        if (party == null || message.trim().isEmpty()) return;

        String safeSender = MiniMessage.miniMessage().escapeTags(senderName);
        String safeMsg = MiniMessage.miniMessage().escapeTags(message);
        String mmSender = ColorUtils.convertLegacyToMiniMessage(safeSender);
        String mmMessage = ColorUtils.convertLegacyToMiniMessage(safeMsg);

        String rawFormat = plugin.getLanguageManager().getString("party.chat_format", "<aqua>[Party] <sender>: <white><msg>");
        String mmFormat = ColorUtils.convertLegacyToMiniMessage(rawFormat);

        Component finalComponent = MiniMessage.miniMessage().deserialize(
                mmFormat,
                Placeholder.parsed("sender", mmSender),
                Placeholder.parsed("msg", mmMessage)
        );

        String shortId = party.getLeader().toString().substring(0, 6);
        plugin.getLogger().info("[Party Chat - " + shortId + "] " + ColorUtils.toPlainText(finalComponent));

        party.getMembers().forEach(uuid -> {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) p.sendMessage(finalComponent);
        });
    }

    @Override
    public void handleDisconnect(Player player) {
        removePlayerFromCache(player.getUniqueId());

        Party party = activeParties.get(player.getUniqueId());
        if (party == null) return;

        synchronized (party) {
            if (party.getLeader().equals(player.getUniqueId())) {
                passLeadership(party);
            }
            if (!hasOnlineMembers(party)) {
                disbandParty(party.getLeader());
            }
        }
    }

    @Override
    public boolean isPartyChatEnabled(UUID playerId) {
        return partyChatToggled.contains(playerId);
    }

    @Override
    public void disbandParty(UUID leaderId) {
        Party party = activeParties.get(leaderId);
        if (party == null) return;

        if (plugin.getConfigFile().getBoolean("cross-server.enabled", false) && plugin.getRedisManager() != null) {
            plugin.getRedisManager().syncDisbandParty(party.getLeader());
        }

        synchronized (party) {
            party.getMembers().forEach(uuid -> {
                activeParties.remove(uuid);
                partyChatToggled.remove(uuid);
                clearSentInvites(uuid);
                ejectFromDungeon(uuid);
            });
        }
    }

    /* --------------------------------------------------------
       Native-Exclusive Methods (Used internally by PartyCommand)
       -------------------------------------------------------- */

    public Party getPartyObject(UUID uuid) {
        return activeParties.get(uuid);
    }

    public Map<UUID, Map<UUID, Long>> getActiveInvites() {
        return activeInvites;
    }

    public Party createParty(Player leader) {
        Party party = new Party(leader);
        if (activeParties.putIfAbsent(leader.getUniqueId(), party) != null) {
            return null;
        }
        return party;
    }

    public void quitParty(UUID uuid) {
        Party party = activeParties.get(uuid);
        if (party == null) return;

        synchronized (party) {
            if (activeParties.get(party.getLeader()) != party) return;

            if (party.getLeader().equals(uuid)) {
                passLeadership(party);
            }

            party.removeMember(uuid);
            activeParties.remove(uuid);
            partyChatToggled.remove(uuid);
            clearSentInvites(uuid);

            if (party.getMembers().isEmpty() || !hasOnlineMembers(party)) {
                disbandParty(party.getLeader());
            }
        }
        ejectFromDungeon(uuid);
        if (plugin.getConfigFile().getBoolean("cross-server.enabled", false) && plugin.getRedisManager() != null) {
            plugin.getRedisManager().syncLeaveParty(uuid);
        }
    }

    public void kickPlayer(Party party, UUID target) {
        if (party == null) return;
        synchronized (party) {
            party.removeMember(target);
            activeParties.remove(target);
            partyChatToggled.remove(target);
            activeInvites.remove(target);
            clearSentInvites(target);

            if (party.getMembers().isEmpty() || !hasOnlineMembers(party)) {
                disbandParty(party.getLeader());
            }
        }
        ejectFromDungeon(target);
        if (plugin.getConfigFile().getBoolean("cross-server.enabled", false) && plugin.getRedisManager() != null) {
            plugin.getRedisManager().syncLeaveParty(target);
        }
    }

    public void passLeadership(Party party) {
        if (party == null || party.getMembers().size() <= 1) {
            disbandParty(party.getLeader());
            return;
        }

        Set<UUID> candidates = party.getMembers().stream()
                .filter(id -> !id.equals(party.getLeader()))
                .collect(Collectors.toSet());

        UUID newLeader = candidates.stream()
                .filter(id -> Bukkit.getPlayer(id) != null && Bukkit.getPlayer(id).isOnline())
                .findFirst()
                .orElse(candidates.stream().findFirst().orElse(null));

        if (newLeader != null) {
            clearSentInvites(party.getLeader());
            party.setLeader(newLeader);
            String sysName = plugin.getConfigFile().getString("party.system-name", "System");
            sendPartyMessage(newLeader, sysName, plugin.getLanguageManager().getString("party.new_leader").replace("<player>", party.getMemberName(newLeader)));
        } else {
            disbandParty(party.getLeader());
        }
    }

    public boolean invitePlayer(UUID leader, UUID target) {
        long timeoutSeconds = plugin.getConfigFile().getInt("party.invite-timeout", 60);
        boolean[] success = new boolean[]{false};

        activeInvites.compute(target, (k, invites) -> {
            if (invites == null) invites = new ConcurrentHashMap<>();
            if (invites.containsKey(leader) && invites.get(leader) > System.currentTimeMillis()) {
                success[0] = false;
            } else {
                invites.put(leader, System.currentTimeMillis() + (timeoutSeconds * 1000L));
                success[0] = true;
            }
            return invites;
        });
        return success[0];
    }

    public boolean acceptInvite(Player target, UUID leader) {
        Map<UUID, Long> targetInvites = activeInvites.get(target.getUniqueId());
        if (targetInvites == null || !targetInvites.containsKey(leader)) return false;

        if (System.currentTimeMillis() > targetInvites.get(leader)) {
            targetInvites.remove(leader);
            return false;
        }

        Party party = activeParties.get(leader);
        int maxMembers = plugin.getConfigFile().getInt("party.max-members", 4);

        if (party == null || !party.getLeader().equals(leader)) {
            targetInvites.remove(leader);
            return false;
        }

        if (plugin.getDungeonManager().getGame(leader) != null) {
            targetInvites.remove(leader);
            String msg = plugin.getLanguageManager().getString("party.leader_in_dungeon", "&cCannot join! The Party Leader is currently fighting inside a Dungeon!");
            target.sendMessage(ColorUtils.parseWithPrefix(msg));
            return false;
        }

        if (plugin.getDungeonManager().getGame(target.getUniqueId()) != null) {
            targetInvites.remove(leader);
            String msg = plugin.getLanguageManager().getString("party.cannot_accept_in_dungeon", "&cYou cannot accept a party invite while inside a Dungeon!");
            target.sendMessage(ColorUtils.parseWithPrefix(msg));
            return false;
        }

        synchronized (party) {
            if (activeParties.get(party.getLeader()) != party) {
                targetInvites.remove(leader);
                return false;
            }
            if (party.getMembers().size() >= maxMembers) {
                targetInvites.remove(leader);
                return false;
            }
            party.addMember(target);
            activeParties.put(target.getUniqueId(), party);
        }

        activeInvites.remove(target.getUniqueId());
        return true;
    }

    public void promoteMember(Party party, UUID newLeader) {
        if (party == null || !party.getMembers().contains(newLeader)) return;
        synchronized (party) {
            clearSentInvites(party.getLeader());
            party.setLeader(newLeader);
        }
        String sysName = plugin.getConfigFile().getString("party.system-name", "System");
        sendPartyMessage(party.getLeader(), sysName, plugin.getLanguageManager().getString("party.promoted").replace("<player>", party.getMemberName(newLeader)));
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

    public void removePlayerFromCache(UUID uuid) {
        partyChatToggled.remove(uuid);
        activeInvites.remove(uuid);
    }

    private void clearSentInvites(UUID sender) {
        activeInvites.values().forEach(invites -> invites.remove(sender));
        activeInvites.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    private void purgeExpiredInvites() {
        long now = System.currentTimeMillis();
        activeInvites.entrySet().removeIf(entry -> {
            entry.getValue().entrySet().removeIf(inv -> now > inv.getValue());
            return entry.getValue().isEmpty();
        });
    }

    /**
     * Ejects a player from their active dungeon game cleanly if they leave the party natively.
     *
     * @param uuid The UUID of the player to eject.
     */
    private void ejectFromDungeon(UUID uuid) {
        Player p = Bukkit.getPlayer(uuid);
        if (p != null && p.isOnline()) {
            DungeonGame game = plugin.getDungeonManager().getGame(uuid);
            if (game != null) {
                game.handlePlayerDisconnect(p, false);
                String msg = plugin.getLanguageManager().getString("party.left_dungeon_due_to_party", "&cYou have been removed from the Dungeon because you left the Party!");
                p.sendMessage(ColorUtils.parseWithPrefix(msg));
            }
        }
    }

    private boolean hasOnlineMembers(Party party) {
        return party.getMembers().stream().anyMatch(id -> {
            Player p = Bukkit.getPlayer(id);
            return p != null && p.isOnline();
        });
    }

    public void forceCreateCrossServerParty(UUID leader, String[] members) {
        String defaultLeaderName = plugin.getLanguageManager().getString("party.leader_default_name", "Leader");
        Party party = new Party(leader, defaultLeaderName);
        for (String mem : members) {
            String[] split = mem.split("~");
            if (split.length == 2) {
                UUID memId = UUID.fromString(split[0]);
                String memName = split[1];
                party.forceAddMember(memId, memName);
                activeParties.put(memId, party);
            }
        }
        activeParties.put(leader, party);
    }

    public void silentDisband(Party party) {
        if (party == null) return;
        synchronized (party) {
            party.getMembers().forEach(uuid -> {
                activeParties.remove(uuid);
                partyChatToggled.remove(uuid);
            });
        }
    }

    public void silentQuit(UUID uuid) {
        Party party = activeParties.get(uuid);
        if (party == null) return;
        synchronized (party) {
            party.removeMember(uuid);
            activeParties.remove(uuid);
            partyChatToggled.remove(uuid);
            if (party.getMembers().isEmpty()) {
                silentDisband(party);
            }
        }
    }

    public void updatePlayerName(Player p) {
        Party party = activeParties.get(p.getUniqueId());
        if (party != null) {
            party.updateMemberName(p);
        }
    }

    public static class Party {
        private final Map<UUID, String> members = new ConcurrentHashMap<>();
        private UUID leader;

        public Party(Player leader) {
            this.leader = leader.getUniqueId();
            this.members.put(leader.getUniqueId(), leader.getName());
        }

        public Party(UUID leader, String leaderName) {
            this.leader = leader;
            this.members.put(leader, leaderName);
        }

        public UUID getLeader() {
            return leader;
        }

        public void setLeader(UUID leader) {
            this.leader = leader;
        }

        public Set<UUID> getMembers() {
            return members.keySet();
        }

        public String getMemberName(UUID uuid) {
            return members.getOrDefault(uuid, SinceDungeon.getPlugin().getLanguageManager().getString("party.unknown_player", "Unknown"));
        }

        public void addMember(Player p) {
            members.put(p.getUniqueId(), p.getName());
        }

        public void forceAddMember(UUID uuid, String name) {
            members.put(uuid, name);
        }

        public void removeMember(UUID uuid) {
            members.remove(uuid);
        }

        public void updateMemberName(Player p) {
            members.put(p.getUniqueId(), p.getName());
        }
    }
}