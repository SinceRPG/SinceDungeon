package net.danh.sinceDungeon.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.registrar.ReloadableRegistrarEvent;
import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.managers.PartyManager;
import net.danh.sinceDungeon.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Handles the registration and execution of the /party command ecosystem.
 */
public class PartyCommand {

    /**
     * Registers the Party command using Paper's Brigadier API.
     * The root command literal and aliases are dynamically loaded from the configuration.
     * Full tab completion logic is applied to dynamic variables like <target> and <leader>.
     *
     * @param plugin The main plugin instance.
     * @param event  The lifecycle registrar event.
     */
    public static void register(SinceDungeon plugin, ReloadableRegistrarEvent<Commands> event) {
        PartyManager pm = plugin.getPartyManager();

        String commandName = plugin.getConfigFile().getString("commands.party", "party");
        List<String> aliases = plugin.getConfigFile().getStringList("commands.party-aliases");

        LiteralCommandNode<CommandSourceStack> partyNode = Commands.literal(commandName)
                .executes(ctx -> {
                    CommandSender sender = ctx.getSource().getSender();
                    if (!(sender instanceof Player)) {
                        sender.sendMessage(ColorUtils.parseWithPrefix(plugin.getLanguageManager().getString("admin.only_player")));
                        return 0;
                    }
                    sender.sendMessage(ColorUtils.parseWithPrefix(plugin.getLanguageManager().getString("party.usage_main")));
                    return 1;
                })

                .then(Commands.literal("create").executes(ctx -> {
                    CommandSender sender = ctx.getSource().getSender();
                    if (!(sender instanceof Player p)) {
                        sender.sendMessage(ColorUtils.parseWithPrefix(plugin.getLanguageManager().getString("admin.only_player")));
                        return 0;
                    }
                    if (pm.getParty(p.getUniqueId()) != null) {
                        p.sendMessage(ColorUtils.parseWithPrefix(plugin.getLanguageManager().getString("party.already_in_party")));
                        return 0;
                    }
                    PartyManager.Party party = pm.createParty(p);
                    if (party != null) {
                        p.sendMessage(ColorUtils.parseWithPrefix(plugin.getLanguageManager().getString("party.created")));
                    } else {
                        p.sendMessage(ColorUtils.parseWithPrefix(plugin.getLanguageManager().getString("party.already_in_party")));
                    }
                    return 1;
                }))

                .then(Commands.literal("disband").executes(ctx -> {
                    CommandSender sender = ctx.getSource().getSender();
                    if (!(sender instanceof Player p)) {
                        sender.sendMessage(ColorUtils.parseWithPrefix(plugin.getLanguageManager().getString("admin.only_player")));
                        return 0;
                    }
                    PartyManager.Party party = pm.getParty(p.getUniqueId());
                    if (party == null || !party.getLeader().equals(p.getUniqueId())) {
                        p.sendMessage(ColorUtils.parseWithPrefix(plugin.getLanguageManager().getString("party.not_leader")));
                        return 0;
                    }
                    String sysName = plugin.getConfigFile().getString("party.system-name", "System");
                    pm.sendPartyMessage(party, sysName, plugin.getLanguageManager().getString("party.disbanded"));
                    pm.disbandParty(party);
                    return 1;
                }))

                .then(Commands.literal("invite")
                        .executes(ctx -> {
                            CommandSender sender = ctx.getSource().getSender();
                            if (!(sender instanceof Player)) {
                                sender.sendMessage(ColorUtils.parseWithPrefix(plugin.getLanguageManager().getString("admin.only_player")));
                                return 0;
                            }
                            sender.sendMessage(ColorUtils.parseWithPrefix(plugin.getLanguageManager().getString("party.usage_invite")));
                            return 1;
                        })
                        .then(Commands.argument("target", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    if (ctx.getSource().getSender() instanceof Player p) {
                                        PartyManager.Party party = pm.getParty(p.getUniqueId());
                                        int maxMembers = plugin.getConfigFile().getInt("party.max-members", 4);

                                        if (party != null && party.getMembers().size() >= maxMembers) {
                                            return builder.buildFuture();
                                        }

                                        String remaining = builder.getRemainingLowerCase();
                                        Bukkit.getOnlinePlayers().stream()
                                                .filter(t -> pm.getParty(t.getUniqueId()) == null)
                                                .filter(t -> !t.equals(p))
                                                .map(Player::getName)
                                                .filter(name -> name.toLowerCase().startsWith(remaining))
                                                .forEach(builder::suggest);
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> {
                                    CommandSender sender = ctx.getSource().getSender();
                                    if (!(sender instanceof Player p)) {
                                        sender.sendMessage(ColorUtils.parseWithPrefix(plugin.getLanguageManager().getString("admin.only_player")));
                                        return 0;
                                    }
                                    PartyManager.Party party = pm.getParty(p.getUniqueId());
                                    boolean isAutoCreated = false;

                                    if (party != null && !party.getLeader().equals(p.getUniqueId())) {
                                        p.sendMessage(ColorUtils.parseWithPrefix(plugin.getLanguageManager().getString("party.not_leader")));
                                        return 0;
                                    }

                                    Player target = Bukkit.getPlayerExact(StringArgumentType.getString(ctx, "target"));

                                    if (target != null && target.equals(p)) {
                                        p.sendMessage(ColorUtils.parseWithPrefix(plugin.getLanguageManager().getString("party.invite_self")));
                                        return 0;
                                    }

                                    if (target == null) {
                                        p.sendMessage(ColorUtils.parseWithPrefix(plugin.getLanguageManager().getString("party.player_not_found")));
                                        return 0;
                                    }

                                    if (pm.getParty(target.getUniqueId()) != null) {
                                        p.sendMessage(ColorUtils.parseWithPrefix(plugin.getLanguageManager().getString("party.target_already_in_party")));
                                        return 0;
                                    }

                                    int maxMembers = plugin.getConfigFile().getInt("party.max-members", 4);
                                    if (party != null && party.getMembers().size() >= maxMembers) {
                                        p.sendMessage(ColorUtils.parseWithPrefix(plugin.getLanguageManager().getString("party.full")));
                                        return 0;
                                    }

                                    if (party == null) {
                                        party = pm.createParty(p);
                                        if (party == null) {
                                            party = pm.getParty(p.getUniqueId());
                                        } else {
                                            isAutoCreated = true;
                                        }
                                    }

                                    if (party == null) return 0;

                                    boolean sent = pm.invitePlayer(p.getUniqueId(), target.getUniqueId());
                                    if (sent) {
                                        if (isAutoCreated) {
                                            p.sendMessage(ColorUtils.parseWithPrefix(plugin.getLanguageManager().getString("party.auto_created")));
                                        }
                                        p.sendMessage(ColorUtils.parseWithPrefix(plugin.getLanguageManager().getString("party.invite_sent").replace("<player>", target.getName())));
                                        target.sendMessage(ColorUtils.parseWithPrefix(plugin.getLanguageManager().getString("party.invite_received").replace("<player>", p.getName())));
                                    } else {
                                        if (isAutoCreated) pm.disbandParty(party);
                                        p.sendMessage(ColorUtils.parseWithPrefix(plugin.getLanguageManager().getString("party.already_invited")));
                                    }
                                    return 1;
                                })
                        )
                )

                .then(Commands.literal("accept")
                        .executes(ctx -> {
                            CommandSender sender = ctx.getSource().getSender();
                            if (!(sender instanceof Player)) {
                                sender.sendMessage(ColorUtils.parseWithPrefix(plugin.getLanguageManager().getString("admin.only_player")));
                                return 0;
                            }
                            sender.sendMessage(ColorUtils.parseWithPrefix(plugin.getLanguageManager().getString("party.usage_accept")));
                            return 1;
                        })
                        .then(Commands.argument("leader", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    if (ctx.getSource().getSender() instanceof Player p) {
                                        Map<UUID, Long> invites = pm.getActiveInvites().get(p.getUniqueId());
                                        if (invites != null) {
                                            String remaining = builder.getRemainingLowerCase();
                                            invites.keySet().stream()
                                                    .map(id -> pm.getParty(id) != null ? pm.getParty(id).getMemberName(id) : null)
                                                    .filter(name -> name != null && name.toLowerCase().startsWith(remaining))
                                                    .forEach(builder::suggest);
                                        }
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> {
                                    CommandSender sender = ctx.getSource().getSender();
                                    if (!(sender instanceof Player p)) {
                                        sender.sendMessage(ColorUtils.parseWithPrefix(plugin.getLanguageManager().getString("admin.only_player")));
                                        return 0;
                                    }

                                    if (pm.getParty(p.getUniqueId()) != null) {
                                        p.sendMessage(ColorUtils.parseWithPrefix(plugin.getLanguageManager().getString("party.already_in_party")));
                                        return 0;
                                    }

                                    Map<UUID, Long> invites = pm.getActiveInvites().get(p.getUniqueId());
                                    if (invites == null || invites.isEmpty()) {
                                        p.sendMessage(ColorUtils.parseWithPrefix(plugin.getLanguageManager().getString("party.no_invite")));
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
                                        p.sendMessage(ColorUtils.parseWithPrefix(plugin.getLanguageManager().getString("party.player_not_found")));
                                        return 0;
                                    }

                                    if (pm.acceptInvite(p, leaderId)) {
                                        PartyManager.Party party = pm.getParty(p.getUniqueId());
                                        String sysName = plugin.getConfigFile().getString("party.system-name", "System");
                                        pm.sendPartyMessage(party, sysName, plugin.getLanguageManager().getString("party.player_joined").replace("<player>", p.getName()));
                                    } else {
                                        p.sendMessage(ColorUtils.parseWithPrefix(plugin.getLanguageManager().getString("party.no_invite")));
                                    }
                                    return 1;
                                })
                        )
                )

                .then(Commands.literal("leave").executes(ctx -> {
                    CommandSender sender = ctx.getSource().getSender();
                    if (!(sender instanceof Player p)) {
                        sender.sendMessage(ColorUtils.parseWithPrefix(plugin.getLanguageManager().getString("admin.only_player")));
                        return 0;
                    }
                    PartyManager.Party party = pm.getParty(p.getUniqueId());

                    if (party == null) {
                        p.sendMessage(ColorUtils.parseWithPrefix(plugin.getLanguageManager().getString("party.not_in_party")));
                        return 0;
                    }

                    pm.quitParty(p.getUniqueId());
                    p.sendMessage(ColorUtils.parseWithPrefix(plugin.getLanguageManager().getString("party.left")));

                    if (pm.getParty(party.getLeader()) != null) {
                        String sysName = plugin.getConfigFile().getString("party.system-name", "System");
                        pm.sendPartyMessage(party, sysName, plugin.getLanguageManager().getString("party.player_left").replace("<player>", p.getName()));
                    }
                    return 1;
                }))

                .then(Commands.literal("promote")
                        .executes(ctx -> {
                            CommandSender sender = ctx.getSource().getSender();
                            if (!(sender instanceof Player)) {
                                sender.sendMessage(ColorUtils.parseWithPrefix(plugin.getLanguageManager().getString("admin.only_player")));
                                return 0;
                            }
                            sender.sendMessage(ColorUtils.parseWithPrefix(plugin.getLanguageManager().getString("party.usage_promote")));
                            return 1;
                        })
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
                                    CommandSender sender = ctx.getSource().getSender();
                                    if (!(sender instanceof Player p)) {
                                        sender.sendMessage(ColorUtils.parseWithPrefix(plugin.getLanguageManager().getString("admin.only_player")));
                                        return 0;
                                    }
                                    PartyManager.Party party = pm.getParty(p.getUniqueId());

                                    if (party == null || !party.getLeader().equals(p.getUniqueId())) {
                                        p.sendMessage(ColorUtils.parseWithPrefix(plugin.getLanguageManager().getString("party.not_leader")));
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

                                    if (targetId != null && targetId.equals(p.getUniqueId())) {
                                        p.sendMessage(ColorUtils.parseWithPrefix(plugin.getLanguageManager().getString("party.promote_self")));
                                        return 0;
                                    }

                                    if (targetId == null) {
                                        p.sendMessage(ColorUtils.parseWithPrefix(plugin.getLanguageManager().getString("party.player_not_in_party")));
                                        return 0;
                                    }

                                    pm.promoteMember(party, targetId);
                                    return 1;
                                })
                        )
                )

                .then(Commands.literal("kick")
                        .executes(ctx -> {
                            CommandSender sender = ctx.getSource().getSender();
                            if (!(sender instanceof Player)) {
                                sender.sendMessage(ColorUtils.parseWithPrefix(plugin.getLanguageManager().getString("admin.only_player")));
                                return 0;
                            }
                            sender.sendMessage(ColorUtils.parseWithPrefix(plugin.getLanguageManager().getString("party.usage_kick")));
                            return 1;
                        })
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
                                    CommandSender sender = ctx.getSource().getSender();
                                    if (!(sender instanceof Player p)) {
                                        sender.sendMessage(ColorUtils.parseWithPrefix(plugin.getLanguageManager().getString("admin.only_player")));
                                        return 0;
                                    }
                                    PartyManager.Party party = pm.getParty(p.getUniqueId());

                                    if (party == null || !party.getLeader().equals(p.getUniqueId())) {
                                        p.sendMessage(ColorUtils.parseWithPrefix(plugin.getLanguageManager().getString("party.not_leader")));
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

                                    if (targetId != null && targetId.equals(p.getUniqueId())) {
                                        p.sendMessage(ColorUtils.parseWithPrefix(plugin.getLanguageManager().getString("party.kick_self")));
                                        return 0;
                                    }

                                    if (targetId == null) {
                                        p.sendMessage(ColorUtils.parseWithPrefix(plugin.getLanguageManager().getString("party.player_not_in_party")));
                                        return 0;
                                    }

                                    pm.kickPlayer(party, targetId);

                                    Player targetOnline = Bukkit.getPlayer(targetId);
                                    if (targetOnline != null && targetOnline.isOnline()) {
                                        targetOnline.sendMessage(ColorUtils.parseWithPrefix(plugin.getLanguageManager().getString("party.kicked")));
                                    }

                                    String sysName = plugin.getConfigFile().getString("party.system-name", "System");
                                    pm.sendPartyMessage(party, sysName, plugin.getLanguageManager().getString("party.player_kicked").replace("<player>", targetName));
                                    return 1;
                                })
                        )
                )

                .then(Commands.literal("chat")
                        .executes(ctx -> {
                            CommandSender sender = ctx.getSource().getSender();
                            if (!(sender instanceof Player p)) {
                                sender.sendMessage(ColorUtils.parseWithPrefix(plugin.getLanguageManager().getString("admin.only_player")));
                                return 0;
                            }
                            PartyManager.Party party = pm.getParty(p.getUniqueId());
                            if (party == null) {
                                p.sendMessage(ColorUtils.parseWithPrefix(plugin.getLanguageManager().getString("party.not_in_party")));
                                return 0;
                            }
                            boolean enabled = pm.togglePartyChat(p.getUniqueId());
                            String state = enabled ? plugin.getLanguageManager().getString("editor.words.true_word") : plugin.getLanguageManager().getString("editor.words.false_word");
                            p.sendMessage(ColorUtils.parseWithPrefix(plugin.getLanguageManager().getString("party.chat_toggled").replace("<status>", state)));
                            return 1;
                        })
                )

                .then(Commands.literal("list").executes(ctx -> {
                    CommandSender sender = ctx.getSource().getSender();
                    if (!(sender instanceof Player p)) {
                        sender.sendMessage(ColorUtils.parseWithPrefix(plugin.getLanguageManager().getString("admin.only_player")));
                        return 0;
                    }
                    PartyManager.Party party = pm.getParty(p.getUniqueId());

                    if (party == null) {
                        p.sendMessage(ColorUtils.parseWithPrefix(plugin.getLanguageManager().getString("party.not_in_party")));
                        return 0;
                    }

                    String offlineTxt = plugin.getLanguageManager().getString("party.offline", "Offline");
                    String leaderSuffix = plugin.getLanguageManager().getString("party.leader_suffix", " (Leader)");

                    String members = party.getMembers().stream()
                            .map(id -> {
                                Player onlinePlayer = Bukkit.getPlayer(id);
                                String name = party.getMemberName(id);

                                String status = (onlinePlayer != null && onlinePlayer.isOnline()) ? "" : " <gray>[" + offlineTxt + "]";
                                String prefix = id.equals(party.getLeader()) ? leaderSuffix : "";

                                return name + prefix + status;
                            })
                            .collect(Collectors.joining(", "));

                    p.sendMessage(ColorUtils.parseWithPrefix(plugin.getLanguageManager().getString("party.list").replace("<members>", members)));
                    return 1;
                }))
                .build();

        event.registrar().register(partyNode, "SinceDungeon Party System", aliases);
    }
}