package net.danh.sinceDungeon;

import com.mojang.brigadier.arguments.StringArgumentType;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.danh.sinceDungeon.api.SinceDungeonAPI;
import net.danh.sinceDungeon.database.DatabaseManager;
import net.danh.sinceDungeon.database.TopManager;
import net.danh.sinceDungeon.editor.EditorGUI;
import net.danh.sinceDungeon.editor.EditorListener;
import net.danh.sinceDungeon.editor.EditorManager;
import net.danh.sinceDungeon.manager.DungeonGame;
import net.danh.sinceDungeon.manager.DungeonListener;
import net.danh.sinceDungeon.manager.DungeonManager;
import net.danh.sinceDungeon.manager.MythicListener;
import net.danh.sinceDungeon.party.PartyCommand;
import net.danh.sinceDungeon.party.PartyManager;
import net.danh.sinceDungeon.reward.RewardGUI;
import net.danh.sinceDungeon.reward.RewardSession;
import net.danh.sinceDungeon.reward.RewardSessionManager;
import net.danh.sinceDungeon.system.RedisManager;
import net.danh.sinceDungeon.utils.ColorUtils;
import net.danh.sinceDungeon.utils.ConfigUtils;
import net.danh.sinceDungeon.utils.ServerVersion;
import net.danh.sinceDungeon.utils.WorldUtils;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.NonNull;

import java.io.File;
import java.util.*;

public final class SinceDungeon extends JavaPlugin {
    private static SinceDungeon plugin;
    private MiniMessage miniMessage;

    private ConfigUtils configFile;
    private ConfigUtils messagesFile;

    private DungeonManager dungeonManager;
    private PartyManager partyManager;
    private EditorManager editorManager;
    private EditorListener editorListener;
    private DatabaseManager databaseManager;
    private TopManager topManager;
    private RedisManager redisManager;

    public static SinceDungeon getPlugin() {
        return plugin;
    }

    @Override
    public void onLoad() {
        plugin = this;
        if (ServerVersion.isAtMost(1, 21, 11))
            getLogger().info("Running natively for Paper 1.21+ | NMS Version: " + ServerVersion.getNmsVersion());
        else {
            getLogger().info("Running natively for Paper 26.1+ | Version: v" + ServerVersion.getMajor() + "_" + ServerVersion.getMinor() + "_" + ServerVersion.getPatch());
            getLogger().info("Running natively for Paper 26.1+ | NMS Version: v" + ServerVersion.getMajor() + "_" + ServerVersion.getMinor() + "_R" + ServerVersion.getRevisionNumber());
        }
    }

