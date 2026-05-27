package net.danh.sinceDungeon.systems.instancing;

import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.api.interfaces.InstanceProvider;
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

public class DefaultInstanceProvider implements InstanceProvider {

    private final SinceDungeon plugin;
    private final Map<String, Integer> templateUsageCount = new ConcurrentHashMap<>();

    public DefaultInstanceProvider(SinceDungeon plugin) {
        this.plugin = plugin;
    }

    @Override
    public void initialize() {
    }

    @Override
    public void cleanup() {
        for (String templateName : templateUsageCount.keySet()) {
            World world = Bukkit.getWorld(templateName);
            if (world != null) {
                world.setAutoSave(true);
            }
        }
        templateUsageCount.clear();
    }

    @Override
    public CompletableFuture<World> createInstance(String templateName, String instanceId) {
        CompletableFuture<World> finalFuture = new CompletableFuture<>();

        if (SchedulerCompat.isFolia()) {
            String errorMsg = plugin.getLanguageManager().getString(
                    "admin.log.folia_world_mode_unsupported",
                    "[Instancing] Folia does not support Bukkit runtime world creation. Use Premium SCHEMATIC shared-world mode with a preloaded shared world."
            );
            plugin.getLogger().severe(errorMsg);
            finalFuture.completeExceptionally(new UnsupportedOperationException(errorMsg));
            return finalFuture;
        }

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
                executeAsyncCopyAndLoad(templateName, instanceId, finalFuture, templateW);
            } else {
                executeAsyncCopyAndLoad(templateName, instanceId, finalFuture, null);
            }
        });

        return finalFuture;
    }

    private void executeAsyncCopyAndLoad(String templateName, String instanceId, CompletableFuture<World> finalFuture, World templateW) {
        SchedulerCompat.runAsync(plugin, () -> {
            try {
                File source = new File(Bukkit.getWorldContainer(), templateName);
                File target = new File(Bukkit.getWorldContainer(), instanceId);

                if (!source.exists()) {
                    throw new RuntimeException("Template world folder not found: " + templateName);
                }

                boolean copied = WorldUtils.copyWorld(source, target);
                if (!copied) {
                    throw new RuntimeException("Failed to copy world files using WorldUtils.");
                }

                new File(target, "uid.dat").delete();

                SchedulerCompat.createWorld(plugin, createWorldCreator(instanceId)).whenComplete((world, throwable) -> SchedulerCompat.runGlobal(plugin, () -> {
                    try {
                        if (throwable != null) {
                            finalFuture.completeExceptionally(throwable);
                            return;
                        }
                        if (world != null) {
                            configureWorld(world);
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
                String logErr = plugin.getLanguageManager().getString("admin.log.world_gen_error", "[InstanceProvider] Error generating dungeon world: <error>");
                plugin.getLogger().severe(logErr.replace("<error>", ex.getMessage() != null ? ex.getMessage() : "Unknown"));
                finalFuture.completeExceptionally(ex);
                SchedulerCompat.runGlobal(plugin, () -> releaseTemplateLock(templateName, templateW));
            }
        });
    }

    private void releaseTemplateLock(String templateName, World templateW) {
        if (templateW == null) return;
        int newCount = templateUsageCount.merge(templateName, -1, Integer::sum);
        if (newCount <= 0) {
            templateW.setAutoSave(true);
            templateUsageCount.remove(templateName);
        }
    }

    @Override
    public void unloadAndDeleteInstance(World world) {
        if (world == null) return;
        File folder = world.getWorldFolder();

        List<Player> players = world.getPlayers();
        if (!players.isEmpty()) {
            Location safeLoc = Bukkit.getWorlds().get(0).getSpawnLocation();
            for (Player p : players) p.teleportAsync(safeLoc);
            SchedulerCompat.runGlobalLater(plugin, () -> performUnload(world, folder, unloadRetries()), unloadDelayTicks());
        } else {
            performUnload(world, folder, unloadRetries());
        }
    }

    private void performUnload(World world, File folder, int retries) {
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
                scheduleUnloadRetry(world, folder, retries);
                return;
            }
            if (Boolean.TRUE.equals(unloaded)) {
                SchedulerCompat.runAsyncLater(plugin, () -> {
                    if (!WorldUtils.deleteWorld(folder)) {
                        String logWarn = plugin.getLanguageManager().getString("admin.log.world_delete_fail", "Failed to fully delete world folder: <world>. It may be locked by another process.");
                        plugin.getLogger().warning(logWarn.replace("<world>", folder.getName()));
                    }
                }, deleteDelayTicks());
                return;
            }
            scheduleUnloadRetry(world, folder, retries);
        });
    }

    @Override
    public void forceUnloadAndDeleteInstance(World world) {
        if (world == null) return;
        File folder = world.getWorldFolder();

        for (Player p : world.getPlayers()) {
            p.teleportAsync(Bukkit.getWorlds().get(0).getSpawnLocation());
        }

        SchedulerCompat.unloadWorld(plugin, world, false).whenComplete((unloaded, throwable) -> {
            if (throwable == null && Boolean.TRUE.equals(unloaded)) {
                SchedulerCompat.runAsyncLater(plugin, () -> WorldUtils.deleteWorld(folder), deleteDelayTicks());
            } else {
                String logCritical = plugin.getLanguageManager().getString("admin.log.world_force_unload_fail", "CRITICAL: Failed to force-unload world <world> during shutdown!");
                plugin.getLogger().severe(logCritical.replace("<world>", world.getName()));
            }
        });
    }

    private WorldCreator createWorldCreator(String instanceId) {
        WorldCreator creator = new WorldCreator(instanceId);
        creator.generatorSettings(plugin.getConfigFile().getString("dungeon.world-generator-settings", ""));
        creator.generateStructures(plugin.getConfigFile().getBoolean("dungeon.generate-structures", false));
        return creator;
    }

    private void configureWorld(World world) {
        world.setAutoSave(plugin.getConfigFile().getBoolean("dungeon.instance-autosave", false));
        world.setTime(plugin.getConfigFile().getInt("dungeon.default-time", 6000));
        world.setStorm(plugin.getConfigFile().getBoolean("dungeon.default-storm", false));
        world.setThundering(plugin.getConfigFile().getBoolean("dungeon.default-thundering", false));
    }

    private void scheduleUnloadRetry(World world, File folder, int retries) {
        if (retries > 0) {
            String logRetry = plugin.getLanguageManager().getString("admin.log.world_unload_retry", "Retrying unload for world: <world> in 5 seconds...");
            if (logRetry != null) {
                plugin.getLogger().warning(logRetry.replace("<world>", world.getName()));
            }
            SchedulerCompat.runGlobalLater(plugin, () -> performUnload(world, folder, retries - 1), retryDelayTicks());
            return;
        }
        String logWarn = plugin.getLanguageManager().getString("admin.log.world_unload_fail", "Could not unload world: <world>");
        plugin.getLogger().warning(logWarn.replace("<world>", world.getName()));
    }

    private int unloadRetries() {
        return Math.max(0, plugin.getConfigFile().getInt("dungeon.unload-retries", 5));
    }

    private long unloadDelayTicks() {
        return Math.max(1L, plugin.getConfigFile().getInt("dungeon.unload-delay-ticks", 10));
    }

    private long retryDelayTicks() {
        return Math.max(1L, plugin.getConfigFile().getInt("dungeon.unload-retry-delay-ticks", 100));
    }

    private long deleteDelayTicks() {
        return Math.max(1L, plugin.getConfigFile().getInt("dungeon.delete-delay-ticks", 40));
    }
}
