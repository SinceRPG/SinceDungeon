package net.danh.sincedungeonpremium;

import net.danh.sinceDungeon.api.SinceDungeonAPI;
import net.danh.sinceDungeon.utils.ItemBuilder;
import net.danh.sincedungeonpremium.actions.BranchingPathAction;
import net.danh.sincedungeonpremium.actions.BuffAction;
import net.danh.sincedungeonpremium.actions.EscortAction;
import net.danh.sincedungeonpremium.actions.LeverPuzzleAction;
import net.danh.sincedungeonpremium.hooks.MMOCoreHook;
import net.danh.sincedungeonpremium.listeners.AffixListener;
import net.danh.sincedungeonpremium.listeners.PremiumRewardListener;
import net.danh.sincedungeonpremium.listeners.WebhookListener;
import net.danh.sincedungeonpremium.managers.FileManager;
import net.danh.sincedungeonpremium.managers.HologramManager;
import net.danh.sincedungeonpremium.managers.RouletteManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

/**
 * Core Entry Point for SinceDungeon Premium Addon.
 * Responsibilities:
 * - Validates dependency presence (SinceDungeon Core).
 * - Initializes Managers for Files, Holograms, and Roulette GUIs.
 * - Hooks into the core API to inject Premium actions, rewards, and conditions.
 * - Registers premium event listeners (Affixes, Webhooks, Custom Drops).
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

        hologramManager = new HologramManager(this);
        hologramManager.startUpdater();

        rouletteManager = new RouletteManager(this);

        registerPremiumActions();
        registerPremiumProcessors();
        registerPremiumListeners();

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

    /**
     * Registers all premium-exclusive actions into the SinceDungeon Core via API.
     */
    private void registerPremiumActions() {
        SinceDungeonAPI api = SinceDungeonAPI.get();

        // 1. Buff Action
        Map<String, Object> buffDefaults = new HashMap<>();
        buffDefaults.put("effect", fileManager.getConfig().getString("action-defaults.apply_buff.default-effect"));
        buffDefaults.put("duration", fileManager.getConfig().getInt("action-defaults.apply_buff.default-duration"));
        buffDefaults.put("amplifier", fileManager.getConfig().getInt("action-defaults.apply_buff.default-amplifier"));
        buffDefaults.put("objective_text", fileManager.getConfig().getString("action-defaults.apply_buff.objective_text"));

        api.registerCustomAction(
                "APPLY_BUFF",
                map -> new BuffAction(
                        String.valueOf(map.getOrDefault("effect", buffDefaults.get("effect"))),
                        parseSafeInt(map.get("duration"), (int) buffDefaults.get("duration")),
                        parseSafeInt(map.get("amplifier"), (int) buffDefaults.get("amplifier")),
                        String.valueOf(map.getOrDefault("objective_text", buffDefaults.get("objective_text")))
                ),
                fileManager.getConfig().getString("gui.actions.apply_buff.name"),
                Material.POTION,
                fileManager.getConfig().getString("gui.actions.apply_buff.desc"),
                buffDefaults,
                null
        );

        // 2. Escort NPC Action
        Map<String, Object> escortDefaults = new HashMap<>();
        escortDefaults.put("mob", fileManager.getConfig().getString("action-defaults.escort.default-mob"));
        escortDefaults.put("name", fileManager.getConfig().getString("action-defaults.escort.default-name"));
        escortDefaults.put("health", fileManager.getConfig().getDouble("action-defaults.escort.default-health"));
        escortDefaults.put("start_location", "0,64,0");
        escortDefaults.put("target_location", "10,64,10");
        escortDefaults.put("speed", fileManager.getConfig().getDouble("action-defaults.escort.default-speed"));
        escortDefaults.put("radius", fileManager.getConfig().getDouble("action-defaults.escort.default-radius"));
        escortDefaults.put("objective_text", fileManager.getConfig().getString("action-defaults.escort.objective_text"));

        api.registerCustomAction(
                "ESCORT_NPC",
                map -> new EscortAction(
                        String.valueOf(map.getOrDefault("mob", escortDefaults.get("mob"))),
                        String.valueOf(map.getOrDefault("name", escortDefaults.get("name"))),
                        parseSafeDouble(map.get("health"), (double) escortDefaults.get("health")),
                        String.valueOf(map.getOrDefault("start_location", escortDefaults.get("start_location"))),
                        String.valueOf(map.getOrDefault("target_location", escortDefaults.get("target_location"))),
                        parseSafeDouble(map.get("speed"), (double) escortDefaults.get("speed")),
                        parseSafeDouble(map.get("radius"), (double) escortDefaults.get("radius")),
                        String.valueOf(map.getOrDefault("objective_text", escortDefaults.get("objective_text")))
                ),
                fileManager.getConfig().getString("gui.actions.escort.name"),
                Material.MINECART,
                fileManager.getConfig().getString("gui.actions.escort.desc"),
                escortDefaults,
                null
        );

        // 3. Branching Paths Action
        Map<String, Object> branchDefaults = new HashMap<>();
        branchDefaults.put("path_a_loc", "0,64,0");
        branchDefaults.put("path_b_loc", "10,64,10");
        branchDefaults.put("stage_a", 3);
        branchDefaults.put("stage_b", 4);
        branchDefaults.put("radius", 3.0);
        branchDefaults.put("objective_text", fileManager.getConfig().getString("action-defaults.branch.objective_text"));

        api.registerCustomAction(
                "BRANCHING_PATH",
                map -> new BranchingPathAction(
                        String.valueOf(map.getOrDefault("path_a_loc", branchDefaults.get("path_a_loc"))),
                        String.valueOf(map.getOrDefault("path_b_loc", branchDefaults.get("path_b_loc"))),
                        parseSafeInt(map.get("stage_a"), (int) branchDefaults.get("stage_a")),
                        parseSafeInt(map.get("stage_b"), (int) branchDefaults.get("stage_b")),
                        parseSafeDouble(map.get("radius"), (double) branchDefaults.get("radius")),
                        String.valueOf(map.getOrDefault("objective_text", branchDefaults.get("objective_text")))
                ),
                fileManager.getConfig().getString("gui.actions.branch.name"),
                Material.OAK_SIGN,
                fileManager.getConfig().getString("gui.actions.branch.desc"),
                branchDefaults,
                null
        );

        // 4. Lever Puzzle Action
        Map<String, Object> puzzleDefaults = new HashMap<>();
        puzzleDefaults.put("levers", new ArrayList<>(Arrays.asList("0,64,0", "2,64,0", "4,64,0")));
        puzzleDefaults.put("objective_text", fileManager.getConfig().getString("action-defaults.puzzle.objective_text"));

        api.registerCustomAction(
                "LEVER_PUZZLE",
                map -> {
                    List<String> levers = new ArrayList<>();
                    Object obj = map.get("levers");
                    if (obj instanceof List<?> l) {
                        for (Object o : l) levers.add(o.toString());
                    }
                    String objText = String.valueOf(map.getOrDefault("objective_text", puzzleDefaults.get("objective_text")));
                    return new LeverPuzzleAction(levers, objText);
                },
                fileManager.getConfig().getString("gui.actions.puzzle.name"),
                Material.LEVER,
                fileManager.getConfig().getString("gui.actions.puzzle.desc"),
                puzzleDefaults,
                null
        );
    }


    /**
     * Registers premium reward processors.
     * Includes isolated MMOCore integration for personalized class loot.
     * Uses the MMOCoreHook to prevent JVM crashes on servers without MMOCore.
     */
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

    public int parseSafeInt(Object obj, int fallback) {
        if (obj == null) return fallback;
        if (obj instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(obj.toString().trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public double parseSafeDouble(Object obj, double fallback) {
        if (obj == null) return fallback;
        if (obj instanceof Number number) return number.doubleValue();
        try {
            return Double.parseDouble(obj.toString().trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}