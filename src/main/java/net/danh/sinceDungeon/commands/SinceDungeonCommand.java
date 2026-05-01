package net.danh.sinceDungeon.commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.registrar.ReloadableRegistrarEvent;
import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.managers.LivesManager;
import net.danh.sinceDungeon.utils.ColorUtils;
import net.danh.sinceDungeon.utils.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public class SinceDungeonCommand {

    /**
     * Registers the Admin command.
     * The root command literal and aliases are dynamically loaded from the configuration.
     * Includes Cooldown Reset logic.
     */
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
                .then(Commands.literal("cooldown")
                        .then(Commands.literal("reset")
                                .then(Commands.argument("target", StringArgumentType.word())
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
                                                        ctx.getSource().getSender().sendMessage(ColorUtils.parseWithPrefix(plugin.getMessagesFile().getString("admin.invalid_player", "&cPlayer not found.")));
                                                        return 0;
                                                    }

                                                    String map = StringArgumentType.getString(ctx, "map");
                                                    plugin.getCooldownManager().resetCooldown(target.getUniqueId(), map);

                                                    String successMsg = plugin.getMessagesFile().getString("admin.cooldown_reset_success", "&aSuccessfully reset dungeon cooldown for <player> in map <map>!");
                                                    ctx.getSource().getSender().sendMessage(ColorUtils.parseWithPrefix(successMsg.replace("<player>", target.getName()).replace("<map>", map)));
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
                                            org.bukkit.command.CommandSender sender = ctx.getSource().getSender();
                                            String map = StringArgumentType.getString(ctx, "map");

                                            if (!plugin.getDungeonManager().getTemplates().containsKey(map)) {
                                                sender.sendMessage(ColorUtils.parseWithPrefix(plugin.getMessagesFile().getString("error.file_not_found").replace("<file>", map)));
                                                return 0;
                                            }

                                            plugin.getTopManager().resetLeaderboard(map);

                                            String msg = plugin.getMessagesFile().getString("admin.top_reset_success", "&aSuccessfully reset the leaderboard for map: &e<map>");
                                            sender.sendMessage(ColorUtils.parseWithPrefix(msg.replace("<map>", map)));
                                            return 1;
                                        })
                                )
                        )
                )
                .then(Commands.literal("lives")
                        .then(Commands.argument("target", StringArgumentType.word())
                                .then(Commands.literal("add").then(Commands.argument("amount", IntegerArgumentType.integer())
                                        .executes(ctx -> {
                                            Player target = Bukkit.getPlayerExact(StringArgumentType.getString(ctx, "target"));
                                            if (target != null) {
                                                int amount = IntegerArgumentType.getInteger(ctx, "amount");
                                                plugin.getLivesManager().addLives(target.getUniqueId(), amount);
                                                ctx.getSource().getSender().sendMessage(ColorUtils.parseWithPrefix(plugin.getMessagesFile().getString("lives.admin_add").replace("<amount>", String.valueOf(amount)).replace("<player>", target.getName())));
                                            } else
                                                ctx.getSource().getSender().sendMessage(ColorUtils.parseWithPrefix(plugin.getMessagesFile().getString("lives.player_not_found")));
                                            return 1;
                                        })
                                ))
                                .then(Commands.literal("set").then(Commands.argument("amount", IntegerArgumentType.integer())
                                        .executes(ctx -> {
                                            Player target = Bukkit.getPlayerExact(StringArgumentType.getString(ctx, "target"));
                                            if (target != null) {
                                                int amount = IntegerArgumentType.getInteger(ctx, "amount");
                                                plugin.getLivesManager().setLives(target.getUniqueId(), amount);
                                                ctx.getSource().getSender().sendMessage(ColorUtils.parseWithPrefix(plugin.getMessagesFile().getString("lives.admin_set").replace("<amount>", String.valueOf(amount)).replace("<player>", target.getName())));
                                            } else
                                                ctx.getSource().getSender().sendMessage(ColorUtils.parseWithPrefix(plugin.getMessagesFile().getString("lives.player_not_found")));
                                            return 1;
                                        })
                                ))
                                .then(Commands.literal("addmax").then(Commands.argument("amount", IntegerArgumentType.integer())
                                        .executes(ctx -> {
                                            Player target = Bukkit.getPlayerExact(StringArgumentType.getString(ctx, "target"));
                                            if (target != null) {
                                                int amount = IntegerArgumentType.getInteger(ctx, "amount");
                                                plugin.getLivesManager().addMaxLives(target.getUniqueId(), amount);
                                                ctx.getSource().getSender().sendMessage(ColorUtils.parseWithPrefix(plugin.getMessagesFile().getString("lives.admin_addmax").replace("<amount>", String.valueOf(amount)).replace("<player>", target.getName())));
                                            } else
                                                ctx.getSource().getSender().sendMessage(ColorUtils.parseWithPrefix(plugin.getMessagesFile().getString("lives.player_not_found")));
                                            return 1;
                                        })
                                ))
                                .then(Commands.literal("setregenamount").then(Commands.argument("amount", IntegerArgumentType.integer())
                                        .executes(ctx -> {
                                            Player target = Bukkit.getPlayerExact(StringArgumentType.getString(ctx, "target"));
                                            if (target != null) {
                                                int amount = IntegerArgumentType.getInteger(ctx, "amount");
                                                plugin.getLivesManager().setCustomRegenAmount(target.getUniqueId(), amount);
                                                ctx.getSource().getSender().sendMessage(ColorUtils.parseWithPrefix(plugin.getMessagesFile().getString("lives.admin_set_regen_amount").replace("<amount>", String.valueOf(amount)).replace("<player>", target.getName())));
                                            } else
                                                ctx.getSource().getSender().sendMessage(ColorUtils.parseWithPrefix(plugin.getMessagesFile().getString("lives.player_not_found")));
                                            return 1;
                                        })
                                ))
                                .then(Commands.literal("setregeninterval").then(Commands.argument("seconds", IntegerArgumentType.integer())
                                        .executes(ctx -> {
                                            Player target = Bukkit.getPlayerExact(StringArgumentType.getString(ctx, "target"));
                                            if (target != null) {
                                                int seconds = IntegerArgumentType.getInteger(ctx, "seconds");
                                                plugin.getLivesManager().setCustomRegenInterval(target.getUniqueId(), seconds);
                                                ctx.getSource().getSender().sendMessage(ColorUtils.parseWithPrefix(plugin.getMessagesFile().getString("lives.admin_set_regen_interval").replace("<amount>", String.valueOf(seconds)).replace("<player>", target.getName())));
                                            } else
                                                ctx.getSource().getSender().sendMessage(ColorUtils.parseWithPrefix(plugin.getMessagesFile().getString("lives.player_not_found")));
                                            return 1;
                                        })
                                ))
                                .then(Commands.literal("resetregen")
                                        .executes(ctx -> {
                                            Player target = Bukkit.getPlayerExact(StringArgumentType.getString(ctx, "target"));
                                            if (target != null) {
                                                plugin.getLivesManager().setCustomRegenAmount(target.getUniqueId(), -1);
                                                plugin.getLivesManager().setCustomRegenInterval(target.getUniqueId(), -1);
                                                ctx.getSource().getSender().sendMessage(ColorUtils.parseWithPrefix(plugin.getMessagesFile().getString("lives.admin_reset_regen").replace("<player>", target.getName())));
                                            } else
                                                ctx.getSource().getSender().sendMessage(ColorUtils.parseWithPrefix(plugin.getMessagesFile().getString("lives.player_not_found")));
                                            return 1;
                                        })
                                )
                                .then(Commands.literal("check")
                                        .executes(ctx -> {
                                            Player target = Bukkit.getPlayerExact(StringArgumentType.getString(ctx, "target"));
                                            if (target != null) {
                                                LivesManager.PlayerLives l = plugin.getLivesManager().getLives(target.getUniqueId());
                                                int interval = l.getCustomRegenInterval() != -1 ? l.getCustomRegenInterval() : plugin.getConfigFile().getInt("lives.regen-interval-seconds", 3600);
                                                int amt = l.getCustomRegenAmount() != -1 ? l.getCustomRegenAmount() : plugin.getConfigFile().getInt("lives.regen-amount", 1);

                                                String msg = plugin.getMessagesFile().getString("lives.check_other")
                                                        .replace("<player>", target.getName())
                                                        .replace("<current>", String.valueOf(l.getCurrentLives()))
                                                        .replace("<max>", String.valueOf(l.getMaxLives()))
                                                        .replace("<amount>", String.valueOf(amt))
                                                        .replace("<time>", String.valueOf(interval));
                                                ctx.getSource().getSender().sendMessage(ColorUtils.parseWithPrefix(msg));
                                            } else
                                                ctx.getSource().getSender().sendMessage(ColorUtils.parseWithPrefix(plugin.getMessagesFile().getString("lives.player_not_found")));
                                            return 1;
                                        })
                                )
                        )
                )
                .then(Commands.literal("givelifeitem")
                        .then(Commands.argument("target", StringArgumentType.word())
                                .then(Commands.argument("amount", IntegerArgumentType.integer())
                                        .executes(ctx -> {
                                            Player target = Bukkit.getPlayerExact(StringArgumentType.getString(ctx, "target"));
                                            if (target != null) {
                                                int amount = IntegerArgumentType.getInteger(ctx, "amount");

                                                NamespacedKey lifeKey = new NamespacedKey(plugin, "life_amount");
                                                ConfigurationSection cfg = plugin.getConfigFile().getConfig().getConfigurationSection("lives.life-item");

                                                ItemStack item = ItemBuilder.fromConfig(plugin, "lives.life-item", "TOTEM_OF_UNDYING")
                                                        .amount(amount)
                                                        .applyConfig(cfg, "&a&lExtra Life (+<amount>)", "<amount>", String.valueOf(amount))
                                                        .setTag(lifeKey, PersistentDataType.INTEGER, amount)
                                                        .build();

                                                target.getInventory().addItem(item);

                                                target.sendMessage(ColorUtils.parseWithPrefix(plugin.getMessagesFile().getString("lives.item_received").replace("<amount>", String.valueOf(amount))));
                                                ctx.getSource().getSender().sendMessage(ColorUtils.parseWithPrefix(plugin.getMessagesFile().getString("lives.admin_gave_item")
                                                        .replace("<amount>", String.valueOf(amount))
                                                        .replace("<player>", target.getName())));
                                            } else {
                                                ctx.getSource().getSender().sendMessage(ColorUtils.parseWithPrefix(plugin.getMessagesFile().getString("admin.invalid_player")));
                                            }
                                            return 1;
                                        })
                                )
                        )
                )
                .build();

        event.registrar().register(adminNode, "SinceDungeon Admin", aliases);
    }
}