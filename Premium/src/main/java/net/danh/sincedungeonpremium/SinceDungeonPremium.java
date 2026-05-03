package net.danh.sincedungeonpremium;

import net.danh.sinceDungeon.api.SinceDungeonAPI;
import net.danh.sincedungeonpremium.actions.BuffAction;
import net.danh.sincedungeonpremium.managers.FileManager;
import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;

/**
 * Core Entry Point for SinceDungeon Premium Addon.
 * Responsibilities:
 * - Validates dependency presence (SinceDungeon Core).
 * - Initializes the File Manager to load configs and messages.
 * - Hooks into the core API to inject Premium actions, rewards, and conditions.
 * - Contains safe-parsing utilities to prevent configuration-related crashes.
 */
public final class SinceDungeonPremium extends JavaPlugin {

    private static SinceDungeonPremium instance;
    private FileManager fileManager;

    public static SinceDungeonPremium getInstance() {
        return instance;
    }

    public FileManager getFileManager() {
        return fileManager;
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
            getLogger().severe("SinceDungeon Core is not installed! Disabling Premium Addon.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        registerPremiumFeatures();

        getLogger().info(fileManager.getMessageRaw("log.plugin_enabled"));
    }

    @Override
    public void onDisable() {
        if (fileManager != null) {
            getLogger().info(fileManager.getMessageRaw("log.plugin_disabled"));
        }
    }

    private void registerPremiumFeatures() {
        SinceDungeonAPI api = SinceDungeonAPI.get();

        Map<String, Object> buffDefaults = new HashMap<>();
        buffDefaults.put("effect", fileManager.getConfig().getString("action-defaults.apply_buff.default-effect"));
        buffDefaults.put("duration", fileManager.getConfig().getInt("action-defaults.apply_buff.default-duration"));
        buffDefaults.put("amplifier", fileManager.getConfig().getInt("action-defaults.apply_buff.default-amplifier"));
        buffDefaults.put("objective_text", fileManager.getConfig().getString("action-defaults.apply_buff.objective_text"));

        api.registerCustomAction(
                "APPLY_BUFF",
                map -> {
                    String effect = String.valueOf(map.getOrDefault("effect", buffDefaults.get("effect")));
                    int duration = parseSafeInt(map.get("duration"), (int) buffDefaults.get("duration"));
                    int amplifier = parseSafeInt(map.get("amplifier"), (int) buffDefaults.get("amplifier"));
                    String objText = String.valueOf(map.getOrDefault("objective_text", buffDefaults.get("objective_text")));

                    return new BuffAction(effect, duration, amplifier, objText);
                },
                fileManager.getConfig().getString("gui.actions.apply_buff.name"),
                Material.POTION,
                fileManager.getConfig().getString("gui.actions.apply_buff.desc"),
                buffDefaults,
                null
        );

        api.registerRewardProcessor("EXP_LEVELS", (player, value, displayName) -> {
            try {
                int levels = Integer.parseInt(value.trim());
                player.giveExpLevels(levels);
                fileManager.sendMessage(player, "rewards.exp_levels", "<levels>", String.valueOf(levels));
            } catch (NumberFormatException e) {
                getLogger().warning("Invalid EXP level value provided in reward pool: " + value);
            }
        });

        api.registerConditionProcessor("HAS_PERMISSION", (player, value) -> player.hasPermission(value.trim()));
    }

    private int parseSafeInt(Object obj, int fallback) {
        if (obj == null) return fallback;
        if (obj instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(obj.toString().trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}