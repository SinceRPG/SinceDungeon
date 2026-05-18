package net.danh.sincedungeonpremium;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.api.SinceDungeonAPI;
import net.danh.sinceDungeon.systems.instancing.DefaultInstanceProvider;
import net.danh.sinceDungeon.systems.reward.DefaultRewardSystem;
import net.danh.sincedungeonpremium.commands.PremiumCommand;
import net.danh.sincedungeonpremium.hooks.PremiumMythicMobsHook;
import net.danh.sincedungeonpremium.listeners.AffixListener;
import net.danh.sincedungeonpremium.listeners.PremiumRewardListener;
import net.danh.sincedungeonpremium.listeners.WebhookListener;
import net.danh.sincedungeonpremium.managers.FileManager;
import net.danh.sincedungeonpremium.managers.HologramManager;
import net.danh.sincedungeonpremium.registry.PremiumActionRegistry;
import net.danh.sincedungeonpremium.systems.RouletteRewardSystem;
import net.danh.sincedungeonpremium.systems.instancing.SchematicInstanceProvider;
import net.danh.sincedungeonpremium.utils.PremiumLanguageInjector;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.plugin.java.JavaPlugin;

public final class SinceDungeonPremium extends JavaPlugin {

    private static SinceDungeonPremium instance;
    private FileManager fileManager;
    private HologramManager hologramManager;

    public static SinceDungeonPremium getInstance() {
        return instance;
    }

    public FileManager getFileManager() {
        return fileManager;
    }

    public HologramManager getHologramManager() {
        return hologramManager;
    }

    @Override
    public void onLoad() {
        instance = this;
    }

