package net.danh.sinceDungeon.managers;

import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.utils.SchedulerCompat;
import net.danh.sinceDungeon.utils.WorldUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Native Bukkit world management utility for cloning and loading maps.
 * Now features a Recursive Retry Mechanism to prevent RAM-Leak Hostage States.
 */
public class WorldManager {

    private static final Map<String, Integer> templateUsageCount = new ConcurrentHashMap<>();

    public static CompletableFuture<World> createDungeonWorldAsync(SinceDungeon plugin, String templateName, String instanceId) {
        CompletableFuture<World> finalFuture = new CompletableFuture<>();

        if (templateName == null || templateName.contains("/") || templateName.contains("\\") || templateName.contains(".")) {
            String errorMsg = plugin.getLanguageManager().getString("error.path_traversal", "Path Traversal attack detected in World Name: <world>");
            finalFuture.completeExceptionally(new IllegalArgumentException(errorMsg.replace("<world>", templateName)));
            return finalFuture;
        }

        SchedulerCompat.runGlobal(plugin, () -> {
            World templateW = Bukkit.getWorld(templateName);

            if (templateW != null) {
                templateW.save();

                int count = templateUsageCount.merge(templateName, 1, Integer::sum);
                if (count == 1) {
                    templateW.setAutoSave(false);
                }

                executeAsyncCopyAndLoad(plugin, templateName, instanceId, finalFuture, templateW);
            } else {
                executeAsyncCopyAndLoad(plugin, templateName, instanceId, finalFuture, null);
            }
        });

        return finalFuture;
    }

    private static void executeAsyncCopyAndLoad(SinceDungeon plugin, String templateName, String instanceId, CompletableFuture<World> finalFuture, World templateW) {
        SchedulerCompat.runAsync(plugin, () -> {
            try {
                File source = new File(Bukkit.getWorldContainer(), templateName);
                File target = new File(Bukkit.getWorldContainer(), instanceId);

                if (!source.exists()) {
                    String errorMsg = plugin.getLanguageManager().getString("error.template_not_found", "Template world folder not found: <template>").replace("<template>", templateName);
                    throw new RuntimeException(errorMsg);
                }

                boolean copied = WorldUtils.copyWorld(source, target);
                if (!copied) {
                    String errorMsg = plugin.getLanguageManager().getString("error.world_copy_fail", "Failed to copy world files using WorldUtils.");
                    throw new RuntimeException(errorMsg);
                }

                new File(target, "uid.dat").delete();

                SchedulerCompat.createWorld(plugin, createWorldCreator(plugin, instanceId)).whenComplete((world, throwable) -> SchedulerCompat.runGlobal(plugin, () -> {
                    try {
                        if (throwable != null) {
                            finalFuture.completeExceptionally(throwable);
                            return;
                        }
                        if (world != null) {
                            configureWorld(plugin, world);
                            finalFuture.complete(world);
                        } else {
                            finalFuture.completeExceptionally(new RuntimeException("WorldCreator returned null for created world."));
                        }
                    } catch (Exception e) {
                        finalFuture.completeExceptionally(e);
                    } finally {
                        releaseTemplateLock(templateName, templateW);
                    }
                }));
            } catch (Exception ex) {
                String logErr = plugin.getLanguageManager().getString("admin.log.world_gen_error", "[WorldManager] Error generating dungeon world: <error>");
                plugin.getLogger().severe(logErr.replace("<error>", ex.getMessage() != null ? ex.getMessage() : "Unknown"));
                finalFuture.completeExceptionally(ex);
                SchedulerCompat.runGlobal(plugin, () -> releaseTemplateLock(templateName, templateW));
            }
        });
    }

    private static void releaseTemplateLock(String templateName, World templateW) {
        if (templateW == null) return;
        int newCount = templateUsageCount.merge(templateName, -1, Integer::sum);
        if (newCount <= 0) {
            templateW.setAutoSave(true);
            templateUsageCount.remove(templateName);
        }
    }

    public static void cleanupTemplateLocks() {
        for (String templateName : templateUsageCount.keySet()) {
            World world = Bukkit.getWorld(templateName);
            if (world != null) {
                world.setAutoSave(true);
            }
        }
        templateUsageCount.clear();
    }

    public static void unloadAndDeleteWorld(SinceDungeon plugin, World world) {
        if (world == null) return;
        File folder = world.getWorldFolder();

        List<Player> players = world.getPlayers();
        if (!players.isEmpty()) {
            Location safeLoc = Bukkit.getWorlds().get(0).getSpawnLocation();
            for (Player p : players) {
                p.teleportAsync(safeLoc);
            }
            // Add a slight delay before triggering the unload to allow the teleport to fully process
            SchedulerCompat.runGlobalLater(plugin, () -> performUnload(plugin, world, folder, unloadRetries(plugin)), unloadDelayTicks(plugin));
        } else {
            performUnload(plugin, world, folder, unloadRetries(plugin));
        }
    }

