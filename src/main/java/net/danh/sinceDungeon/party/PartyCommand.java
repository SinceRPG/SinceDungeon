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
                                            .filter(t -> pm.getParty(t.getUniqueId()) == null) // Don't suggest players already in a party
                                            .map(Player::getName)
                                            .filter(name -> name.toLowerCase().startsWith(remaining))
                                            .forEach(builder::suggest);
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> {
                                    Player p = (Player) ctx.getSource().getSender();
                                    PartyManager.Party party = pm.getParty(p.getUniqueId());

                                    if (party == null || !party.getLeader().equals(p.getUniqueId())) {
                                        p.sendMessage(ColorUtils.parseWithPrefix(plugin.getMessagesFile().getString("party.not_leader")));
                                        return 0;
                                    }

                                    Player target = Bukkit.getPlayerExact(StringArgumentType.getString(ctx, "target"));
                                    if (target == null || target.equals(p)) {
                                        p.sendMessage(ColorUtils.parseWithPrefix(plugin.getMessagesFile().getString("party.player_not_found")));
                                        return 0;
                                    }

                                    if (pm.getParty(target.getUniqueId()) != null) {
                                        p.sendMessage(ColorUtils.parseWithPrefix(plugin.getMessagesFile().getString("party.target_already_in_party")));
                                        return 0;
                                    }

                                    pm.invitePlayer(p.getUniqueId(), target.getUniqueId());
                                    p.sendMessage(ColorUtils.parseWithPrefix(plugin.getMessagesFile().getString("party.invite_sent").replace("<player>", target.getName())));
                                    target.sendMessage(ColorUtils.parseWithPrefix(plugin.getMessagesFile().getString("party.invite_received").replace("<player>", p.getName())));
                                    return 1;
                                })
                        )
                )

                .then(Commands.literal("accept")
                        .then(Commands.argument("leader", StringArgumentType.word())
                                .executes(ctx -> {
                                    Player p = (Player) ctx.getSource().getSender();
                                    Player leader = Bukkit.getPlayerExact(StringArgumentType.getString(ctx, "leader"));

                                    if (leader == null) {
                                        p.sendMessage(ColorUtils.parseWithPrefix(plugin.getMessagesFile().getString("party.player_not_found")));
                                        return 0;
                                    }

                                    if (pm.acceptInvite(p, leader.getUniqueId())) {
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

                    if (party.getLeader().equals(p.getUniqueId())) {
                        pm.electNewLeader(party);
                        p.sendMessage(ColorUtils.parseWithPrefix(plugin.getMessagesFile().getString("party.left")));
                    } else {
                        party.removeMember(p.getUniqueId());
                        pm.removePlayerFromCache(p.getUniqueId());
                        String sysName = plugin.getConfigFile().getString("party.system-name", "System");
                        pm.sendPartyMessage(party, sysName, plugin.getMessagesFile().getString("party.player_left").replace("<player>", p.getName()));
                        p.sendMessage(ColorUtils.parseWithPrefix(plugin.getMessagesFile().getString("party.left")));
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
                                            party.getMembers().stream()
                                                    .map(Bukkit::getPlayer)
                                                    .filter(t -> t != null && t.getName().toLowerCase().startsWith(remaining) && !t.equals(p))
                                                    .forEach(t -> builder.suggest(t.getName()));
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

                                    Player target = Bukkit.getPlayerExact(StringArgumentType.getString(ctx, "target"));
                                    if (target == null || !party.getMembers().contains(target.getUniqueId()) || target.equals(p)) {
                                        p.sendMessage(ColorUtils.parseWithPrefix(plugin.getMessagesFile().getString("party.player_not_in_party")));
                                        return 0;
                                    }

                                    pm.promoteMember(party, target.getUniqueId());
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
                                                    .map(Bukkit::getPlayer)
                                                    .filter(t -> t != null && t.getName().toLowerCase().startsWith(remaining) && !t.equals(p))
                                                    .forEach(t -> builder.suggest(t.getName()));
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

                                    Player target = Bukkit.getPlayerExact(StringArgumentType.getString(ctx, "target"));
                                    if (target == null || !party.getMembers().contains(target.getUniqueId()) || target.equals(p)) {
                                        p.sendMessage(ColorUtils.parseWithPrefix(plugin.getMessagesFile().getString("party.player_not_in_party")));
                                        return 0;
                                    }

                                    party.removeMember(target.getUniqueId());
                                    pm.removePlayerFromCache(target.getUniqueId());
                                    target.sendMessage(ColorUtils.parseWithPrefix(plugin.getMessagesFile().getString("party.kicked")));
                                    String sysName = plugin.getConfigFile().getString("party.system-name", "System");
                                    pm.sendPartyMessage(party, sysName, plugin.getMessagesFile().getString("party.player_kicked").replace("<player>", target.getName()));
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

                                    pm.sendPartyMessage(party, p.getName(), StringArgumentType.getString(ctx, "message"));
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

                    String members = party.getMembers().stream()
                            .map(Bukkit::getPlayer)
                            .map(t -> {
                                if (t == null) return "Offline";
                                return t.getUniqueId().equals(party.getLeader()) ? t.getName() + " (Leader)" : t.getName();
                            })
                            .collect(Collectors.joining(", "));

                    p.sendMessage(ColorUtils.parseWithPrefix(plugin.getMessagesFile().getString("party.list").replace("<members>", members)));
                    return 1;
                }))
                .build();

        event.registrar().register(partyNode, "SinceDungeon Party System");
    }
}