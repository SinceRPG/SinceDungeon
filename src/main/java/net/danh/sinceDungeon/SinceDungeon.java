package net.danh.sinceDungeon;

import com.mojang.brigadier.arguments.StringArgumentType;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.danh.sinceDungeon.api.SinceDungeonAPI;
import net.danh.sinceDungeon.editor.EditorGUI;
import net.danh.sinceDungeon.editor.EditorListener;
import net.danh.sinceDungeon.editor.EditorManager;
import net.danh.sinceDungeon.manager.DungeonListener;
import net.danh.sinceDungeon.manager.DungeonManager;
import net.danh.sinceDungeon.manager.MythicListener;
import net.danh.sinceDungeon.reward.RewardGUI;
import net.danh.sinceDungeon.reward.RewardSession;
import net.danh.sinceDungeon.reward.RewardSessionManager;
import net.danh.sinceDungeon.utils.ColorUtils;
import net.danh.sinceDungeon.utils.ConfigUtils;
import net.danh.sinceDungeon.utils.ServerVersion;
import net.danh.sinceDungeon.utils.WorldUtils;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.NonNull;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Main plugin class for SinceDungeon.
 */
public final class SinceDungeon extends JavaPlugin {
    private static SinceDungeon plugin;
    private MiniMessage miniMessage;

    private ConfigUtils configFile;
    private ConfigUtils messagesFile;

    private DungeonManager dungeonManager;
    private EditorManager editorManager;
    private EditorListener editorListener;

    /**
     * Returns the singleton plugin instance.
     *
     * @return The SinceDungeon JavaPlugin instance.
     */
    public static SinceDungeon getPlugin() {
        return plugin;
    }

    @Override
    public void onLoad() {
        plugin = this;
        getLogger().info("Running natively for Paper 1.21+ | Version: " + ServerVersion.getMajor() + "." + ServerVersion.getMinor() + "." + ServerVersion.getPatch());
    }

    @Override
    public void onEnable() {
        miniMessage = MiniMessage.miniMessage();

        configFile = new ConfigUtils(this, "config.yml");

        extractDefaultLocales();

        setupLanguage();

        new ConfigUtils(this, "dungeons/example_dungeon.yml");

        dungeonManager = new DungeonManager(this);
        editorManager = new EditorManager(this);
        editorListener = new EditorListener(this);

        SinceDungeonAPI.init(this);

        List<Listener> listeners = new ArrayList<>();
        listeners.add(new DungeonListener(this));
        listeners.add(editorListener);
        listeners.add(new RewardGUI(this));
        listeners.add(new EditorGUI(this));

        if (Bukkit.getPluginManager().isPluginEnabled("MythicMobs")) {
            listeners.add(new MythicListener(this));
        }

        registerListeners(listeners.toArray(new Listener[0]));

        registerCommands();
        cleanUpStuckWorlds();
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
        for (Map.Entry<UUID, RewardSession> entry : RewardSessionManager.getSessions().entrySet()) {
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p != null && p.isOnline()) {
                rewardHelper.forceClaimAll(p, entry.getValue());
                p.closeInventory();
            }
        }

        RewardSessionManager.clearAll();
        Bukkit.getScheduler().cancelTasks(this);

        if (configFile != null) configFile.save();
        if (messagesFile != null) messagesFile.save();
    }

    /**
     * Reloads configurations and dungeon templates dynamically.
     */
    public void reloadFiles() {
        if (configFile != null) configFile.reload();
        setupLanguage();
        if (dungeonManager != null) dungeonManager.reload();
        getLogger().info("Configuration, Language, and Dungeons reloaded.");
    }

    private void cleanUpStuckWorlds() {
        File container = Bukkit.getWorldContainer();
        File[] files = container.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory() && file.getName().startsWith("SinceDungeon_")) {
                    getLogger().info("[Cleanup] Detected leftover generated dungeon world: " + file.getName() + ". Processing execution routines...");
                    WorldUtils.deleteWorld(file);
                }
            }
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
                                reloadFiles();
                                ctx.getSource().getSender().sendMessage(ColorUtils.parseWithPrefix(messagesFile.getString("admin.reload")));
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
                                    dungeonManager.quitDungeon(p);
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
}