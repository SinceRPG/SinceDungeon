package net.danh.sinceDungeon.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.registrar.ReloadableRegistrarEvent;
import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.guis.top.TopGUI;
import net.danh.sinceDungeon.managers.LivesManager;
import net.danh.sinceDungeon.managers.TopManager;
import net.danh.sinceDungeon.models.DungeonGame;
import net.danh.sinceDungeon.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

public class DungeonCommand {

    public static void register(SinceDungeon plugin, ReloadableRegistrarEvent<Commands> event) {
        event.registrar().register(Commands.literal("dungeon")
                .then(Commands.literal("lives")
                        .executes(ctx -> {
                            if (ctx.getSource().getExecutor() instanceof Player p) {
                                LivesManager.PlayerLives l = plugin.getLivesManager().getLives(p.getUniqueId());
                                if (l != null) {
                                    String time = plugin.getConfigFile().getInt("lives.regen-interval-seconds", 3600) + "s";
                                    String msg = plugin.getMessagesFile().getString("lives.check")
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
                                        if (plugin.getDungeonManager().getTemplates().get(mapName).isPublic())
                                            if (mapName.toLowerCase().contains(remaining)) {
                                                builder.suggest(mapName);
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
                                            p.sendMessage(ColorUtils.parseWithPrefix(plugin.getMessagesFile().getString("error.file_not_found").replace("<file>", map)));
                                        }
                                    } else {
                                        ctx.getSource().getSender().sendMessage(ColorUtils.parseWithPrefix(plugin.getMessagesFile().getString("admin.only_player")));
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
                                        if (plugin.getDungeonManager().getTemplates().get(mapName).isPublic())
                                            if (mapName.toLowerCase().contains(remaining)) {
                                                builder.suggest(mapName);
                                            }
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> {
                                    if (ctx.getSource().getExecutor() instanceof Player p) {
                                        plugin.getDungeonManager().joinDungeon(p, StringArgumentType.getString(ctx, "name"));
                                    } else {
                                        ctx.getSource().getSender().sendMessage(ColorUtils.parseWithPrefix(plugin.getMessagesFile().getString("admin.join_dungeon.console_error")));
                                    }
                                    return 1;
                                })
                                .then(Commands.argument("target", StringArgumentType.word())
                                        .requires(s -> s.getSender().hasPermission("SinceDungeon.admin") || !(s.getSender() instanceof Player))
                                        .executes(ctx -> {
                                            String targetName = StringArgumentType.getString(ctx, "target");
                                            Player target = Bukkit.getPlayerExact(targetName);
                                            if (target != null) {
                                                plugin.getDungeonManager().joinDungeon(target, StringArgumentType.getString(ctx, "name"));
                                            } else {
                                                ctx.getSource().getSender().sendMessage(ColorUtils.parseWithPrefix(plugin.getMessagesFile().getString("admin.join_dungeon.not_found_player")
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
                                    game.handlePlayerDisconnect(p);
                                    p.sendMessage(ColorUtils.parseWithPrefix(plugin.getMessagesFile().getString("party.left_dungeon_due_to_party", "<yellow>Bạn đã thoát khỏi Dungeon.")));
                                } else {
                                    p.sendMessage(ColorUtils.parseWithPrefix(plugin.getMessagesFile().getString("error.not_in_dungeon", "<red>Bạn hiện không ở trong Dungeon nào.")));
                                }
                            } else {
                                ctx.getSource().getSender().sendMessage(ColorUtils.parseWithPrefix(plugin.getMessagesFile().getString("admin.only_admin")));
                            }
                            return 1;
                        })
                )
                .then(Commands.literal("editor")
                        .requires(s -> s.getSender().hasPermission("SinceDungeon.admin"))
                        .executes(ctx -> {
                            if (ctx.getSource().getExecutor() instanceof Player p) {
                                plugin.getEditorManager().openEditor(p);
                            } else {
                                ctx.getSource().getSender().sendMessage(ColorUtils.parseWithPrefix(plugin.getMessagesFile().getString("admin.only_admin")));
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
                                                p.setGameMode(org.bukkit.GameMode.SPECTATOR);
                                                p.teleportAsync(target.getLocation());

                                                String successMsg = plugin.getMessagesFile().getString("admin.spectate_success", "&aSpectating!");
                                                p.sendMessage(ColorUtils.parseWithPrefix(successMsg.replace("<player>", target.getName())));
                                            } else {
                                                String notFoundMsg = plugin.getMessagesFile().getString("admin.target_not_in_dungeon", "&cNot in dungeon!");
                                                p.sendMessage(ColorUtils.parseWithPrefix(notFoundMsg));
                                            }
                                        } else {
                                            p.sendMessage(ColorUtils.parseWithPrefix(plugin.getMessagesFile().getString("admin.invalid_player")));
                                        }
                                    } else {
                                        ctx.getSource().getSender().sendMessage(ColorUtils.parseWithPrefix(plugin.getMessagesFile().getString("admin.only_player")));
                                    }
                                    return 1;
                                })
                        )
                )
                .then(Commands.literal("getkey")
                        .requires(s -> s.getSender().hasPermission("SinceDungeon.admin"))
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(ctx -> {
                                    if (ctx.getSource().getExecutor() instanceof Player p) {
                                        String keyId = StringArgumentType.getString(ctx, "id");
                                        org.bukkit.NamespacedKey keyTag = new org.bukkit.NamespacedKey(plugin, "dungeon_key_id");
                                        org.bukkit.configuration.ConfigurationSection cfg = plugin.getConfigFile().getConfig().getConfigurationSection("dungeon-items.key");

                                        org.bukkit.inventory.ItemStack keyItem = net.danh.sinceDungeon.utils.ItemBuilder.fromConfig(plugin, "dungeon-items.key", "TRIPWIRE_HOOK")
                                                .amount(1)
                                                .applyConfig(cfg, "&6&lDungeon Key", "<id>", keyId)
                                                .setTag(keyTag, org.bukkit.persistence.PersistentDataType.STRING, keyId)
                                                .build();

                                        p.getInventory().addItem(keyItem);

                                        String successMsg = plugin.getMessagesFile().getString("admin.getkey_success", "&aReceived system key: &e<id>");
                                        p.sendMessage(net.danh.sinceDungeon.utils.ColorUtils.parseWithPrefix(successMsg.replace("<id>", keyId)));
                                    } else {
                                        ctx.getSource().getSender().sendMessage(ColorUtils.parseWithPrefix(plugin.getMessagesFile().getString("admin.only_player")));
                                    }
                                    return 1;
                                })
                        )
                )
                .build(), "SinceDungeon Player"
        );
    }
}