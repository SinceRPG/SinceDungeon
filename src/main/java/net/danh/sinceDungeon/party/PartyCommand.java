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

/**
 * Modern Brigadier command implementation for the Party System.
 * Supports Paper 1.21+ dynamic tab completion and synchronous node resolution.
 */
public class PartyCommand {

    /**
     * Registers the /party command tree to the lifecycle registrar.
     *
     * @param plugin The plugin instance.
     * @param event  The registrar event from LifecycleEvents.COMMANDS.
     */
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

                .then(Commands.literal("invite")
                        .then(Commands.argument("target", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    String remaining = builder.getRemainingLowerCase();
                                    Bukkit.getOnlinePlayers().stream()
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
                                        pm.sendPartyMessage(party, "System", plugin.getMessagesFile().getString("party.player_joined").replace("<player>", p.getName()));
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
                        pm.sendPartyMessage(party, "System", plugin.getMessagesFile().getString("party.player_left").replace("<player>", p.getName()));
                        p.sendMessage(ColorUtils.parseWithPrefix(plugin.getMessagesFile().getString("party.left")));
                    }
                    return 1;
                }))

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
                                    target.sendMessage(ColorUtils.parseWithPrefix(plugin.getMessagesFile().getString("party.kicked")));
                                    pm.sendPartyMessage(party, "System", plugin.getMessagesFile().getString("party.player_kicked").replace("<player>", target.getName()));
                                    return 1;
                                })
                        )
                )

                .then(Commands.literal("chat")
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
                            .map(t -> t != null ? t.getName() : "Offline")
                            .collect(Collectors.joining(", "));

                    p.sendMessage(ColorUtils.parseWithPrefix(plugin.getMessagesFile().getString("party.list").replace("<members>", members)));
                    return 1;
                }))
                .build();

        event.registrar().register(partyNode, "SinceDungeon Party System");
    }
}