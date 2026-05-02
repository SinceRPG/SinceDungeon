package net.danh.sinceDungeon;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.danh.sinceDungeon.api.SinceDungeonAPI;
import net.danh.sinceDungeon.commands.DungeonCommand;
import net.danh.sinceDungeon.commands.PartyCommand;
import net.danh.sinceDungeon.commands.SinceDungeonCommand;
import net.danh.sinceDungeon.guis.editor.EditorListener;
import net.danh.sinceDungeon.guis.editor.EditorManager;
import net.danh.sinceDungeon.guis.editor.EditorMenuListener;
import net.danh.sinceDungeon.guis.reward.RewardGUI;
import net.danh.sinceDungeon.guis.reward.RewardSession;
import net.danh.sinceDungeon.guis.reward.RewardSessionManager;
import net.danh.sinceDungeon.guis.top.TopMenuListener;
import net.danh.sinceDungeon.hooks.LivesExpansion;
import net.danh.sinceDungeon.listeners.CooldownItemListener;
import net.danh.sinceDungeon.listeners.DungeonListener;
import net.danh.sinceDungeon.listeners.LifeItemListener;
import net.danh.sinceDungeon.listeners.MythicListener;
import net.danh.sinceDungeon.managers.*;
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
    private LivesManager livesManager;
    private CooldownManager cooldownManager;

    public static SinceDungeon getPlugin() {
        return plugin;
    }

    @Override
    public void onLoad() {
        plugin = this;
        configFile = new ConfigUtils(this, "config.yml");
        String lang = configFile.getString("settings.locale", "en");
        messagesFile = new ConfigUtils(this, "messages_" + lang + ".yml");

        if (ServerVersion.isAtMost(1, 21, 11)) {
            String msg = messagesFile.getString("admin.log.startup_paper_modern", "Running natively for Paper 1.21+ | NMS Version: <nms>");
            getLogger().info(msg.replace("<nms>", ServerVersion.getNmsVersion()));
        } else {
            String msg = messagesFile.getString("admin.log.startup_paper_legacy", "Running natively for Paper 26.1+ | Version: <version>");
            getLogger().info(msg.replace("<version>", "v" + ServerVersion.getMajor() + "_" + ServerVersion.getMinor() + "_" + ServerVersion.getPatch()));
        }
    }

    @Override
    public void onEnable() {
        miniMessage = MiniMessage.miniMessage();
        if (configFile == null) configFile = new ConfigUtils(this, "config.yml");
        extractDefaultLocales();
        if (messagesFile == null) setupLanguage();
        new ConfigUtils(this, "dungeons/example_dungeon.yml");

        dungeonManager = new DungeonManager(this);
        partyManager = new PartyManager(this);
        editorManager = new EditorManager(this);
        editorListener = new EditorListener(this);

        databaseManager = new DatabaseManager(this);
        databaseManager.connect();
        topManager = new TopManager(this, databaseManager);
        livesManager = new LivesManager(this);

        cooldownManager = new CooldownManager(this);
        cooldownManager.loadCooldowns();

        if (configFile.getBoolean("cross-server.enabled", false)) {
            getLogger().warning("======================================================");
            getLogger().warning("⚠️ EXPERIMENTAL FEATURE ENABLED: CROSS-SERVER (v1.5.5+)");
            getLogger().warning("======================================================");
            redisManager = new RedisManager(this);
            redisManager.connect();
            String bungeeChannel = configFile.getString("cross-server.bungee-channel", "BungeeCord");
            getServer().getMessenger().registerOutgoingPluginChannel(this, bungeeChannel);
        }

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new LivesExpansion(this).register();
            getLogger().info(messagesFile.getString("admin.log.papi_registered"));
        } else {
            getLogger().warning(messagesFile.getString("admin.log.papi_missing"));
        }

        SinceDungeonAPI.init(this);
        RewardSessionManager.startCleanupTask(this);

        List<Listener> listeners = new ArrayList<>();
        listeners.add(new DungeonListener(this));
        listeners.add(editorListener);
        listeners.add(new RewardGUI(this));
        listeners.add(new EditorMenuListener(this));
        listeners.add(new LifeItemListener(this));
        listeners.add(new TopMenuListener(this));
        listeners.add(new CooldownItemListener(this));

        if (Bukkit.getPluginManager().isPluginEnabled("MythicMobs")) {
            listeners.add(new MythicListener(this));
        }

        registerListeners(listeners.toArray(new Listener[0]));
        registerCommands();
        cleanUpStuckWorlds();

        if (ServerVersion.isOlderThan(1, 21, 11) || ServerVersion.isAtLeast(26, 1)) {
            getLogger().warning(messagesFile.getString("admin.log.version_warning"));
        }

        for (Player p : Bukkit.getOnlinePlayers()) {
            livesManager.loadPlayer(p.getUniqueId());
        }
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

        if (livesManager != null) livesManager.forceSaveAll();
        if (editorManager != null) editorManager.clearAll();
        if (editorListener != null) editorListener.clearAll();
        if (configFile != null) configFile.save();
        if (messagesFile != null) messagesFile.save();
        if (databaseManager != null) databaseManager.disconnect();
        if (redisManager != null) {
            redisManager.disconnect();
        }
    }

    /**
     * Reloads configuration files, language files, and dungeon templates.
     * Automatically detects if core files were deleted by the admin and regenerates
     * them from the plugin jar before attempting to load them into memory.
     *
     * @param sender The entity (Player or Console) executing the reload command.
     */
    public void reloadFiles(CommandSender sender) {
        // 1. Force regenerate config.yml if it was deleted by the user
        if (!new File(getDataFolder(), "config.yml").exists()) {
            saveResource("config.yml", false);
        }

        // 2. Force regenerate default language files if they were deleted
        extractDefaultLocales();

        // 3. Reload ConfigUtils instances into memory
        if (configFile != null) configFile.reload();
        if (editorManager != null) editorManager.clearAll();
        if (editorListener != null) editorListener.clearAll();

        // 4. Setup language again to catch any locale changes in the fresh config
        setupLanguage();

        // 5. Asynchronously reload dungeon templates
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
                                String msg = messagesFile.getString("admin.log.cleanup_deleted", "[Cleanup] Deleted leftover generated dungeon world: <world>");
                                getLogger().info(msg.replace("<world>", file.getName()));
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
            PartyCommand.register(this, event);
            SinceDungeonCommand.register(this, event);
            DungeonCommand.register(this, event);
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

    public LivesManager getLivesManager() {
        return livesManager;
    }

    public CooldownManager getCooldownManager() {
        return cooldownManager;
    }
}