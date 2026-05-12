package net.danh.sinceDungeon;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.danh.sinceDungeon.api.SinceDungeonAPI;
import net.danh.sinceDungeon.commands.DungeonCommand;
import net.danh.sinceDungeon.commands.PartyCommand;
import net.danh.sinceDungeon.commands.SinceDungeonCommand;
import net.danh.sinceDungeon.guis.editor.EditorListener;
import net.danh.sinceDungeon.guis.editor.EditorManager;
import net.danh.sinceDungeon.guis.editor.EditorMenuListener;
import net.danh.sinceDungeon.guis.top.TopMenuListener;
import net.danh.sinceDungeon.hooks.LivesExpansion;
import net.danh.sinceDungeon.hooks.PAPIHook;
import net.danh.sinceDungeon.listeners.CooldownItemListener;
import net.danh.sinceDungeon.listeners.DungeonListener;
import net.danh.sinceDungeon.listeners.LifeItemListener;
import net.danh.sinceDungeon.listeners.MythicListener;
import net.danh.sinceDungeon.managers.*;
import net.danh.sinceDungeon.systems.instancing.DefaultInstanceProvider;
import net.danh.sinceDungeon.systems.party.DefaultPartyProvider;
import net.danh.sinceDungeon.systems.reward.DefaultRewardSystem;
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
import java.util.ArrayList;
import java.util.List;

public final class SinceDungeon extends JavaPlugin {
    private static SinceDungeon plugin;
    private MiniMessage miniMessage;

    private ConfigManager configFile;
    private LanguageManager languageManager;

    private DungeonManager dungeonManager;
    private EditorManager editorManager;
    private EditorListener editorListener;
    private DatabaseManager databaseManager;
    private TopManager topManager;
    private RedisManager redisManager;
    private LivesManager livesManager;
    private CooldownManager cooldownManager;

    private RewardManager rewardManager;
    private PartySystemManager partySystemManager;
    private InstanceManager instanceManager;

    private DungeonListener dungeonListener;

    public static SinceDungeon getPlugin() {
        return plugin;
    }

    @Override
    public void onLoad() {
        plugin = this;
        configFile = new ConfigManager(this);
        String lang = configFile.getString("settings.locale", "en");
        languageManager = new LanguageManager(this, lang);

        if (ServerVersion.isAtMost(1, 21, 11)) {
            String msg = languageManager.getString("admin.log.startup_paper_modern", "Running natively for Paper 1.21+ | NMS Version: <nms>");
            getLogger().info(msg.replace("<nms>", ServerVersion.getNmsVersion()));
        } else {
            String msg = languageManager.getString("admin.log.startup_paper_legacy", "Running natively for Paper 26.1+ | Version: <version>");
            getLogger().info(msg.replace("<version>", "v" + ServerVersion.getMajor() + "_" + ServerVersion.getMinor() + "_" + ServerVersion.getPatch()));
        }
    }

    @Override
    public void onEnable() {
        miniMessage = MiniMessage.miniMessage();
        if (configFile == null) configFile = new ConfigManager(this);
        if (languageManager == null) setupLanguage();
        new ConfigUtils(this, "dungeons/example_dungeon.yml");

        rewardManager = new RewardManager(this);
        rewardManager.setRewardSystem(new DefaultRewardSystem(this));

        partySystemManager = new PartySystemManager(this);
        partySystemManager.setProvider(new DefaultPartyProvider(this));

        instanceManager = new InstanceManager(this);
        instanceManager.setProvider(new DefaultInstanceProvider(this));

        dungeonManager = new DungeonManager(this);
        editorManager = new EditorManager(this);
        editorListener = new EditorListener(this);

        databaseManager = new DatabaseManager(this);
        databaseManager.connect();
        topManager = new TopManager(this, databaseManager);
        livesManager = new LivesManager(this);

        cooldownManager = new CooldownManager(this);
        cooldownManager.loadCooldowns();

        if (configFile.getBoolean("cross-server.enabled", false)) {
            getLogger().warning(languageManager.getString("admin.log.experimental_cross_server", "⚠️ EXPERIMENTAL FEATURE ENABLED: CROSS-SERVER (v1.5.5+)"));
            redisManager = new RedisManager(this);
            redisManager.connect();
            String bungeeChannel = configFile.getString("cross-server.bungee-channel", "BungeeCord");
            getServer().getMessenger().registerOutgoingPluginChannel(this, bungeeChannel);
        }

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            PAPIHook.register(this);
            getLogger().info(languageManager.getString("admin.log.papi_registered"));
        } else {
            getLogger().warning(languageManager.getString("admin.log.papi_missing"));
        }

