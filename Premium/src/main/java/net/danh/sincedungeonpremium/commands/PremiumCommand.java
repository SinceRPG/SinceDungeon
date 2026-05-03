package net.danh.sincedungeonpremium.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.registrar.ReloadableRegistrarEvent;
import net.danh.sinceDungeon.api.SinceDungeonAPI;
import net.danh.sincedungeonpremium.SinceDungeonPremium;
import org.bukkit.entity.Player;

/**
 * Premium-Exclusive Command: /sdp
 * Responsibilities:
 * - Admin command for reloading Premium configs and Holograms.
 * - In-game utility to dynamically place Holographic Leaderboards without editing the config file.
 * - Provides full, dynamic tab-completion for all arguments, integrating with Core's API.
 */
public class PremiumCommand {

    public static void register(SinceDungeonPremium plugin, ReloadableRegistrarEvent<Commands> event) {
        LiteralCommandNode<CommandSourceStack> node = Commands.literal("sdp")
                .requires(s -> s.getSender().hasPermission("SinceDungeon.admin"))
                .then(Commands.literal("reload")
                        .executes(ctx -> {
                            plugin.getFileManager().setup();
                            if (plugin.getHologramManager() != null) {
                                plugin.getHologramManager().clearAllHolograms();
                                plugin.getHologramManager().startUpdater();
                            }
                            plugin.getFileManager().sendMessage((Player) ctx.getSource().getSender(), "admin.reload");
                            return 1;
                        })
                )
                .then(Commands.literal("hologram")
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
                                            if ("FASTEST_TIME".toLowerCase().startsWith(remaining)) builder.suggest("FASTEST_TIME");
                                            if ("PARTY_FASTEST_TIME".toLowerCase().startsWith(remaining)) builder.suggest("PARTY_FASTEST_TIME");
                                            if ("MOST_KILLS".toLowerCase().startsWith(remaining)) builder.suggest("MOST_KILLS");
                                            if ("MOST_CLEARS".toLowerCase().startsWith(remaining)) builder.suggest("MOST_CLEARS");
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
                .build();

        event.registrar().register(node, "SinceDungeon Premium Commands", java.util.List.of("sdpremium"));
    }
}