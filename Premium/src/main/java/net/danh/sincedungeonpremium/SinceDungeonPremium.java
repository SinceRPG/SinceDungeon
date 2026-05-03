package net.danh.sincedungeonpremium;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.danh.sinceDungeon.api.SinceDungeonAPI;
import net.danh.sinceDungeon.utils.ItemBuilder;
import net.danh.sincedungeonpremium.commands.PremiumCommand;
import net.danh.sincedungeonpremium.hooks.MMOCoreHook;
import net.danh.sincedungeonpremium.listeners.AffixListener;
import net.danh.sincedungeonpremium.listeners.PremiumRewardListener;
import net.danh.sincedungeonpremium.listeners.WebhookListener;
import net.danh.sincedungeonpremium.managers.FileManager;
import net.danh.sincedungeonpremium.managers.HologramManager;
import net.danh.sincedungeonpremium.managers.RouletteManager;
import net.danh.sincedungeonpremium.registry.PremiumActionRegistry;
import net.danh.sincedungeonpremium.utils.PremiumLanguageInjector;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
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
    private RouletteManager rouletteManager;

    public static SinceDungeonPremium getInstance() {
        return instance;
    }

    public FileManager getFileManager() {
        return fileManager;
    }

    public HologramManager getHologramManager() {
        return hologramManager;
    }

    public RouletteManager getRouletteManager() {
        return rouletteManager;
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

        // Auto-inject missing premium language keys directly into Core files
        PremiumLanguageInjector.inject(net.danh.sinceDungeon.SinceDungeon.getPlugin());

        hologramManager = new HologramManager(this);
        hologramManager.startUpdater();

        rouletteManager = new RouletteManager(this);

        PremiumActionRegistry.registerAll(this);

        registerPremiumProcessors();
        registerPremiumListeners();
        registerCommands();

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

        api.registerRewardProcessor("CLASS_LOOT", (player, value, displayName) -> {
            if (Bukkit.getPluginManager().isPluginEnabled("MMOCore")) {
                try {
                    String playerClass = MMOCoreHook.getPlayerClass(player);
                    if (playerClass == null) return;

                    String[] options = value.split(";");

                    for (String option : options) {
                        String[] split = option.split("->");
                        if (split.length == 2 && split[0].equalsIgnoreCase(playerClass)) {
                            ItemStack item = ItemBuilder.parseDynamicItem(split[1].trim());
                            if (item != null) {
                                player.getInventory().addItem(item);
                                fileManager.sendMessage(player, "rewards.class_loot", "<item>", item.getType().name());
                            }
                            return;
                        }
                    }
                } catch (Exception e) {
                    getLogger().warning(fileManager.getMessageRaw("log.mmocore_error").replace("<error>", e.getMessage()));
                }
            } else {
                getLogger().warning(fileManager.getMessageRaw("log.mmocore_missing"));
            }
        });

        api.registerConditionProcessor("HAS_PERMISSION", (player, value) -> player.hasPermission(value.trim()));
    }

    private void registerPremiumListeners() {
        getServer().getPluginManager().registerEvents(new AffixListener(this), this);
        getServer().getPluginManager().registerEvents(new WebhookListener(this), this);
        getServer().getPluginManager().registerEvents(new PremiumRewardListener(this), this);
    }
}