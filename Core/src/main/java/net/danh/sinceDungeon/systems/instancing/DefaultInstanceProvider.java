package net.danh.sinceDungeon.systems.instancing;

import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.api.interfaces.InstanceProvider;
import net.danh.sinceDungeon.utils.ServerVersion;
import net.danh.sinceDungeon.utils.WorldUtils;
import net.kyori.adventure.util.TriState;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
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
        templateUsageCount.clear();
    }

    /**
     * Implements the API-driven strategy to generate dungeon worlds safely.
     * Validates input parameters to stop malicious injections prior to I/O tasks.
     *
     * @param templateName The name of the source template directory.
     * @param instanceId   The newly assigned session ID for the clone.
     * @return CompletableFuture yielding the loaded Bukkit World.
     */
    @Override
    public CompletableFuture<World> createInstance(String templateName, String instanceId) {
        CompletableFuture<World> finalFuture = new CompletableFuture<>();

        if (templateName == null || templateName.contains("/") || templateName.contains("\\") || templateName.contains(".")) {
            String errorMsg = plugin.getLanguageManager().getString("error.path_traversal", "Path Traversal attack detected in World Name: <world>");
            finalFuture.completeExceptionally(new IllegalArgumentException(errorMsg.replace("<world>", templateName)));
            return finalFuture;
        }

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

        return finalFuture;
    }

    private void executeAsyncCopyAndLoad(String templateName, String instanceId, CompletableFuture<World> finalFuture, World templateW) {
        CompletableFuture.supplyAsync(() -> {
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
            return instanceId;

        }).thenAccept(id -> Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                WorldCreator creator = new WorldCreator(id);
                creator.generatorSettings("");
                creator.generateStructures(false);

                if (ServerVersion.isAtMost(1, 21, 9)) creator.keepSpawnLoaded(TriState.FALSE);

                World world = Bukkit.createWorld(creator);
                if (world != null) {
                    world.setAutoSave(false);
                    world.setTime(6000);
                    world.setStorm(false);
                    world.setThundering(false);
                    finalFuture.complete(world);
                } else {
                    finalFuture.completeExceptionally(new RuntimeException("Bukkit returned null for created world."));
                }
            } catch (Exception e) {
                finalFuture.completeExceptionally(e);
            }

            releaseTemplateLock(templateName, templateW);
        })).exceptionally(ex -> {
            String logErr = plugin.getLanguageManager().getString("admin.log.world_gen_error", "[InstanceProvider] Error generating dungeon world: <error>");
            plugin.getLogger().severe(logErr.replace("<error>", ex.getMessage()));
            finalFuture.completeExceptionally(ex);
            Bukkit.getScheduler().runTask(plugin, () -> releaseTemplateLock(templateName, templateW));
            return null;
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
            for (Player p : players) p.teleport(safeLoc);
            Bukkit.getScheduler().runTaskLater(plugin, () -> performUnload(world, folder), 1L);
        } else {
            performUnload(world, folder);
        }
    }

    private void performUnload(World world, File folder) {
        if (Bukkit.unloadWorld(world, false)) {
            Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
                if (!WorldUtils.deleteWorld(folder)) {
                    String logWarn = plugin.getLanguageManager().getString("admin.log.world_delete_fail", "Failed to fully delete world folder: <world>. It may be locked by another process.");
                    plugin.getLogger().warning(logWarn.replace("<world>", folder.getName()));
                }
            }, 40L);
        } else {
            String logWarn = plugin.getLanguageManager().getString("admin.log.world_unload_fail", "Could not unload world: <world>");
            plugin.getLogger().warning(logWarn.replace("<world>", world.getName()));
        }
    }

    @Override
    public void forceUnloadAndDeleteInstance(World world) {
        if (world == null) return;
        File folder = world.getWorldFolder();

        for (Player p : world.getPlayers()) {
            p.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
        }

        if (Bukkit.unloadWorld(world, false)) {
            Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> WorldUtils.deleteWorld(folder), 40L);
        } else {
            String logCritical = plugin.getLanguageManager().getString("admin.log.world_force_unload_fail", "CRITICAL: Failed to force-unload world <world> during shutdown!");
            plugin.getLogger().severe(logCritical.replace("<world>", world.getName()));
        }
    }
}