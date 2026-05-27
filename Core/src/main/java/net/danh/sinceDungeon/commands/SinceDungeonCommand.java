package net.danh.sinceDungeon.commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.registrar.ReloadableRegistrarEvent;
import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.api.SinceDungeonAPI;
import net.danh.sinceDungeon.managers.LivesManager;
import net.danh.sinceDungeon.utils.ColorUtils;
import net.danh.sinceDungeon.utils.ItemBuilder;
import net.danh.sinceDungeon.utils.SchedulerCompat;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Handles the registration and execution of administrative /sincedungeon commands.
 * Natively supports the 'stage insert' command to manipulate YAML configurations safely.
 */
public class SinceDungeonCommand {

    public static void register(SinceDungeon plugin, ReloadableRegistrarEvent<Commands> event) {
        String commandName = plugin.getConfigFile().getString("commands.admin", "sincedungeon");
        List<String> aliases = plugin.getConfigFile().getStringList("commands.admin-aliases");

        LiteralCommandNode<CommandSourceStack> adminNode = Commands.literal(commandName)
                .requires(s -> s.getSender().hasPermission("SinceDungeon.admin"))
                .then(Commands.literal("reload")
                        .executes(ctx -> {
                            plugin.reloadFiles(ctx.getSource().getSender());
                            return 1;
                        })
                )
                .then(Commands.literal("stage")
                        .then(Commands.literal("insert")
                                .then(Commands.argument("map_id", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            String remaining = builder.getRemainingLowerCase();
                                            for (String mapId : SinceDungeonAPI.get().getAvailableTemplates()) {
                                                if (mapId.toLowerCase().startsWith(remaining)) {
                                                    builder.suggest(mapId);
                                                }
                                            }
                                            return builder.buildFuture();
                                        })
                                        .then(Commands.argument("position", IntegerArgumentType.integer(1))
                                                .executes(ctx -> {
                                                    String mapId = StringArgumentType.getString(ctx, "map_id");
                                                    int pos = IntegerArgumentType.getInteger(ctx, "position");

                                                    File file = new File(plugin.getDataFolder(), "dungeons/" + mapId + ".yml");
                                                    if (!file.exists()) {
                                                        if (ctx.getSource().getExecutor() instanceof Player p) {
                                                            String msg = plugin.getLanguageManager().getString("admin.invalid_map", "&cCannot find the requested Dungeon Map file.");
                                                            p.sendMessage(ColorUtils.parseWithPrefix(msg));
                                                        }
                                                        return 0;
                                                    }

                                                    YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
                                                    ConfigurationSection sec = config.getConfigurationSection("stages");

                                                    if (sec != null) {
                                                        List<Integer> keys = new ArrayList<>();
                                                        for (String k : sec.getKeys(false)) {
                                                            try {
                                                                keys.add(Integer.parseInt(k));
                                                            } catch (Exception ignored) {
                                                            }
                                                        }
                                                        keys.sort(Collections.reverseOrder());
                                                        for (int k : keys) {
                                                            if (k >= pos) {
                                                                config.set("stages." + (k + 1), config.get("stages." + k));
                                                                config.set("stages." + k, null);
                                                            }
                                                        }
                                                    }

                                                    config.createSection("stages." + pos + ".actions");
                                                    config.set("stages." + pos + ".chance", 100.0);
                                                    config.set("stages." + pos + ".commands", new ArrayList<String>());

                                                    try {
                                                        config.save(file);
                                                        if (ctx.getSource().getExecutor() instanceof Player p) {
                                                            String msg = plugin.getLanguageManager().getString("admin.stage_inserted", "&aSuccessfully shifted configuration and inserted Stage <pos> into <map>!");
                                                            p.sendMessage(ColorUtils.parseWithPrefix(msg.replace("<pos>", String.valueOf(pos)).replace("<map>", mapId)));
                                                        }
                                                    } catch (IOException e) {
                                                        plugin.getLogger().warning(plugin.getLanguageManager().getString("admin.log.stage_save_fail", "Failed to save stage shift data!"));
                                                    }

                                                    return 1;
                                                })
                                        )
                                )
                        )
                )
                .then(Commands.literal("top")
                        .then(Commands.literal("reset")
                                .then(Commands.argument("map", StringArgumentType.string())
                                        .suggests((ctx, builder) -> {
                                            String remaining = builder.getRemainingLowerCase();
                                            for (String mapName : plugin.getDungeonManager().getTemplates().keySet()) {
                                                if (mapName.toLowerCase().contains(remaining)) {
                                                    builder.suggest(mapName);
                                                }
                                            }
                                            return builder.buildFuture();
                                        })
                                        .executes(ctx -> {
                                            CommandSender sender = ctx.getSource().getSender();
                                            String map = StringArgumentType.getString(ctx, "map");

                                            if (!plugin.getDungeonManager().getTemplates().containsKey(map)) {
                                                sender.sendMessage(ColorUtils.parseWithPrefix(plugin.getLanguageManager().getString("error.file_not_found").replace("<file>", map)));
                                                return 0;
                                            }

                                            plugin.getTopManager().resetLeaderboard(map);

                                            String msg = plugin.getLanguageManager().getString("admin.top_reset_success", "&aSuccessfully reset the leaderboard for map: &e<map>");
                                            sender.sendMessage(ColorUtils.parseWithPrefix(msg.replace("<map>", map)));
                                            return 1;
                                        })
                                )
                        )
                        .then(Commands.literal("resetplayer")
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
                                            CommandSender sender = ctx.getSource().getSender();
                                            String targetName = StringArgumentType.getString(ctx, "target");

                                            // Execute async to prevent blocking main thread for OfflinePlayer fetch
                                            SchedulerCompat.runAsync(plugin, () -> {
                                                OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
                                                plugin.getTopManager().resetPlayerLeaderboard(target.getUniqueId(), null);

                                                String msg = plugin.getLanguageManager().getString("admin.top_reset_player_all_success", "&aSuccessfully reset leaderboards for player &e<player> &aacross all maps.");
                                                sender.sendMessage(ColorUtils.parseWithPrefix(msg.replace("<player>", target.getName() == null ? targetName : target.getName())));
                                            });
                                            return 1;
                                        })
                                        .then(Commands.argument("map", StringArgumentType.string())
                                                .suggests((ctx, builder) -> {
                                                    String remaining = builder.getRemainingLowerCase();
                                                    for (String mapName : plugin.getDungeonManager().getTemplates().keySet()) {
                                                        if (mapName.toLowerCase().contains(remaining)) {
                                                            builder.suggest(mapName);
                                                        }
                                                    }
                                                    return builder.buildFuture();
                                                })
                                                .executes(ctx -> {
                                                    CommandSender sender = ctx.getSource().getSender();
                                                    String targetName = StringArgumentType.getString(ctx, "target");
                                                    String map = StringArgumentType.getString(ctx, "map");

                                                    if (!plugin.getDungeonManager().getTemplates().containsKey(map)) {
                                                        sender.sendMessage(ColorUtils.parseWithPrefix(plugin.getLanguageManager().getString("error.file_not_found").replace("<file>", map)));
                                                        return 0;
                                                    }

                                                    SchedulerCompat.runAsync(plugin, () -> {
                                                        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
                                                        plugin.getTopManager().resetPlayerLeaderboard(target.getUniqueId(), map);

                                                        String msg = plugin.getLanguageManager().getString("admin.top_reset_player_success", "&aSuccessfully reset leaderboards for player &e<player> &aon map: &e<map>");
                                                        sender.sendMessage(ColorUtils.parseWithPrefix(msg.replace("<player>", target.getName() == null ? targetName : target.getName()).replace("<map>", map)));
                                                    });
                                                    return 1;
                                                })
                                        )
                                )
                        )
                )
                .then(Commands.literal("lives")
                        .then(Commands.argument("target", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    String remaining = builder.getRemainingLowerCase();
                                    Bukkit.getOnlinePlayers().stream()
                                            .map(Player::getName)
                                            .filter(name -> name.toLowerCase().startsWith(remaining))
                                            .forEach(builder::suggest);
                                    return builder.buildFuture();
                                })
                                .then(Commands.literal("add").then(Commands.argument("amount", IntegerArgumentType.integer())
                                        .suggests((ctx, builder) -> {
                                            builder.suggest("1");
                                            builder.suggest("3");
                                            builder.suggest("5");
                                            return builder.buildFuture();
                                        })
                                        .executes(ctx -> executeLivesAdd(plugin, ctx))
                                        .then(Commands.literal("-s").executes(ctx -> executeLivesAdd(plugin, ctx)))
                                ))
                                .then(Commands.literal("reduce").then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                        .suggests((ctx, builder) -> {
                                            builder.suggest("1");
                                            builder.suggest("3");
                                            builder.suggest("5");
                                            return builder.buildFuture();
                                        })
                                        .executes(ctx -> executeLivesReduce(plugin, ctx))
                                        .then(Commands.literal("-s").executes(ctx -> executeLivesReduce(plugin, ctx)))
                                ))
                                .then(Commands.literal("set").then(Commands.argument("amount", IntegerArgumentType.integer())
                                        .suggests((ctx, builder) -> {
                                            builder.suggest("1");
                                            builder.suggest("3");
                                            builder.suggest("5");
                                            return builder.buildFuture();
                                        })
                                        .executes(ctx -> executeLivesSet(plugin, ctx))
                                        .then(Commands.literal("-s").executes(ctx -> executeLivesSet(plugin, ctx)))
                                ))
                                .then(Commands.literal("addmax").then(Commands.argument("amount", IntegerArgumentType.integer())
                                        .suggests((ctx, builder) -> {
                                            builder.suggest("1");
                                            builder.suggest("3");
                                            builder.suggest("5");
                                            return builder.buildFuture();
                                        })
                                        .executes(ctx -> executeLivesAddMax(plugin, ctx))
                                        .then(Commands.literal("-s").executes(ctx -> executeLivesAddMax(plugin, ctx)))
                                ))
                                .then(Commands.literal("setregenamount").then(Commands.argument("amount", IntegerArgumentType.integer())
                                        .suggests((ctx, builder) -> {
                                            builder.suggest("1");
                                            builder.suggest("2");
                                            return builder.buildFuture();
                                        })
                                        .executes(ctx -> executeLivesSetRegenAmount(plugin, ctx))
                                        .then(Commands.literal("-s").executes(ctx -> executeLivesSetRegenAmount(plugin, ctx)))
                                ))
                                .then(Commands.literal("setregeninterval").then(Commands.argument("seconds", IntegerArgumentType.integer())
                                        .suggests((ctx, builder) -> {
                                            builder.suggest("1800");
                                            builder.suggest("3600");
                                            builder.suggest("7200");
                                            return builder.buildFuture();
                                        })
                                        .executes(ctx -> executeLivesSetRegenInterval(plugin, ctx))
                                        .then(Commands.literal("-s").executes(ctx -> executeLivesSetRegenInterval(plugin, ctx)))
                                ))
                                .then(Commands.literal("resetregen")
                                        .executes(ctx -> executeLivesResetRegen(plugin, ctx))
                                        .then(Commands.literal("-s").executes(ctx -> executeLivesResetRegen(plugin, ctx)))
                                )
                                .then(Commands.literal("check")
                                        .executes(ctx -> executeLivesCheck(plugin, ctx))
                                        .then(Commands.literal("-s").executes(ctx -> executeLivesCheck(plugin, ctx)))
                                )
                        )
                )
                .then(Commands.literal("givelifeitem")
                        .then(Commands.argument("target", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    String remaining = builder.getRemainingLowerCase();
                                    Bukkit.getOnlinePlayers().stream()
                                            .map(Player::getName)
                                            .filter(name -> name.toLowerCase().startsWith(remaining))
                                            .forEach(builder::suggest);
                                    return builder.buildFuture();
                                })
                                .then(Commands.argument("amount", IntegerArgumentType.integer())
                                        .suggests((ctx, builder) -> {
                                            builder.suggest("1");
                                            builder.suggest("64");
                                            return builder.buildFuture();
                                        })
                                        .executes(ctx -> executeGiveLifeItem(plugin, ctx))
                                        .then(Commands.literal("-s").executes(ctx -> executeGiveLifeItem(plugin, ctx)))
                                )
                        )
                )
                .then(Commands.literal("cooldown")
                        .then(Commands.literal("check")
                                .then(Commands.argument("target", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            String remaining = builder.getRemainingLowerCase();
                                            Bukkit.getOnlinePlayers().stream()
                                                    .map(Player::getName)
                                                    .filter(name -> name.toLowerCase().startsWith(remaining))
                                                    .forEach(builder::suggest);
                                            return builder.buildFuture();
                                        })
                                        .then(Commands.argument("map", StringArgumentType.string())
                                                .suggests((ctx, builder) -> {
                                                    String remaining = builder.getRemainingLowerCase();
                                                    for (String mapName : plugin.getDungeonManager().getTemplates().keySet()) {
                                                        if (mapName.toLowerCase().contains(remaining)) {
                                                            builder.suggest(mapName);
                                                        }
                                                    }
                                                    return builder.buildFuture();
                                                })
                                                .executes(ctx -> {
                                                    Player target = Bukkit.getPlayerExact(StringArgumentType.getString(ctx, "target"));
                                                    if (target == null) {
                                                        ctx.getSource().getSender().sendMessage(ColorUtils.parseWithPrefix(plugin.getLanguageManager().getString("admin.invalid_player", "&cPlayer not found.")));
                                                        return 0;
                                                    }
                                                    String map = StringArgumentType.getString(ctx, "map");

                                                    if (plugin.getCooldownManager().isOnCooldown(target.getUniqueId(), map)) {
                                                        String time = plugin.getCooldownManager().getRemainingTimeFormatted(target.getUniqueId(), map);
                                                        String msg = plugin.getLanguageManager().getString("cooldown.check_other", "&e<player>'s cooldown for <map>: &c<time>");
                                                        ctx.getSource().getSender().sendMessage(ColorUtils.parseWithPrefix(msg.replace("<player>", target.getName()).replace("<map>", map).replace("<time>", time)));
                                                    } else {
                                                        String msg = plugin.getLanguageManager().getString("cooldown.check_other_ready", "&e<player> is ready to enter <map>.");
                                                        ctx.getSource().getSender().sendMessage(ColorUtils.parseWithPrefix(msg.replace("<player>", target.getName()).replace("<map>", map)));
                                                    }
                                                    return 1;
                                                })
                                        )
                                )
                        )
                        .then(Commands.literal("resetall")
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
                                            Player target = Bukkit.getPlayerExact(StringArgumentType.getString(ctx, "target"));
                                            if (target == null) {
                                                ctx.getSource().getSender().sendMessage(ColorUtils.parseWithPrefix(plugin.getLanguageManager().getString("admin.invalid_player", "&cPlayer not found.")));
                                                return 0;
                                            }

                                            plugin.getCooldownManager().resetAllCooldowns(target.getUniqueId());

                                            String successMsg = plugin.getLanguageManager().getString("cooldown.admin_reset_all", "&aReset all cooldowns for <player>.");
                                            ctx.getSource().getSender().sendMessage(ColorUtils.parseWithPrefix(successMsg.replace("<player>", target.getName())));
                                            return 1;
                                        })
                                )
                        )
                        .then(Commands.literal("reduce")
                                .then(Commands.argument("target", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            String remaining = builder.getRemainingLowerCase();
                                            Bukkit.getOnlinePlayers().stream()
                                                    .map(Player::getName)
                                                    .filter(name -> name.toLowerCase().startsWith(remaining))
                                                    .forEach(builder::suggest);
                                            return builder.buildFuture();
                                        })
                                        .then(Commands.argument("seconds", IntegerArgumentType.integer())
                                                .suggests((ctx, builder) -> {
                                                    builder.suggest("60");
                                                    builder.suggest("300");
                                                    builder.suggest("1800");
                                                    return builder.buildFuture();
                                                })
                                                .executes(ctx -> {
                                                    Player target = Bukkit.getPlayerExact(StringArgumentType.getString(ctx, "target"));
                                                    if (target == null) {
                                                        ctx.getSource().getSender().sendMessage(ColorUtils.parseWithPrefix(plugin.getLanguageManager().getString("admin.invalid_player", "&cPlayer not found.")));
                                                        return 0;
                                                    }

                                                    int seconds = IntegerArgumentType.getInteger(ctx, "seconds");
                                                    plugin.getCooldownManager().reduceAllCooldowns(target.getUniqueId(), seconds);

                                                    String successMsg = plugin.getLanguageManager().getString("cooldown.admin_reduced", "&aReduced <player>'s cooldowns by <time>s.");
                                                    ctx.getSource().getSender().sendMessage(ColorUtils.parseWithPrefix(successMsg.replace("<player>", target.getName()).replace("<time>", String.valueOf(seconds))));
                                                    return 1;
                                                })
                                        )
                                )
                        )
                        .then(Commands.literal("reset")
                                .then(Commands.argument("target", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            String remaining = builder.getRemainingLowerCase();
                                            Bukkit.getOnlinePlayers().stream()
                                                    .map(Player::getName)
                                                    .filter(name -> name.toLowerCase().startsWith(remaining))
                                                    .forEach(builder::suggest);
                                            return builder.buildFuture();
                                        })
                                        .then(Commands.argument("map", StringArgumentType.string())
                                                .suggests((ctx, builder) -> {
                                                    String remaining = builder.getRemainingLowerCase();
                                                    for (String mapName : plugin.getDungeonManager().getTemplates().keySet()) {
                                                        if (mapName.toLowerCase().contains(remaining)) {
                                                            builder.suggest(mapName);
                                                        }
                                                    }
                                                    return builder.buildFuture();
                                                })
                                                .executes(ctx -> {
                                                    Player target = Bukkit.getPlayerExact(StringArgumentType.getString(ctx, "target"));
                                                    if (target == null) {
                                                        ctx.getSource().getSender().sendMessage(ColorUtils.parseWithPrefix(plugin.getLanguageManager().getString("admin.invalid_player", "&cPlayer not found.")));
                                                        return 0;
                                                    }

                                                    String map = StringArgumentType.getString(ctx, "map");
                                                    plugin.getCooldownManager().resetCooldown(target.getUniqueId(), map);

                                                    String successMsg = plugin.getLanguageManager().getString("admin.cooldown_reset_success", "&aSuccessfully reset dungeon cooldown for <player> in map <map>!");
                                                    ctx.getSource().getSender().sendMessage(ColorUtils.parseWithPrefix(successMsg.replace("<player>", target.getName()).replace("<map>", map)));
                                                    return 1;
                                                })
                                        )
                                )
                        )
                )
                .then(Commands.literal("givecooldownitem")
                        .then(Commands.literal("reset")
                                .then(Commands.argument("target", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            String remaining = builder.getRemainingLowerCase();
                                            Bukkit.getOnlinePlayers().stream()
                                                    .map(Player::getName)
                                                    .filter(name -> name.toLowerCase().startsWith(remaining))
                                                    .forEach(builder::suggest);
                                            return builder.buildFuture();
                                        })
                                        .then(Commands.argument("amount", IntegerArgumentType.integer())
                                                .suggests((ctx, builder) -> {
                                                    builder.suggest("1");
                                                    builder.suggest("64");
                                                    return builder.buildFuture();
                                                })
                                                .executes(ctx -> {
                                                    Player target = Bukkit.getPlayerExact(StringArgumentType.getString(ctx, "target"));
                                                    if (target == null) {
                                                        ctx.getSource().getSender().sendMessage(ColorUtils.parseWithPrefix(plugin.getLanguageManager().getString("admin.invalid_player")));
                                                        return 0;
                                                    }

                                                    int amount = IntegerArgumentType.getInteger(ctx, "amount");
                                                    NamespacedKey resetKey = new NamespacedKey(plugin, "cooldown_reset");
                                                    ConfigurationSection cfg = plugin.getConfigFile().getSection("items.cooldown_reset");

                                                    ItemStack item = ItemBuilder.fromConfig(plugin, "items.cooldown_reset", "PAPER")
                                                            .amount(amount)
                                                            .applyConfig(cfg, "&e&lCooldown Reset Ticket")
                                                            .setTag(resetKey, PersistentDataType.BYTE, (byte) 1)
                                                            .build();

                                                    target.getInventory().addItem(item);

                                                    target.sendMessage(ColorUtils.parseWithPrefix(plugin.getLanguageManager().getString("cooldown.item_reset_received").replace("<amount>", String.valueOf(amount))));
                                                    ctx.getSource().getSender().sendMessage(ColorUtils.parseWithPrefix(plugin.getLanguageManager().getString("cooldown.admin_gave_reset").replace("<amount>", String.valueOf(amount)).replace("<player>", target.getName())));
                                                    return 1;
                                                })
                                        )
                                )
                        )
                        .then(Commands.literal("reduce")
                                .then(Commands.argument("target", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            String remaining = builder.getRemainingLowerCase();
                                            Bukkit.getOnlinePlayers().stream()
                                                    .map(Player::getName)
                                                    .filter(name -> name.toLowerCase().startsWith(remaining))
                                                    .forEach(builder::suggest);
                                            return builder.buildFuture();
                                        })
                                        .then(Commands.argument("amount", IntegerArgumentType.integer())
                                                .suggests((ctx, builder) -> {
                                                    builder.suggest("1");
                                                    builder.suggest("64");
                                                    return builder.buildFuture();
                                                })
                                                .then(Commands.argument("seconds", IntegerArgumentType.integer())
                                                        .suggests((ctx, builder) -> {
                                                            builder.suggest("300");
                                                            builder.suggest("1800");
                                                            return builder.buildFuture();
                                                        })
                                                        .executes(ctx -> {
                                                            Player target = Bukkit.getPlayerExact(StringArgumentType.getString(ctx, "target"));
                                                            if (target == null) {
                                                                ctx.getSource().getSender().sendMessage(ColorUtils.parseWithPrefix(plugin.getLanguageManager().getString("admin.invalid_player")));
                                                                return 0;
                                                            }

                                                            int amount = IntegerArgumentType.getInteger(ctx, "amount");
                                                            int seconds = IntegerArgumentType.getInteger(ctx, "seconds");

                                                            NamespacedKey reduceKey = new NamespacedKey(plugin, "cooldown_reduce");
                                                            ConfigurationSection cfg = plugin.getConfigFile().getSection("items.cooldown_reduce");

                                                            ItemStack item = ItemBuilder.fromConfig(plugin, "items.cooldown_reduce", "CLOCK")
                                                                    .amount(amount)
                                                                    .applyConfig(cfg, "&a&lTime Skip Ticket", "<time>", String.valueOf(seconds))
                                                                    .setTag(reduceKey, PersistentDataType.INTEGER, seconds)
                                                                    .build();

                                                            target.getInventory().addItem(item);

                                                            target.sendMessage(ColorUtils.parseWithPrefix(plugin.getLanguageManager().getString("cooldown.item_reduce_received").replace("<amount>", String.valueOf(amount)).replace("<time>", String.valueOf(seconds))));
                                                            ctx.getSource().getSender().sendMessage(ColorUtils.parseWithPrefix(plugin.getLanguageManager().getString("cooldown.admin_gave_reduce").replace("<amount>", String.valueOf(amount)).replace("<time>", String.valueOf(seconds)).replace("<player>", target.getName())));
                                                            return 1;
                                                        })
                                                )
                                        )
                                )
                        )
                )
                .build();

        event.registrar().register(adminNode, "SinceDungeon Admin", aliases);
    }

    private static boolean isSilent(CommandContext<CommandSourceStack> ctx) {
        return ctx.getInput().trim().endsWith(" -s");
    }

    private static void send(CommandContext<CommandSourceStack> ctx, String message) {
        if (!isSilent(ctx)) {
            ctx.getSource().getSender().sendMessage(ColorUtils.parseWithPrefix(message));
        }
    }

    private static int executeLivesAdd(SinceDungeon plugin, CommandContext<CommandSourceStack> ctx) {
        Player target = Bukkit.getPlayerExact(StringArgumentType.getString(ctx, "target"));
        if (target == null) {
            send(ctx, plugin.getLanguageManager().getString("lives.player_not_found"));
            return 0;
        }
        int amount = IntegerArgumentType.getInteger(ctx, "amount");
        plugin.getLivesManager().addLives(target.getUniqueId(), amount);
        send(ctx, plugin.getLanguageManager().getString("lives.admin_add")
                .replace("<amount>", String.valueOf(amount))
                .replace("<player>", target.getName()));
        return 1;
    }

    private static int executeLivesReduce(SinceDungeon plugin, CommandContext<CommandSourceStack> ctx) {
        Player target = Bukkit.getPlayerExact(StringArgumentType.getString(ctx, "target"));
        if (target == null) {
            send(ctx, plugin.getLanguageManager().getString("lives.player_not_found"));
            return 0;
        }
        int amount = IntegerArgumentType.getInteger(ctx, "amount");
        LivesManager.PlayerLives lives = plugin.getLivesManager().getLives(target.getUniqueId());
        if (lives == null) {
            send(ctx, plugin.getLanguageManager().getString("lives.player_not_found"));
            return 0;
        }
        int before = lives.getCurrentLives();
        plugin.getLivesManager().removeLives(target.getUniqueId(), amount);
        int reduced = before - lives.getCurrentLives();
        send(ctx, plugin.getLanguageManager().getString("lives.admin_reduce")
                .replace("<amount>", String.valueOf(reduced))
                .replace("<player>", target.getName())
                .replace("<current>", String.valueOf(lives.getCurrentLives()))
                .replace("<max>", String.valueOf(lives.getMaxLives())));
        return 1;
    }

    private static int executeLivesSet(SinceDungeon plugin, CommandContext<CommandSourceStack> ctx) {
        Player target = Bukkit.getPlayerExact(StringArgumentType.getString(ctx, "target"));
        if (target == null) {
            send(ctx, plugin.getLanguageManager().getString("lives.player_not_found"));
            return 0;
        }
        int amount = IntegerArgumentType.getInteger(ctx, "amount");
        plugin.getLivesManager().setLives(target.getUniqueId(), amount);
        send(ctx, plugin.getLanguageManager().getString("lives.admin_set")
                .replace("<amount>", String.valueOf(amount))
                .replace("<player>", target.getName()));
        return 1;
    }

    private static int executeLivesAddMax(SinceDungeon plugin, CommandContext<CommandSourceStack> ctx) {
        Player target = Bukkit.getPlayerExact(StringArgumentType.getString(ctx, "target"));
        if (target == null) {
            send(ctx, plugin.getLanguageManager().getString("lives.player_not_found"));
            return 0;
        }
        int amount = IntegerArgumentType.getInteger(ctx, "amount");
        plugin.getLivesManager().addMaxLives(target.getUniqueId(), amount);
        send(ctx, plugin.getLanguageManager().getString("lives.admin_addmax")
                .replace("<amount>", String.valueOf(amount))
                .replace("<player>", target.getName()));
        return 1;
    }

    private static int executeLivesSetRegenAmount(SinceDungeon plugin, CommandContext<CommandSourceStack> ctx) {
        Player target = Bukkit.getPlayerExact(StringArgumentType.getString(ctx, "target"));
        if (target == null) {
            send(ctx, plugin.getLanguageManager().getString("lives.player_not_found"));
            return 0;
        }
        int amount = IntegerArgumentType.getInteger(ctx, "amount");
        plugin.getLivesManager().setCustomRegenAmount(target.getUniqueId(), amount);
        send(ctx, plugin.getLanguageManager().getString("lives.admin_set_regen_amount")
                .replace("<amount>", String.valueOf(amount))
                .replace("<player>", target.getName()));
        return 1;
    }

    private static int executeLivesSetRegenInterval(SinceDungeon plugin, CommandContext<CommandSourceStack> ctx) {
        Player target = Bukkit.getPlayerExact(StringArgumentType.getString(ctx, "target"));
        if (target == null) {
            send(ctx, plugin.getLanguageManager().getString("lives.player_not_found"));
            return 0;
        }
        int seconds = IntegerArgumentType.getInteger(ctx, "seconds");
        plugin.getLivesManager().setCustomRegenInterval(target.getUniqueId(), seconds);
        send(ctx, plugin.getLanguageManager().getString("lives.admin_set_regen_interval")
                .replace("<amount>", String.valueOf(seconds))
                .replace("<player>", target.getName()));
        return 1;
    }

    private static int executeLivesResetRegen(SinceDungeon plugin, CommandContext<CommandSourceStack> ctx) {
        Player target = Bukkit.getPlayerExact(StringArgumentType.getString(ctx, "target"));
        if (target == null) {
            send(ctx, plugin.getLanguageManager().getString("lives.player_not_found"));
            return 0;
        }
        plugin.getLivesManager().setCustomRegenAmount(target.getUniqueId(), -1);
        plugin.getLivesManager().setCustomRegenInterval(target.getUniqueId(), -1);
        send(ctx, plugin.getLanguageManager().getString("lives.admin_reset_regen")
                .replace("<player>", target.getName()));
        return 1;
    }

    private static int executeLivesCheck(SinceDungeon plugin, CommandContext<CommandSourceStack> ctx) {
        Player target = Bukkit.getPlayerExact(StringArgumentType.getString(ctx, "target"));
        if (target == null) {
            send(ctx, plugin.getLanguageManager().getString("lives.player_not_found"));
            return 0;
        }
        LivesManager.PlayerLives lives = plugin.getLivesManager().getLives(target.getUniqueId());
        if (lives == null) {
            send(ctx, plugin.getLanguageManager().getString("lives.player_not_found"));
            return 0;
        }
        int interval = lives.getCustomRegenInterval() != -1 ? lives.getCustomRegenInterval() : plugin.getConfigFile().getInt("lives.regen-interval-seconds", 3600);
        int amount = lives.getCustomRegenAmount() != -1 ? lives.getCustomRegenAmount() : plugin.getConfigFile().getInt("lives.regen-amount", 1);
        send(ctx, plugin.getLanguageManager().getString("lives.check_other")
                .replace("<player>", target.getName())
                .replace("<current>", String.valueOf(lives.getCurrentLives()))
                .replace("<max>", String.valueOf(lives.getMaxLives()))
                .replace("<amount>", String.valueOf(amount))
                .replace("<time>", String.valueOf(interval)));
        return 1;
    }

    private static int executeGiveLifeItem(SinceDungeon plugin, CommandContext<CommandSourceStack> ctx) {
        Player target = Bukkit.getPlayerExact(StringArgumentType.getString(ctx, "target"));
        if (target == null) {
            send(ctx, plugin.getLanguageManager().getString("admin.invalid_player"));
            return 0;
        }
        int amount = IntegerArgumentType.getInteger(ctx, "amount");

        NamespacedKey lifeKey = new NamespacedKey(plugin, "life_amount");
        ConfigurationSection cfg = plugin.getConfigFile().getSection("items.life_crystal");

        ItemStack item = ItemBuilder.fromConfig(plugin, "items.life_crystal", "TOTEM_OF_UNDYING")
                .amount(amount)
                .applyConfig(cfg, "&a&lExtra Life (+<amount>)", "<amount>", String.valueOf(amount))
                .setTag(lifeKey, PersistentDataType.INTEGER, amount)
                .build();

        target.getInventory().addItem(item);

        if (!isSilent(ctx)) {
            target.sendMessage(ColorUtils.parseWithPrefix(plugin.getLanguageManager().getString("lives.item_received").replace("<amount>", String.valueOf(amount))));
        }
        send(ctx, plugin.getLanguageManager().getString("lives.admin_gave_item")
                .replace("<amount>", String.valueOf(amount))
                .replace("<player>", target.getName()));
        return 1;
    }
}