    @Override
    public void onEnable() {
        fileManager = new FileManager(this);
        fileManager.setup();

        if (getServer().getPluginManager().getPlugin("SinceDungeon") == null) {
            getLogger().severe(fileManager.getMessageRaw("log.core_missing"));
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        PremiumLanguageInjector.inject(SinceDungeon.getPlugin());

        hologramManager = new HologramManager(this);
        hologramManager.startUpdater();

        PremiumActionRegistry.registerAll(this);

        registerPremiumHooks();
        registerPremiumProcessors();
        registerPremiumListeners();
        registerCommands();

        // Check Instancing Mode
        String instancingMode = fileManager.getConfig().getString("instancing.mode", "WORLD");
        if (instancingMode.equalsIgnoreCase("SCHEMATIC")) {
            if (getServer().getPluginManager().getPlugin("WorldEdit") != null || getServer().getPluginManager().getPlugin("FastAsyncWorldEdit") != null) {
                SinceDungeonAPI.get().getInstanceManager().setProvider(new SchematicInstanceProvider(this));
                getLogger().info(fileManager.getMessageRaw("log.schematic_provider_enabled"));
            } else {
                getLogger().warning(fileManager.getMessageRaw("log.worldedit_missing_provider"));
            }
        }

        SinceDungeonAPI.get().getRewardManager().setRewardSystem(new RouletteRewardSystem(this));

        getLogger().info(fileManager.getMessageRaw("log.plugin_enabled"));
    }

    @Override
    public void onDisable() {
        SinceDungeon core = SinceDungeon.getPlugin();
        if (core != null && core.isEnabled() && core.getRewardManager() != null) {
            core.getRewardManager().setRewardSystem(new DefaultRewardSystem(core));
        }
        if (hologramManager != null) {
            hologramManager.cleanup();
        }

        restoreCoreSystems();
        unregisterPremiumExtensions();

        if (fileManager != null) {
            getLogger().info(fileManager.getMessageRaw("log.plugin_disabled"));
        }
        instance = null;
    }

    private void registerCommands() {
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            PremiumCommand.register(this, event);
        });
    }

    private void registerPremiumHooks() {
        if (getServer().getPluginManager().getPlugin("MythicMobs") != null) {
            PremiumMythicMobsHook.register();
            getLogger().info(fileManager.getMessageRaw("log.mythicmobs_hooked"));
        }
    }

    private void registerPremiumProcessors() {
        SinceDungeonAPI api = SinceDungeonAPI.get();

        api.registerRewardProcessor("EXP_LEVELS", (player, value, displayName) -> {
            try {
                int levels = Integer.parseInt(value.trim());
                player.giveExpLevels(levels);
                fileManager.sendMessage(player, "rewards.exp_levels", "<levels>", String.valueOf(levels));
            } catch (NumberFormatException e) {
                getLogger().warning(fileManager.getMessageRaw("log.invalid_exp").replace("<value>", value));
            }
        });

        api.registerRewardProcessor("EXP_POINTS", (player, value, displayName) -> {
            try {
                int points = Integer.parseInt(value.trim());
                player.giveExp(points);
                fileManager.sendMessage(player, "rewards.exp_points", "<points>", String.valueOf(points));
            } catch (NumberFormatException e) {
                getLogger().warning(fileManager.getMessageRaw("log.invalid_exp").replace("<value>", value));
            }
        });

        api.registerRewardProcessor("FULL_HEAL", (player, value, displayName) -> {
            AttributeInstance attr = player.getAttribute(Attribute.MAX_HEALTH);
            double maxHealth = attr != null ? attr.getValue() : 20.0;
            player.setHealth(maxHealth);
            player.setFoodLevel(20);
            player.setFireTicks(0);
            fileManager.sendMessage(player, "rewards.full_heal");
        });

        api.registerConditionProcessor("HAS_PERMISSION", (player, value) -> player.hasPermission(value.trim()));
    }

    private void registerPremiumListeners() {
        getServer().getPluginManager().registerEvents(new AffixListener(this), this);
        getServer().getPluginManager().registerEvents(new WebhookListener(this), this);
        getServer().getPluginManager().registerEvents(new PremiumRewardListener(this), this);
    }

    private void restoreCoreSystems() {
        if (getServer().getPluginManager().getPlugin("SinceDungeon") == null || SinceDungeon.getPlugin() == null)
            return;

        SinceDungeon core = SinceDungeon.getPlugin();
        if (!core.isEnabled()) return;

        if (core.getDungeonManager() != null) {
            core.getDungeonManager().stopAllGames();
        }

        if (core.getInstanceManager() != null && core.getInstanceManager().getProvider() instanceof SchematicInstanceProvider) {
            core.getInstanceManager().setProvider(new DefaultInstanceProvider(core));
        }

        if (core.getRewardManager() != null && core.getRewardManager().getRewardSystem() instanceof RouletteRewardSystem) {
            core.getRewardManager().setRewardSystem(new DefaultRewardSystem(core));
        }
    }

    private void unregisterPremiumExtensions() {
        if (getServer().getPluginManager().getPlugin("SinceDungeon") == null) return;
        if (SinceDungeon.getPlugin() == null || !SinceDungeon.getPlugin().isEnabled()) return;

        SinceDungeonAPI api;
        try {
            api = SinceDungeonAPI.get();
        } catch (IllegalStateException ignored) {
            return;
        }

        String[] actions = {
                "BUFF", "ESCORT_NPC", "BRANCHING_PATH", "LEVER_PUZZLE",
                "CHECKPOINT", "DAMAGE_ZONE", "JUMP_STAGE", "CINEMATIC_DIALOGUE",
                "PROJECTILE_TRAP", "DEFEND_CORE", "GIVE_ITEM", "PLAY_SOUND", "NPC_INTERACTION"
        };
        for (String action : actions) {
            api.unregisterCustomAction(action);
        }

        api.unregisterRewardProcessor("EXP_LEVELS");
        api.unregisterRewardProcessor("EXP_POINTS");
        api.unregisterRewardProcessor("FULL_HEAL");
        api.unregisterRewardProcessor("MYTHIC_ITEM");
        api.unregisterConditionProcessor("HAS_PERMISSION");
        api.unregisterItemProvider("MYTHIC_ITEM");
    }
}