    @Override
    public void onEnable() {
        miniMessage = MiniMessage.miniMessage();

        configFile = new ConfigUtils(this, "config.yml");

        extractDefaultLocales();

        setupLanguage();

        new ConfigUtils(this, "dungeons/example_dungeon.yml");

        dungeonManager = new DungeonManager(this);
        partyManager = new PartyManager(this);
        editorManager = new EditorManager(this);
        editorListener = new EditorListener(this);

        // Initialize database and top manager
        databaseManager = new DatabaseManager(this);
        databaseManager.connect();
        topManager = new TopManager(this, databaseManager);

        if (configFile.getBoolean("cross-server.enabled", false)) {
            getLogger().warning("======================================================");
            getLogger().warning("⚠️ EXPERIMENTAL FEATURE ENABLED: CROSS-SERVER (v1.5.5+)");
            getLogger().warning("This feature is completely untested in production.");
            getLogger().warning("The author has no experience setting up or using this");
            getLogger().warning("infrastructure. Expect bugs and use at your own risk!");
            getLogger().warning("Official support for cross-server issues is NOT provided.");
            getLogger().warning("======================================================");
            redisManager = new RedisManager(this);
            redisManager.connect();
            String bungeeChannel = configFile.getString("cross-server.bungee-channel", "BungeeCord");
            getServer().getMessenger().registerOutgoingPluginChannel(this, bungeeChannel);
        }

        SinceDungeonAPI.init(this);

        RewardSessionManager.startCleanupTask(this);

        List<Listener> listeners = new ArrayList<>();
        listeners.add(new DungeonListener(this));
        listeners.add(editorListener);
        listeners.add(new RewardGUI(this));
        listeners.add(new EditorGUI(this));

        if (Bukkit.getPluginManager().isPluginEnabled("MythicMobs")) {
            listeners.add(new MythicListener(this));
        }

        registerListeners(listeners.toArray(new Listener[0]));
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> PartyCommand.register(this, event));
        registerCommands();
        cleanUpStuckWorlds();
        if (ServerVersion.isOlderThan(1, 21, 11))
            getLogger().warning("Warning: Your server version is below 1.21.11! If it have any error, join discord and report to author: https://discord.gg/zbMPtcM3wq");
        else if (ServerVersion.isAtLeast(26, 1))
            getLogger().warning("Warning: Your server version is below 26.1+! If it have any error, join discord and report to author: https://discord.gg/zbMPtcM3wq");
    }

    private void extractDefaultLocales() {
        String[] defaultLocales = {"messages_vi.yml", "messages_en.yml"};
        for (String loc : defaultLocales) {
            File file = new File(getDataFolder(), loc);
            if (!file.exists() && getResource(loc) != null) {
                saveResource(loc, false);
            }
        }
    }

    private void setupLanguage() {
        String lang = configFile.getString("settings.locale", "en");
        messagesFile = new ConfigUtils(this, "messages_" + lang + ".yml");
        getLogger().info("Loaded language file: messages_" + lang + ".yml");
    }

    @Override
    public void onDisable() {
        if (dungeonManager != null) {
            dungeonManager.stopAllGames();
        }

        RewardGUI rewardHelper = new RewardGUI(this);

        for (Map.Entry<UUID, RewardSession> entry : new HashMap<>(RewardSessionManager.getSessions()).entrySet()) {
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p != null && p.isOnline()) {
                rewardHelper.forceClaimAll(p, entry.getValue());
                p.closeInventory();
            }
        }

        RewardSessionManager.clearAll();

        if (editorManager != null) editorManager.clearAll();
        if (editorListener != null) editorListener.clearAll();
        if (configFile != null) configFile.save();
        if (messagesFile != null) messagesFile.save();
        if (databaseManager != null) databaseManager.disconnect();
        if (redisManager != null) {
            redisManager.disconnect();
        }
    }

    public void reloadFiles(CommandSender sender) {
        if (configFile != null) configFile.reload();
        if (editorManager != null) editorManager.clearAll();
        if (editorListener != null) editorListener.clearAll();
        setupLanguage();

        if (dungeonManager != null) {
            dungeonManager.reload().thenRun(() -> {
                String msg = messagesFile.getString("admin.reload");
                if (sender != null && msg != null) {
                    sender.sendMessage(ColorUtils.parseWithPrefix(msg));
                }
                getLogger().info("Configuration, Language, and Dungeons reloaded successfully.");
            });
        } else {
            if (sender != null) {
                sender.sendMessage(ColorUtils.parseWithPrefix(messagesFile.getString("admin.reload")));
            }
            getLogger().info("Configuration and Language reloaded.");
        }
    }

    private void cleanUpStuckWorlds() {
        File container = Bukkit.getWorldContainer();
        File[] files = container.listFiles();

        if (files != null) {
            Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                String currentPrefix = getConfigFile().getString("dungeon.world-prefix", "SinceDungeon_");
                for (File file : files) {
                    if (file.isDirectory() && (file.getName().startsWith("SinceDungeon_") || file.getName().startsWith(currentPrefix))) {
                        Bukkit.getScheduler().runTask(this, () -> {
                            World w = Bukkit.getWorld(file.getName());
                            if (w != null) {
                                Bukkit.unloadWorld(w, false);
                            }
                            Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                                getLogger().info("[Cleanup] Deleted leftover generated dungeon world: " + file.getName());
                                WorldUtils.deleteWorld(file);
                            });
                        });
                    }
                }
            });
        }
    }

    private void registerListeners(Listener @NonNull ... listeners) {
        for (Listener l : listeners) getServer().getPluginManager().registerEvents(l, this);
    }

    private void registerCommands() {
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            event.registrar().register(Commands.literal("sincedungeon")
                    .requires(s -> s.getSender().hasPermission("SinceDungeon.admin"))
                    .then(Commands.literal("reload")
                            .executes(ctx -> {
                                reloadFiles(ctx.getSource().getSender());
                                return 1;
                            })
                    )
                    .build(), "SinceDungeon Admin"
            );

            event.registrar().register(Commands.literal("dungeon")
                    .then(Commands.literal("join")
                            .then(Commands.argument("name", StringArgumentType.word())
                                    .suggests((ctx, builder) -> {
                                        String remaining = builder.getRemainingLowerCase();
                                        for (String mapName : dungeonManager.getTemplates().keySet()) {
                                            if (dungeonManager.getTemplates().get(mapName).isPublic())
                                                if (mapName.toLowerCase().startsWith(remaining)) {
                                                    builder.suggest(mapName);
                                                }
                                        }
                                        return builder.buildFuture();
                                    })
                                    .executes(ctx -> {
                                        if (ctx.getSource().getExecutor() instanceof Player p) {
                                            dungeonManager.joinDungeon(p, StringArgumentType.getString(ctx, "name"));
                                        } else {
                                            ctx.getSource().getSender().sendMessage(ColorUtils.parseWithPrefix(getMessagesFile().getString("admin.join_dungeon.console_error")));
                                        }
                                        return 1;
                                    })
                                    .then(Commands.argument("target", StringArgumentType.word())
                                            .requires(s -> s.getSender().hasPermission("SinceDungeon.admin") || !(s.getSender() instanceof Player))
                                            .executes(ctx -> {
                                                String targetName = StringArgumentType.getString(ctx, "target");
                                                Player target = Bukkit.getPlayerExact(targetName);
                                                if (target != null) {
                                                    dungeonManager.joinDungeon(target, StringArgumentType.getString(ctx, "name"));
                                                } else {
                                                    ctx.getSource().getSender().sendMessage(ColorUtils.parseWithPrefix(getMessagesFile().getString("admin.join_dungeon.not_found_player")
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
                                    DungeonGame game = dungeonManager.getGame(p.getUniqueId());
                                    if (game != null) {
                                        game.handlePlayerDisconnect(p);
                                        p.sendMessage(ColorUtils.parseWithPrefix(getMessagesFile().getString("party.left_dungeon_due_to_party", "<yellow>Bạn đã thoát khỏi Dungeon.")));
                                    } else {
                                        p.sendMessage(ColorUtils.parseWithPrefix(getMessagesFile().getString("error.not_in_dungeon", "<red>Bạn hiện không ở trong Dungeon nào.")));
                                    }
                                } else {
                                    ctx.getSource().getSender().sendMessage(ColorUtils.parseWithPrefix(getMessagesFile().getString("admin.only_admin")));
                                }
                                return 1;
                            })
                    )
                    .then(Commands.literal("editor")
                            .requires(s -> s.getSender().hasPermission("SinceDungeon.admin"))
                            .executes(ctx -> {
                                if (ctx.getSource().getExecutor() instanceof Player p) {
                                    editorManager.openEditor(p);
                                } else {
                                    ctx.getSource().getSender().sendMessage(ColorUtils.parseWithPrefix(getMessagesFile().getString("admin.only_admin")));
                                }
                                return 1;
                            })
                    )
                    .build(), "SinceDungeon Player"
            );
        });
    }

    public MiniMessage getMiniMessage() {
        return miniMessage;
    }

    public ConfigUtils getMessagesFile() {
        return messagesFile;
    }

    public ConfigUtils getConfigFile() {
        return configFile;
    }

    public DungeonManager getDungeonManager() {
        return dungeonManager;
    }

    public EditorManager getEditorManager() {
        return editorManager;
    }

    public EditorListener getEditorListener() {
        return editorListener;
    }

    public PartyManager getPartyManager() {
        return partyManager;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public TopManager getTopManager() {
        return topManager;
    }

    public RedisManager getRedisManager() {
        return redisManager;
    }
}