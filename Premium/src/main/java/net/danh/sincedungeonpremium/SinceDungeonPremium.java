package net.danh.sincedungeonpremium;

import io.lumine.mythic.api.MythicProvider;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.items.MythicItem;
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
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Optional;

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

        // 1. EXP LEVELS REWARD
        api.registerRewardProcessor("EXP_LEVELS", (player, value, displayName) -> {
            try {
                int levels = Integer.parseInt(value.trim());
                player.giveExpLevels(levels);
                fileManager.sendMessage(player, "rewards.exp_levels", "<levels>", String.valueOf(levels));
            } catch (NumberFormatException e) {
                getLogger().warning(fileManager.getMessageRaw("log.invalid_exp").replace("<value>", value));
            }
        });

        // 2. EXP POINTS REWARD
        api.registerRewardProcessor("EXP_POINTS", (player, value, displayName) -> {
            try {
                int points = Integer.parseInt(value.trim());
                player.giveExp(points);
                fileManager.sendMessage(player, "rewards.exp_points", "<points>", String.valueOf(points));
            } catch (NumberFormatException e) {
                getLogger().warning(fileManager.getMessageRaw("log.invalid_exp").replace("<value>", value));
            }
        });

        // 3. FULL HEAL REWARD
        api.registerRewardProcessor("FULL_HEAL", (player, value, displayName) -> {
            AttributeInstance attr = player.getAttribute(Attribute.MAX_HEALTH);
            double maxHealth = attr != null ? attr.getValue() : 20.0;
            player.setHealth(maxHealth);
            player.setFoodLevel(20);
            player.setFireTicks(0);
            fileManager.sendMessage(player, "rewards.full_heal");
        });

        // 4. MYTHIC ITEM REWARD
        api.registerRewardProcessor("MYTHIC_ITEM", (player, value, displayName) -> {
            if (getServer().getPluginManager().getPlugin("MythicMobs") == null) {
                getLogger().warning("Cannot give MythicItem. MythicMobs is not installed.");
                return;
            }

            try {
                String[] parts = value.split(":");
                String internalName = parts[0];
                int amount = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;

                Optional<MythicItem> optItem = MythicProvider.get().getItemManager().getItem(internalName);
                if (optItem.isPresent()) {
                    ItemStack itemStack = MythicBukkit.inst().getItemManager().getItemStack(internalName);
                    if (itemStack != null) {
                        itemStack.setAmount(amount);
                        HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(itemStack);
                        if (!leftover.isEmpty()) {
                            for (ItemStack drop : leftover.values()) {
                                player.getWorld().dropItem(player.getLocation(), drop);
                            }
                            player.sendMessage(fileManager.getMessageRaw("rewards.inventory_full"));
                        }
                        String name = displayName != null && !displayName.isEmpty() ? displayName : internalName;
                        fileManager.sendMessage(player, "rewards.mythic_item", "<item>", name);
                    }
                } else {
                    getLogger().warning("MythicItem not found: " + internalName);
                }
            } catch (Exception e) {
                getLogger().warning("Error processing MythicItem reward: " + value);
            }
        });

        // CONDITION: PERMISSION
        api.registerConditionProcessor("HAS_PERMISSION", (player, value) -> player.hasPermission(value.trim()));
    }

    private void registerPremiumListeners() {
        getServer().getPluginManager().registerEvents(new AffixListener(this), this);
        getServer().getPluginManager().registerEvents(new WebhookListener(this), this);
    }
}