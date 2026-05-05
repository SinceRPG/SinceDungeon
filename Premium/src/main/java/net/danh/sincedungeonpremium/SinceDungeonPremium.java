package net.danh.sincedungeonpremium;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.api.SinceDungeonAPI;
import net.danh.sincedungeonpremium.commands.PremiumCommand;
import net.danh.sincedungeonpremium.listeners.AffixListener;
import net.danh.sincedungeonpremium.listeners.WebhookListener;
import net.danh.sincedungeonpremium.managers.FileManager;
import net.danh.sincedungeonpremium.managers.HologramManager;
import net.danh.sincedungeonpremium.registry.PremiumActionRegistry;
import net.danh.sincedungeonpremium.systems.RouletteRewardSystem;
import net.danh.sincedungeonpremium.utils.PremiumLanguageInjector;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Core Entry Point for SinceDungeon Premium Addon.
 * Responsibilities:
 * - Validates dependency presence (SinceDungeon Core).
 * - Automatically injects GUI translations into Core.
 * - Initializes Managers for Files, Holograms, and Roulette GUIs.
 * - Hooks into the core API to inject Premium actions, rewards, and conditions.
 */
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

        registerPremiumProcessors();
        registerPremiumListeners();
        registerCommands();

        SinceDungeonAPI.get().getRewardManager().setRewardSystem(new RouletteRewardSystem(this));

        getLogger().info(fileManager.getMessageRaw("log.plugin_enabled"));
    }

    @Override
    public void onDisable() {
        if (hologramManager != null) {
            hologramManager.clearAllHolograms();
        }
        if (fileManager != null) {
            getLogger().info(fileManager.getMessageRaw("log.plugin_disabled"));
        }
    }

    private void registerCommands() {
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            PremiumCommand.register(this, event);
        });
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

        api.registerConditionProcessor("HAS_PERMISSION", (player, value) -> player.hasPermission(value.trim()));
    }

    private void registerPremiumListeners() {
        getServer().getPluginManager().registerEvents(new AffixListener(this), this);
        getServer().getPluginManager().registerEvents(new WebhookListener(this), this);
    }
}