        SinceDungeonAPI.init(this);

        List<Listener> listeners = new ArrayList<>();
        dungeonListener = new DungeonListener(this);
        listeners.add(dungeonListener);
        listeners.add(editorListener);
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
            getLogger().warning(languageManager.getString("admin.log.version_warning"));
        }

        for (Player p : Bukkit.getOnlinePlayers()) {
            livesManager.loadPlayer(p.getUniqueId());
        }
    }

    private void setupLanguage() {
        String lang = configFile.getString("settings.locale", "en");
        languageManager = new LanguageManager(this, lang);
        getLogger().info(languageManager.getString("admin.log.lang_loaded", "Loaded modular language files for locale: <lang>").replace("<lang>", lang));
    }

    @Override
    public void onDisable() {
        if (dungeonManager != null) {
            dungeonManager.stopAllGames();
        }
        if (rewardManager != null && rewardManager.getRewardSystem() != null) {
            rewardManager.getRewardSystem().cleanup();
        }
        if (partySystemManager != null && partySystemManager.getProvider() != null) {
            partySystemManager.getProvider().cleanup();
        }
        if (instanceManager != null && instanceManager.getProvider() != null) {
            instanceManager.getProvider().cleanup();
        }
        if (livesManager != null) livesManager.forceSaveAll();
        if (editorManager != null) editorManager.clearAll();
        if (editorListener != null) editorListener.clearAll();
        if (configFile != null) configFile.save();
        if (databaseManager != null) databaseManager.disconnect();
        if (redisManager != null) {
            redisManager.disconnect();
        }
    }

    public void reloadFiles(CommandSender sender) {
        if (!new File(getDataFolder(), "config.yml").exists()) {
            saveResource("config.yml", false);
        }

        if (configFile != null) configFile.reload();
        if (editorManager != null) editorManager.clearAll();
        if (editorListener != null) editorListener.clearAll();

        setupLanguage();

        // Update active listeners holding config-cache objects
        if (dungeonListener != null) {
            dungeonListener.updateConfig();
        }

        if (dungeonManager != null) {
            dungeonManager.reload().thenRun(() -> {
                String msg = languageManager.getString("admin.reload");
                if (sender != null && msg != null) {
                    sender.sendMessage(ColorUtils.parseWithPrefix(msg));
                }
                getLogger().info(languageManager.getString("admin.log.full_reloaded", "Configuration, Language, and Dungeons reloaded successfully."));
            });
        } else {
            if (sender != null) {
                sender.sendMessage(ColorUtils.parseWithPrefix(languageManager.getString("admin.reload")));
            }
            getLogger().info(languageManager.getString("admin.log.config_reloaded", "Configuration and Language reloaded."));
        }
    }

    private void cleanUpStuckWorlds() {
        File container = Bukkit.getWorldContainer();
        File[] files = container.listFiles();

        if (files != null) {
            Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                String currentPrefix = getConfigFile().getString("dungeon.world-prefix", "SinceDungeon_");
                if (currentPrefix == null || currentPrefix.trim().isEmpty()) currentPrefix = "SinceDungeon_";
                for (File file : files) {
                    if (file.isDirectory() && (file.getName().startsWith("SinceDungeon_") || file.getName().startsWith(currentPrefix))) {
                        Bukkit.getScheduler().runTask(this, () -> {
                            World w = Bukkit.getWorld(file.getName());
                            if (w != null) {
                                Bukkit.unloadWorld(w, false);
                            }
                            Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                                String msg = languageManager.getString("admin.log.cleanup_deleted", "[Cleanup] Deleted leftover generated dungeon world: <world>");
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

    public LanguageManager getLanguageManager() {
        return languageManager;
    }

    public ConfigManager getConfigFile() {
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

    public PartySystemManager getPartyManager() {
        return partySystemManager;
    }

    public InstanceManager getInstanceManager() {
        return instanceManager;
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

    public RewardManager getRewardManager() {
        return rewardManager;
    }
}