    /**
     * Recursive Retry Unloader - Resolves the Orphaned World Memory Leak.
     * Safely bypasses transient chunk loading/saving states holding the world in RAM.
     */
    private static void performUnload(SinceDungeon plugin, World world, File folder, int retries) {
        // Note: Clear all entities forcefully before unloading to remove dangling pointers that block chunk unloading.
        if (!SchedulerCompat.isFolia()) {
            for (Entity e : world.getEntities()) {
                if (!(e instanceof Player)) {
                    e.remove();
                }
            }
        }

        SchedulerCompat.unloadWorld(plugin, world, false).whenComplete((unloaded, throwable) -> {
            if (throwable != null) {
                plugin.getLogger().warning(throwable.getMessage());
                scheduleUnloadRetry(plugin, world, folder, retries);
                return;
            }
            if (Boolean.TRUE.equals(unloaded)) {
            String logSuccess = plugin.getLanguageManager().getString("admin.log.world_unloaded", "Unloaded dungeon world: <world>");
            plugin.getLogger().info(logSuccess.replace("<world>", world.getName()));

            SchedulerCompat.runAsyncLater(plugin, () -> {
                if (!WorldUtils.deleteWorld(folder)) {
                    String logWarn = plugin.getLanguageManager().getString("admin.log.world_delete_fail", "Failed to fully delete world folder: <world>. It may be locked by another process.");
                    plugin.getLogger().warning(logWarn.replace("<world>", folder.getName()));
                }
            }, deleteDelayTicks(plugin));
                return;
            }
            scheduleUnloadRetry(plugin, world, folder, retries);
        });
    }

    public static void forceUnloadAndDelete(SinceDungeon plugin, World world) {
        if (world == null) return;
        File folder = world.getWorldFolder();

        for (Player p : world.getPlayers()) {
            p.teleportAsync(Bukkit.getWorlds().get(0).getSpawnLocation());
        }

        SchedulerCompat.unloadWorld(plugin, world, false).whenComplete((unloaded, throwable) -> {
            if (throwable == null && Boolean.TRUE.equals(unloaded)) {
                String logSuccess = plugin.getLanguageManager().getString("admin.log.world_force_unloaded", "Force unloaded dungeon world: <world>");
                plugin.getLogger().info(logSuccess.replace("<world>", world.getName()));
                SchedulerCompat.runAsyncLater(plugin, () -> WorldUtils.deleteWorld(folder), deleteDelayTicks(plugin));
            } else {
                String logCritical = plugin.getLanguageManager().getString("admin.log.world_force_unload_fail", "CRITICAL: Failed to force-unload world <world> during shutdown!");
                plugin.getLogger().severe(logCritical.replace("<world>", world.getName()));
            }
        });
    }

    private static WorldCreator createWorldCreator(SinceDungeon plugin, String instanceId) {
        WorldCreator creator = new WorldCreator(instanceId);
        creator.generatorSettings(plugin.getConfigFile().getString("dungeon.world-generator-settings", ""));
        creator.generateStructures(plugin.getConfigFile().getBoolean("dungeon.generate-structures", false));
        return creator;
    }

    private static void configureWorld(SinceDungeon plugin, World world) {
        world.setAutoSave(plugin.getConfigFile().getBoolean("dungeon.instance-autosave", false));
        world.setTime(plugin.getConfigFile().getInt("dungeon.default-time", 6000));
        world.setStorm(plugin.getConfigFile().getBoolean("dungeon.default-storm", false));
        world.setThundering(plugin.getConfigFile().getBoolean("dungeon.default-thundering", false));
    }

    private static void scheduleUnloadRetry(SinceDungeon plugin, World world, File folder, int retries) {
        if (retries > 0) {
            // Note: Re-queue the unload task safely preventing eternal memory leakage.
            String logRetry = plugin.getLanguageManager().getString("admin.log.world_unload_retry", "Retrying unload for world: <world> in 5 seconds...");
            if (logRetry != null) {
                plugin.getLogger().warning(logRetry.replace("<world>", world.getName()));
            }
            SchedulerCompat.runGlobalLater(plugin, () -> performUnload(plugin, world, folder, retries - 1), retryDelayTicks(plugin));
            return;
        }
        String logWarn = plugin.getLanguageManager().getString("admin.log.world_unload_fail", "Could not unload world: <world>");
        plugin.getLogger().severe(logWarn.replace("<world>", world.getName()));
    }

    private static int unloadRetries(SinceDungeon plugin) {
        return Math.max(0, plugin.getConfigFile().getInt("dungeon.unload-retries", 5));
    }

    private static long unloadDelayTicks(SinceDungeon plugin) {
        return Math.max(1L, plugin.getConfigFile().getInt("dungeon.unload-delay-ticks", 10));
    }

    private static long retryDelayTicks(SinceDungeon plugin) {
        return Math.max(1L, plugin.getConfigFile().getInt("dungeon.unload-retry-delay-ticks", 100));
    }

    private static long deleteDelayTicks(SinceDungeon plugin) {
        return Math.max(1L, plugin.getConfigFile().getInt("dungeon.delete-delay-ticks", 40));
    }
}
