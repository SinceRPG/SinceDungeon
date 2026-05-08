package net.danh.sincedungeonpremium.commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.registrar.ReloadableRegistrarEvent;
import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.api.SinceDungeonAPI;
import net.danh.sincedungeonpremium.SinceDungeonPremium;
import net.danh.sincedungeonpremium.utils.PremiumLanguageInjector;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Premium-Exclusive Command: /sdp
 * Responsibilities:
 * - Admin command for reloading Premium configs and Holograms.
 * - In-game utility to dynamically place, move, and remove Holographic Leaderboards.
 * - Insert new stages dynamically between existing stages.
 * - Provides full, dynamic tab-completion for all arguments.
 */
public class PremiumCommand {

    public static void register(SinceDungeonPremium plugin, ReloadableRegistrarEvent<Commands> event) {
        LiteralCommandNode<CommandSourceStack> node = Commands.literal("sdp")
                .requires(s -> s.getSender().hasPermission("SinceDungeon.admin"))
                .then(Commands.literal("reload")
                        .executes(ctx -> {
                            plugin.getFileManager().setup();
                            // Re-inject on reload to guarantee safety if admin wiped the core files
                            PremiumLanguageInjector.inject(SinceDungeon.getPlugin());

                            if (plugin.getHologramManager() != null) {
                                plugin.getHologramManager().clearAllHolograms();
                                plugin.getHologramManager().startUpdater();
                            }
                            if (ctx.getSource().getExecutor() instanceof Player p) {
                                plugin.getFileManager().sendMessage(p, "admin.reload");
                            }
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

                                                    File file = new File(SinceDungeon.getPlugin().getDataFolder(), "dungeons/" + mapId + ".yml");
                                                    if (!file.exists()) {
                                                        if (ctx.getSource().getExecutor() instanceof Player p) {
                                                            plugin.getFileManager().sendMessage(p, "admin.invalid_map");
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
                                                            plugin.getFileManager().sendMessage(p, "admin.stage_inserted", "<pos>", String.valueOf(pos), "<map>", mapId);
                                                        }
                                                    } catch (IOException e) {
                                                        plugin.getLogger().warning(plugin.getFileManager().getMessageRaw("admin.stage_save_fail"));
                                                    }

                                                    return 1;
                                                })
                                        )
                                )
                        )
                )
                .then(Commands.literal("hologram")
                        .then(Commands.literal("create")
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
                                        .then(Commands.argument("category", StringArgumentType.word())
                                                .suggests((ctx, builder) -> {
                                                    String remaining = builder.getRemainingLowerCase();
                                                    if ("FASTEST_TIME".toLowerCase().startsWith(remaining))
                                                        builder.suggest("FASTEST_TIME");
                                                    if ("PARTY_FASTEST_TIME".toLowerCase().startsWith(remaining))
                                                        builder.suggest("PARTY_FASTEST_TIME");
                                                    if ("MOST_KILLS".toLowerCase().startsWith(remaining))
                                                        builder.suggest("MOST_KILLS");
                                                    if ("MOST_CLEARS".toLowerCase().startsWith(remaining))
                                                        builder.suggest("MOST_CLEARS");
                                                    return builder.buildFuture();
                                                })
                                                .executes(ctx -> {
                                                    if (ctx.getSource().getExecutor() instanceof Player player) {
                                                        String mapId = StringArgumentType.getString(ctx, "map_id");
                                                        String category = StringArgumentType.getString(ctx, "category");
                                                        plugin.getHologramManager().createHologramInGame(player, mapId, category);
                                                    } else {
                                                        plugin.getFileManager().sendMessage(null, "admin.invalid_player");
                                                    }
                                                    return 1;
                                                })
                                        )
                                )
                        )
                        .then(Commands.literal("move")
                                .then(Commands.argument("hologram_id", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            String remaining = builder.getRemainingLowerCase();
                                            ConfigurationSection sec = plugin.getFileManager().getConfig().getConfigurationSection("hologram-leaderboard.locations");
                                            if (sec != null) {
                                                for (String key : sec.getKeys(false)) {
                                                    if (key.toLowerCase().startsWith(remaining)) {
                                                        builder.suggest(key);
                                                    }
                                                }
                                            }
                                            return builder.buildFuture();
                                        })
                                        .executes(ctx -> {
                                            if (ctx.getSource().getExecutor() instanceof Player player) {
                                                String holoId = StringArgumentType.getString(ctx, "hologram_id");
                                                plugin.getHologramManager().moveHologramInGame(player, holoId);
                                            } else {
                                                plugin.getFileManager().sendMessage(null, "admin.invalid_player");
                                            }
                                            return 1;
                                        })
                                )
                        )
                        .then(Commands.literal("delete")
                                .then(Commands.argument("hologram_id", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            String remaining = builder.getRemainingLowerCase();
                                            ConfigurationSection sec = plugin.getFileManager().getConfig().getConfigurationSection("hologram-leaderboard.locations");
                                            if (sec != null) {
                                                for (String key : sec.getKeys(false)) {
                                                    if (key.toLowerCase().startsWith(remaining)) {
                                                        builder.suggest(key);
                                                    }
                                                }
                                            }
                                            return builder.buildFuture();
                                        })
                                        .executes(ctx -> {
                                            if (ctx.getSource().getExecutor() instanceof Player player) {
                                                String holoId = StringArgumentType.getString(ctx, "hologram_id");
                                                plugin.getHologramManager().deleteHologramInGame(player, holoId);
                                            } else {
                                                plugin.getFileManager().sendMessage(null, "admin.invalid_player");
                                            }
                                            return 1;
                                        })
                                )
                        )
                )
                .build();

        event.registrar().register(node, "SinceDungeon Premium Commands", List.of("sdpremium"));
    }
}