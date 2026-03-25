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

public final class SinceDungeon extends JavaPlugin {
    private static SinceDungeon plugin;
    private MiniMessage miniMessage;

    private ConfigUtils configFile;
    private ConfigUtils messagesFile;

    private DungeonManager dungeonManager;
    private EditorManager editorManager;
    private EditorListener editorListener;

    public static SinceDungeon getPlugin() {
        return plugin;
    }

    @Override
    public void onLoad() {
        plugin = this;
        getLogger().info("Server version: " + ServerVersion.getNmsVersion() + " | " + ServerVersion.getMajor() + "." + ServerVersion.getMinor() + "." + ServerVersion.getPatch());
    }

    @Override
    public void onEnable() {
        miniMessage = MiniMessage.miniMessage();

        configFile = new ConfigUtils(this, "config.yml");
        messagesFile = new ConfigUtils(this, "messages.yml");
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

        getLogger().info("SinceDungeon enabled successfully!");
    }

    @Override
    public void onDisable() {
        if (dungeonManager != null) {
            dungeonManager.stopAllGames();
        }

        Bukkit.getScheduler().cancelTasks(this);

        if (configFile != null) configFile.save();
        if (messagesFile != null) messagesFile.save();

        getLogger().info("SinceDungeon disabled!");
    }

    public void reloadFiles() {
        if (configFile != null) configFile.reload();
        if (messagesFile != null) messagesFile.reload();
        if (dungeonManager != null) dungeonManager.reload();
        getLogger().info("Configuration and Dungeons reloaded.");
    }

    private void cleanUpStuckWorlds() {
        File container = Bukkit.getWorldContainer();
        File[] files = container.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory() && file.getName().startsWith("SinceDungeon_")) {
                    getLogger().info("[Cleanup] Phát hiện world rác: " + file.getName() + ". Đang dọn dẹp...");
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
                                    .executes(ctx -> {
                                        if (ctx.getSource().getExecutor() instanceof Player p)
                                            dungeonManager.joinDungeon(p, StringArgumentType.getString(ctx, "name"));
                                        return 1;
                                    })
                            )
                    )
                    .then(Commands.literal("leave")
                            .executes(ctx -> {
                                if (ctx.getSource().getExecutor() instanceof Player p)
                                    dungeonManager.quitDungeon(p);
                                return 1;
                            })
                    )
                    .then(Commands.literal("editor")
                            .requires(s -> s.getSender().hasPermission("SinceDungeon.admin"))
                            .executes(ctx -> {
                                if (ctx.getSource().getExecutor() instanceof Player p)
                                    editorManager.openEditor(p);
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