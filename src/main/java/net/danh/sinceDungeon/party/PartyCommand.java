package net.danh.sinceDungeon.party;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.registrar.ReloadableRegistrarEvent;
import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class PartyCommand {

    public static void register(SinceDungeon plugin, ReloadableRegistrarEvent<Commands> event) {
        PartyManager pm = plugin.getPartyManager();

        LiteralCommandNode<CommandSourceStack> partyNode = Commands.literal("party")
                .requires(source -> source.getSender() instanceof Player)

                .then(Commands.literal("create").executes(ctx -> {
                    Player p = (Player) ctx.getSource().getSender();
                    if (pm.getParty(p.getUniqueId()) != null) {
                        p.sendMessage(ColorUtils.parseWithPrefix(plugin.getMessagesFile().getString("party.already_in_party")));
                        return 0;
                    }
                    pm.createParty(p);
                    p.sendMessage(ColorUtils.parseWithPrefix(plugin.getMessagesFile().getString("party.created")));
                    return 1;
                }))

                .then(Commands.literal("disband").executes(ctx -> {
                    Player p = (Player) ctx.getSource().getSender();
                    PartyManager.Party party = pm.getParty(p.getUniqueId());
                    if (party == null || !party.getLeader().equals(p.getUniqueId())) {
                        p.sendMessage(ColorUtils.parseWithPrefix(plugin.getMessagesFile().getString("party.not_leader")));
                        return 0;
                    }
                    String sysName = plugin.getConfigFile().getString("party.system-name", "System");
                    pm.sendPartyMessage(party, sysName, plugin.getMessagesFile().getString("party.disbanded"));
                    pm.disbandParty(party);
                    return 1;
                }))

                .then(Commands.literal("invite")
                        .then(Commands.argument("target", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    String remaining = builder.getRemainingLowerCase();
                                    Bukkit.getOnlinePlayers().stream()
                                            .filter(t -> pm.getParty(t.getUniqueId()) == null)
                                            .map(Player::getName)
                                            .filter(name -> name.toLowerCase().startsWith(remaining))
                                            .forEach(builder::suggest);
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> {
                                    Player p = (Player) ctx.getSource().getSender();
                                    PartyManager.Party party = pm.getParty(p.getUniqueId());
                                    boolean isAutoCreated = false;

                                    if (party != null && !party.getLeader().equals(p.getUniqueId())) {
                                        p.sendMessage(ColorUtils.parseWithPrefix(plugin.getMessagesFile().getString("party.not_leader")));
                                        return 0;
                                    }

                                    Player target = Bukkit.getPlayerExact(StringArgumentType.getString(ctx, "target"));

                                    // VÁ LỖI LOGIC: Bắt rõ ràng việc tự mời bản thân
                                    if (target != null && target.equals(p)) {
                                        p.sendMessage(ColorUtils.parseWithPrefix(plugin.getMessagesFile().getString("party.invite_self", "<red>You cannot invite yourself to the party!")));
                                        return 0;
                                    }

                                    if (target == null) {
                                        p.sendMessage(ColorUtils.parseWithPrefix(plugin.getMessagesFile().getString("party.player_not_found")));
                                        return 0;
                                    }

                                    if (pm.getParty(target.getUniqueId()) != null) {
                                        p.sendMessage(ColorUtils.parseWithPrefix(plugin.getMessagesFile().getString("party.target_already_in_party")));
                                        return 0;
                                    }

                                    int maxMembers = plugin.getConfigFile().getInt("party.max-members", 4);
                                    if (party != null && party.getMembers().size() >= maxMembers) {
                                        p.sendMessage(ColorUtils.parseWithPrefix(plugin.getMessagesFile().getString("party.full", "<red>Party is full!")));
                                        return 0;
                                    }

                                    if (party == null) {
                                        party = pm.createParty(p);
                                        isAutoCreated = true;
                                    }

                                    boolean sent = pm.invitePlayer(p.getUniqueId(), target.getUniqueId());
                                    if (sent) {
                                        if (isAutoCreated) {
                                            p.sendMessage(ColorUtils.parseWithPrefix(plugin.getMessagesFile().getString("party.auto_created", "<green>Hệ thống đã tự động tạo nhóm cho bạn.")));
                                        }
                                        p.sendMessage(ColorUtils.parseWithPrefix(plugin.getMessagesFile().getString("party.invite_sent").replace("<player>", target.getName())));
                                        target.sendMessage(ColorUtils.parseWithPrefix(plugin.getMessagesFile().getString("party.invite_received").replace("<player>", p.getName())));
                                    } else {
                                        if (isAutoCreated) pm.disbandParty(party);
                                        p.sendMessage(ColorUtils.parseWithPrefix(plugin.getMessagesFile().getString("party.already_invited", "<red>Bạn đã gửi lời mời cho người này rồi, vui lòng chờ họ phản hồi.")));
                                    }
                                    return 1;
                                })
                        )
                )

                .then(Commands.literal("accept")
                        .then(Commands.argument("leader", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    if (ctx.getSource().getSender() instanceof Player p) {
                                        Map<UUID, Long> invites = pm.getActiveInvites().get(p.getUniqueId());
                                        if (invites != null) {
                                            String remaining = builder.getRemainingLowerCase();
                                            invites.keySet().stream()
                                                    // VÁ LỖI: Lấy trực tiếp từ Party để không gọi getOfflinePlayer gây lag
                                                    .map(id -> pm.getParty(id) != null ? pm.getParty(id).getMemberName(id) : null)
                                                    .filter(name -> name != null && name.toLowerCase().startsWith(remaining))
                                                    .forEach(builder::suggest);
                                        }
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> {
                                    Player p = (Player) ctx.getSource().getSender();

                                    Map<UUID, Long> invites = pm.getActiveInvites().get(p.getUniqueId());
                                    if (invites == null || invites.isEmpty()) {
                                        p.sendMessage(ColorUtils.parseWithPrefix(plugin.getMessagesFile().getString("party.no_invite")));
                                        return 0;
                                    }

                                    String leaderName = StringArgumentType.getString(ctx, "leader");
                                    UUID leaderId = null;

                                    for (UUID id : invites.keySet()) {
                                        PartyManager.Party pty = pm.getParty(id);
                                        if (pty != null) {
                                            String name = pty.getMemberName(id);
                                            if (name != null && name.equalsIgnoreCase(leaderName)) {
                                                leaderId = id;
                                                break;
                                            }
                                        }
                                    }

                                    if (leaderId == null) {
                                        p.sendMessage(ColorUtils.parseWithPrefix(plugin.getMessagesFile().getString("party.player_not_found")));
                                        return 0;
                                    }

                                    if (pm.acceptInvite(p, leaderId)) {
                                        PartyManager.Party party = pm.getParty(p.getUniqueId());
                                        String sysName = plugin.getConfigFile().getString("party.system-name", "System");
                                        pm.sendPartyMessage(party, sysName, plugin.getMessagesFile().getString("party.player_joined").replace("<player>", p.getName()));
                                        return 1;
                                    } else {
                                        p.sendMessage(ColorUtils.parseWithPrefix(plugin.getMessagesFile().getString("party.no_invite")));
                                        return 0;
                                    }
                                })
                        )
                )

                .then(Commands.literal("leave").executes(ctx -> {
                    Player p = (Player) ctx.getSource().getSender();
                    PartyManager.Party party = pm.getParty(p.getUniqueId());

                    if (party == null) {
                        p.sendMessage(ColorUtils.parseWithPrefix(plugin.getMessagesFile().getString("party.not_in_party")));
                        return 0;
                    }

                    pm.quitParty(p.getUniqueId());
                    p.sendMessage(ColorUtils.parseWithPrefix(plugin.getMessagesFile().getString("party.left")));

                    if (party.getMembers().size() > 0) {
                        String sysName = plugin.getConfigFile().getString("party.system-name", "System");
                        pm.sendPartyMessage(party, sysName, plugin.getMessagesFile().getString("party.player_left").replace("<player>", p.getName()));
                    }
                    return 1;
                }))

                .then(Commands.literal("promote")
                        .then(Commands.argument("target", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    if (ctx.getSource().getSender() instanceof Player p) {
                                        PartyManager.Party party = pm.getParty(p.getUniqueId());
                                        if (party != null && party.getLeader().equals(p.getUniqueId())) {
                                            String remaining = builder.getRemainingLowerCase();
                                            // Sử dụng Name Cache tốc độ ánh sáng
                                            party.getMembers().stream()
                                                    .map(party::getMemberName)
                                                    .filter(name -> name != null && name.toLowerCase().startsWith(remaining) && !name.equalsIgnoreCase(p.getName()))
                                                    .forEach(builder::suggest);
                                        }
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> {
                                    Player p = (Player) ctx.getSource().getSender();
                                    PartyManager.Party party = pm.getParty(p.getUniqueId());

                                    if (party == null || !party.getLeader().equals(p.getUniqueId())) {
                                        p.sendMessage(ColorUtils.parseWithPrefix(plugin.getMessagesFile().getString("party.not_leader")));
                                        return 0;
                                    }

                                    String targetName = StringArgumentType.getString(ctx, "target");
                                    UUID targetId = null;

                                    for (UUID id : party.getMembers()) {
                                        String name = party.getMemberName(id);
                                        if (name != null && name.equalsIgnoreCase(targetName)) {
                                            targetId = id;
                                            break;
                                        }
                                    }

                                    if (targetId == null || targetId.equals(p.getUniqueId())) {
                                        p.sendMessage(ColorUtils.parseWithPrefix(plugin.getMessagesFile().getString("party.player_not_in_party")));
                                        return 0;
                                    }

                                    pm.promoteMember(party, targetId);
                                    return 1;
                                })
                        )
                )

                .then(Commands.literal("kick")
                        .then(Commands.argument("target", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    if (ctx.getSource().getSender() instanceof Player p) {
                                        PartyManager.Party party = pm.getParty(p.getUniqueId());
                                        if (party != null && party.getLeader().equals(p.getUniqueId())) {
                                            String remaining = builder.getRemainingLowerCase();
                                            party.getMembers().stream()
                                                    .map(party::getMemberName)
                                                    .filter(name -> name != null && name.toLowerCase().startsWith(remaining) && !name.equalsIgnoreCase(p.getName()))
                                                    .forEach(builder::suggest);
                                        }
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> {
                                    Player p = (Player) ctx.getSource().getSender();
                                    PartyManager.Party party = pm.getParty(p.getUniqueId());

                                    if (party == null || !party.getLeader().equals(p.getUniqueId())) {
                                        p.sendMessage(ColorUtils.parseWithPrefix(plugin.getMessagesFile().getString("party.not_leader")));
                                        return 0;
                                    }

                                    String targetName = StringArgumentType.getString(ctx, "target");
                                    UUID targetId = null;

                                    for (UUID id : party.getMembers()) {
                                        String name = party.getMemberName(id);
                                        if (name != null && name.equalsIgnoreCase(targetName)) {
                                            targetId = id;
                                            break;
                                        }
                                    }

                                    if (targetId == null || targetId.equals(p.getUniqueId())) {
                                        p.sendMessage(ColorUtils.parseWithPrefix(plugin.getMessagesFile().getString("party.player_not_in_party")));
                                        return 0;
                                    }

                                    pm.kickPlayer(party, targetId);

                                    Player targetOnline = Bukkit.getPlayer(targetId);
                                    if (targetOnline != null && targetOnline.isOnline()) {
                                        targetOnline.sendMessage(ColorUtils.parseWithPrefix(plugin.getMessagesFile().getString("party.kicked")));
                                    }

                                    String sysName = plugin.getConfigFile().getString("party.system-name", "System");
                                    pm.sendPartyMessage(party, sysName, plugin.getMessagesFile().getString("party.player_kicked").replace("<player>", targetName));
                                    return 1;
                                })
                        )
                )

                .then(Commands.literal("chat")
                        .executes(ctx -> {
                            Player p = (Player) ctx.getSource().getSender();
                            PartyManager.Party party = pm.getParty(p.getUniqueId());
                            if (party == null) {
                                p.sendMessage(ColorUtils.parseWithPrefix(plugin.getMessagesFile().getString("party.not_in_party")));
                                return 0;
                            }
                            boolean enabled = pm.togglePartyChat(p.getUniqueId());
                            String state = enabled ? plugin.getMessagesFile().getString("words.true_word") : plugin.getMessagesFile().getString("words.false_word");
                            p.sendMessage(ColorUtils.parseWithPrefix(plugin.getMessagesFile().getString("party.chat_toggled").replace("<status>", state)));
                            return 1;
                        })
                        .then(Commands.argument("message", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    Player p = (Player) ctx.getSource().getSender();
                                    PartyManager.Party party = pm.getParty(p.getUniqueId());

                                    if (party == null) {
                                        p.sendMessage(ColorUtils.parseWithPrefix(plugin.getMessagesFile().getString("party.not_in_party")));
                                        return 0;
                                    }

                                    String msg = StringArgumentType.getString(ctx, "message").trim();
                                    if (msg.isEmpty()) {
                                        p.sendMessage(ColorUtils.parseWithPrefix(plugin.getMessagesFile().getString("party.empty_message", "<red>Please enter a message.")));
                                        return 0;
                                    }

                                    pm.sendPartyMessage(party, p.getName(), msg);
                                    return 1;
                                })
                        )
                )

                .then(Commands.literal("list").executes(ctx -> {
                    Player p = (Player) ctx.getSource().getSender();
                    PartyManager.Party party = pm.getParty(p.getUniqueId());

                    if (party == null) {
                        p.sendMessage(ColorUtils.parseWithPrefix(plugin.getMessagesFile().getString("party.not_in_party")));
                        return 0;
                    }

                    String offlineTxt = plugin.getMessagesFile().getString("party.offline", "Offline");
                    String leaderSuffix = plugin.getMessagesFile().getString("party.leader_suffix", " (Leader)");

                    String members = party.getMembers().stream()
                            .map(id -> {
                                Player onlinePlayer = Bukkit.getPlayer(id);
                                String name = party.getMemberName(id);

                                String status = (onlinePlayer != null && onlinePlayer.isOnline()) ? "" : " <gray>[" + offlineTxt + "]";
                                String prefix = id.equals(party.getLeader()) ? leaderSuffix : "";

                                return name + prefix + status;
                            })
                            .collect(Collectors.joining(", "));

                    p.sendMessage(ColorUtils.parseWithPrefix(plugin.getMessagesFile().getString("party.list").replace("<members>", members)));
                    return 1;
                }))
                .build();

        event.registrar().register(partyNode, "SinceDungeon Party System");
    }
}