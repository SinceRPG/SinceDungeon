package net.danh.sinceDungeon.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.registrar.ReloadableRegistrarEvent;
import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.guis.top.TopGUI;
import net.danh.sinceDungeon.managers.LivesManager;
import net.danh.sinceDungeon.managers.TopManager;
import net.danh.sinceDungeon.models.DungeonGame;
import net.danh.sinceDungeon.utils.ColorUtils;
import net.danh.sinceDungeon.utils.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.UUID;

/**
 * Handles the registration and execution of the main /dungeon player commands.
 */
public class DungeonCommand {

    /**
     * Registers the Dungeon command for players.
     * The root command literal and aliases are dynamically loaded from the configuration.
     * All arguments are fully integrated with Tab Completion suggestions.
     *
     * @param plugin The main plugin instance.
     * @param event  The lifecycle registrar event.
     */
    public static void register(SinceDungeon plugin, ReloadableRegistrarEvent<Commands> event) {
        String commandName = plugin.getConfigFile().getString("commands.dungeon", "dungeon");
        List<String> aliases = plugin.getConfigFile().getStringList("commands.dungeon-aliases");

        LiteralCommandNode<CommandSourceStack> dungeonNode = Commands.literal(commandName)
                .then(Commands.literal("lives")
                        .executes(ctx -> {
                            if (ctx.getSource().getExecutor() instanceof Player p) {
                                LivesManager.PlayerLives l = plugin.getLivesManager().getLives(p.getUniqueId());
                                if (l != null) {
                                    String time = plugin.getConfigFile().getInt("lives.regen-interval-seconds", 3600) + "s";
                                    String msg = plugin.getLanguageManager().getString("lives.check")
                                            .replace("<current>", String.valueOf(l.getCurrentLives()))
                                            .replace("<max>", String.valueOf(l.getMaxLives()))
                                            .replace("<time>", time);
                                    p.sendMessage(ColorUtils.parseWithPrefix(msg));
                                }
                            }
                            return 1;
                        })
                )
                .then(Commands.literal("top")
                        .then(Commands.argument("map", StringArgumentType.string())
                                .suggests((ctx, builder) -> {
                                    String remaining = builder.getRemainingLowerCase();
                                    for (String mapName : plugin.getDungeonManager().getTemplates().keySet()) {
                                        if (plugin.getDungeonManager().getTemplates().get(mapName).isPublic()) {
                                            if (mapName.toLowerCase().contains(remaining)) {
                                                builder.suggest(mapName);
                                            }
                                        }
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> {
                                    if (ctx.getSource().getExecutor() instanceof Player p) {
                                        String map = StringArgumentType.getString(ctx, "map");
                                        if (plugin.getDungeonManager().getTemplates().containsKey(map)) {
                                            new TopGUI(plugin).openTopGUI(p, map, TopManager.TopCategory.FASTEST_TIME, 0);
                                        } else {
                                            p.sendMessage(ColorUtils.parseWithPrefix(plugin.getLanguageManager().getString("error.file_not_found").replace("<file>", map)));
                                        }
                                    } else {
                                        ctx.getSource().getSender().sendMessage(ColorUtils.parseWithPrefix(plugin.getLanguageManager().getString("admin.only_player")));
                                    }
                                    return 1;
                                })
                        )
                )
                .then(Commands.literal("join")
                        .then(Commands.argument("name", StringArgumentType.string())
                                .suggests((ctx, builder) -> {
                                    String remaining = builder.getRemainingLowerCase();
                                    for (String mapName : plugin.getDungeonManager().getTemplates().keySet()) {
                                        if (plugin.getDungeonManager().getTemplates().get(mapName).isPublic()) {
                                            if (mapName.toLowerCase().contains(remaining)) {
                                                builder.suggest(mapName);
                                            }
                                        }
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> {
                                    if (ctx.getSource().getExecutor() instanceof Player p) {
                                        plugin.getDungeonManager().joinDungeon(p, StringArgumentType.getString(ctx, "name"));
                                    } else {
                                        ctx.getSource().getSender().sendMessage(ColorUtils.parseWithPrefix(plugin.getLanguageManager().getString("admin.join_dungeon.console_error")));
                                    }
                                    return 1;
                                })
                                .then(Commands.argument("target", StringArgumentType.word())
                                        .requires(s -> s.getSender().hasPermission("SinceDungeon.admin") || !(s.getSender() instanceof Player))
                                        .suggests((ctx, builder) -> {
                                            String remaining = builder.getRemainingLowerCase();
                                            Bukkit.getOnlinePlayers().stream()
                                                    .map(Player::getName)
                                                    .filter(name -> name.toLowerCase().startsWith(remaining))
                                                    .forEach(builder::suggest);
                                            return builder.buildFuture();
                                        })
                                        .executes(ctx -> {
                                            String targetName = StringArgumentType.getString(ctx, "target");
                                            Player target = Bukkit.getPlayerExact(targetName);
                                            if (target != null) {
                                                plugin.getDungeonManager().joinDungeon(target, StringArgumentType.getString(ctx, "name"));
                                            } else {
                                                ctx.getSource().getSender().sendMessage(ColorUtils.parseWithPrefix(plugin.getLanguageManager().getString("admin.join_dungeon.not_found_player")
                                                        .replace("<player>", targetName)));
                                            }
                                            return 1;
                                        })
                                )
                        )
                )
                .then(Commands.literal("leave")
                        .executes(ctx -> {
                            if (ctx.getSource().getExecutor() instanceof Player p) {
                                DungeonGame game = plugin.getDungeonManager().getGame(p.getUniqueId());
                                if (game != null) {
                                    game.handlePlayerDisconnect(p, false);
                                    p.sendMessage(ColorUtils.parseWithPrefix(plugin.getLanguageManager().getString("party.left_dungeon_due_to_party")));
                                } else {
                                    p.sendMessage(ColorUtils.parseWithPrefix(plugin.getLanguageManager().getString("error.not_in_dungeon")));
                                }
                            } else {
                                ctx.getSource().getSender().sendMessage(ColorUtils.parseWithPrefix(plugin.getLanguageManager().getString("admin.only_admin")));
                            }
                            return 1;
                        })
                )
                .then(Commands.literal("revive")
                        .then(Commands.argument("target", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    if (ctx.getSource().getExecutor() instanceof Player p) {
                                        DungeonGame game = plugin.getDungeonManager().getGame(p.getUniqueId());
                                        if (game != null) {
                                            String remaining = builder.getRemainingLowerCase();
                                            for (Player member : game.getParticipants()) {
                                                if (member.getGameMode() == GameMode.SPECTATOR && member.getName().toLowerCase().startsWith(remaining)) {
                                                    builder.suggest(member.getName());
                                                }
                                            }
                                        }
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> {
                                    if (ctx.getSource().getExecutor() instanceof Player p) {
                                        Player target = Bukkit.getPlayerExact(StringArgumentType.getString(ctx, "target"));
                                        if (target == null) {
                                            p.sendMessage(ColorUtils.parseWithPrefix(plugin.getLanguageManager().getString("admin.invalid_player", "&cPlayer not found.")));
                                            return 0;
                                        }
                                        DungeonGame game = plugin.getDungeonManager().getGame(p.getUniqueId());
                                        if (game == null || !game.getParticipants().contains(target)) {
                                            p.sendMessage(ColorUtils.parseWithPrefix(plugin.getLanguageManager().getString("error.target_not_in_dungeon", "&cThat player is not in your dungeon!")));
                                            return 0;
                                        }
                                        if (target.getGameMode() != GameMode.SPECTATOR) {
                                            p.sendMessage(ColorUtils.parseWithPrefix(plugin.getLanguageManager().getString("error.target_not_spectator", "&cThat player is not knocked out!")));
                                            return 0;
                                        }

                                        NamespacedKey lifeKey = new NamespacedKey(plugin, "life_amount");
                                        ItemStack lifeItem = null;
                                        for (ItemStack item : p.getInventory().getContents()) {
                                            if (item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(lifeKey, PersistentDataType.INTEGER)) {
                                                lifeItem = item;
                                                break;
                                            }
                                        }

                                        if (lifeItem == null) {
                                            p.sendMessage(ColorUtils.parseWithPrefix(plugin.getLanguageManager().getString("error.no_life_item", "&cYou need a Soul Crystal in your inventory to revive someone!")));
                                            return 0;
                                        }

                                        lifeItem.setAmount(lifeItem.getAmount() - 1);
                                        plugin.getLivesManager().setLives(target.getUniqueId(), 1);
                                        target.setGameMode(GameMode.SURVIVAL);
                                        target.teleport(p.getLocation());

                                        AttributeInstance attr = target.getAttribute(Attribute.MAX_HEALTH);
                                        target.setHealth(attr != null ? attr.getValue() : 20.0);

                                        String msgTarget = plugin.getLanguageManager().getString("game.revived_target", "&aYou have been revived by <player>!");
                                        String msgSender = plugin.getLanguageManager().getString("game.revived_sender", "&aYou revived <player>!");
                                        target.sendMessage(ColorUtils.parseWithPrefix(msgTarget.replace("<player>", p.getName())));
                                        p.sendMessage(ColorUtils.parseWithPrefix(msgSender.replace("<player>", target.getName())));

                                        game.broadcastMessage("game.revived_broadcast", "<sender>", p.getName(), "<target>", target.getName());
                                    } else {
                                        ctx.getSource().getSender().sendMessage(ColorUtils.parseWithPrefix(plugin.getLanguageManager().getString("admin.only_player")));
                                    }
                                    return 1;
                                })
                        )
                )
                .then(Commands.literal("editor")
                        .requires(s -> s.getSender().hasPermission("SinceDungeon.admin"))
                        .executes(ctx -> {
                            if (ctx.getSource().getExecutor() instanceof Player p) {
                                plugin.getEditorManager().openEditor(p);
                            } else {
                                ctx.getSource().getSender().sendMessage(ColorUtils.parseWithPrefix(plugin.getLanguageManager().getString("admin.only_admin")));
                            }
                            return 1;
                        })
                )
                .then(Commands.literal("spectate")
                        .requires(s -> s.getSender().hasPermission("SinceDungeon.admin"))
                        .then(Commands.argument("target", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    String remaining = builder.getRemainingLowerCase();
                                    for (UUID uuid : plugin.getDungeonManager().getActiveGames().keySet()) {
                                        Player participant = Bukkit.getPlayer(uuid);
                                        if (participant != null && participant.getName().toLowerCase().startsWith(remaining)) {
                                            builder.suggest(participant.getName());
                                        }
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> {
                                    if (ctx.getSource().getExecutor() instanceof Player p) {
                                        String targetName = StringArgumentType.getString(ctx, "target");
                                        Player target = Bukkit.getPlayerExact(targetName);

                                        if (target != null) {
                                            DungeonGame targetGame = plugin.getDungeonManager().getGame(target.getUniqueId());
                                            if (targetGame != null && targetGame.getWorld() != null) {
                                                p.setGameMode(GameMode.SPECTATOR);
                                                p.teleportAsync(target.getLocation());

                                                String successMsg = plugin.getLanguageManager().getString("admin.spectate_success");
                                                if (successMsg != null) {
                                                    p.sendMessage(ColorUtils.parseWithPrefix(successMsg.replace("<player>", target.getName())));
                                                }
                                            } else {
                                                String notFoundMsg = plugin.getLanguageManager().getString("admin.target_not_in_dungeon");
                                                if (notFoundMsg != null) {
                                                    p.sendMessage(ColorUtils.parseWithPrefix(notFoundMsg));
                                                }
                                            }
                                        } else {
                                            p.sendMessage(ColorUtils.parseWithPrefix(plugin.getLanguageManager().getString("admin.invalid_player")));
                                        }
                                    } else {
                                        ctx.getSource().getSender().sendMessage(ColorUtils.parseWithPrefix(plugin.getLanguageManager().getString("admin.only_player")));
                                    }
                                    return 1;
                                })
                        )
                )
                .then(Commands.literal("getkey")
                        .requires(s -> s.getSender().hasPermission("SinceDungeon.admin"))
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    String remaining = builder.getRemainingLowerCase();
                                    if ("door_1".startsWith(remaining)) builder.suggest("door_1");
                                    if ("door_2".startsWith(remaining)) builder.suggest("door_2");
                                    if ("boss_key".startsWith(remaining)) builder.suggest("boss_key");
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> {
                                    if (ctx.getSource().getExecutor() instanceof Player p) {
                                        String keyId = StringArgumentType.getString(ctx, "id");
                                        NamespacedKey keyTag = new NamespacedKey(plugin, "dungeon_key_id");
                                        ConfigurationSection cfg = plugin.getConfigFile().getConfig().getConfigurationSection("dungeon-items.key");

                                        ItemStack keyItem = ItemBuilder.fromConfig(plugin, "dungeon-items.key", "TRIPWIRE_HOOK")
                                                .amount(1)
                                                .applyConfig(cfg, "&6&lDungeon Key", "<id>", keyId)
                                                .setTag(keyTag, PersistentDataType.STRING, keyId)
                                                .build();

                                        p.getInventory().addItem(keyItem);

                                        String successMsg = plugin.getLanguageManager().getString("admin.getkey_success");
                                        if (successMsg != null) {
                                            p.sendMessage(ColorUtils.parseWithPrefix(successMsg.replace("<id>", keyId)));
                                        }
                                    } else {
                                        ctx.getSource().getSender().sendMessage(ColorUtils.parseWithPrefix(plugin.getLanguageManager().getString("admin.only_player")));
                                    }
                                    return 1;
                                })
                        )
                )
                .build();

        event.registrar().register(dungeonNode, "SinceDungeon Player", aliases);
    }
}