package net.danh.sinceDungeon.system;

import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.utils.ServerVersion;
import net.danh.sinceDungeon.utils.WorldUtils;
import net.kyori.adventure.util.TriState;
import org.bukkit.*;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class WorldManager {

    private static final Map<String, Integer> templateUsageCount = new ConcurrentHashMap<>();

    public static CompletableFuture<World> createDungeonWorldAsync(SinceDungeon plugin, String templateName, String instanceId) {
        CompletableFuture<World> finalFuture = new CompletableFuture<>();

        if (templateName == null || templateName.contains("/") || templateName.contains("\\") || templateName.contains(".")) {
            finalFuture.completeExceptionally(new IllegalArgumentException("Path Traversal attack detected in World Name: " + templateName));
            return finalFuture;
        }

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

        return finalFuture;
    }

    private static void executeAsyncCopyAndLoad(SinceDungeon plugin, String templateName, String instanceId, CompletableFuture<World> finalFuture, World templateW) {
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

        }).thenAccept(id -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    WorldCreator creator = new WorldCreator(id);
                    creator.generatorSettings("");
                    creator.generateStructures(false);

                    if (ServerVersion.isAtMost(1, 21, 9)) creator.keepSpawnLoaded(TriState.FALSE);

                    World world = Bukkit.createWorld(creator);
                    if (world != null) {
                        world.setAutoSave(false);
                        if (ServerVersion.isAtMost(1, 21, 10)) {
                            world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
                            world.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
                            world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
                            world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
                        } else {
                            world.setGameRule(GameRules.SPAWN_MOBS, false);
                            world.setGameRule(GameRules.SHOW_ADVANCEMENT_MESSAGES, false);
                            world.setGameRule(GameRules.ADVANCE_WEATHER, false);
                            world.setGameRule(GameRules.ADVANCE_TIME, false);
                        }
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
            });

        }).exceptionally(ex -> {
            plugin.getLogger().severe("[WorldManager] Error generating dungeon world: " + ex.getMessage());
            ex.printStackTrace();
            finalFuture.completeExceptionally(ex);

            Bukkit.getScheduler().runTask(plugin, () -> releaseTemplateLock(templateName, templateW));
            return null;
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

    public static void unloadAndDeleteWorld(SinceDungeon plugin, World world) {
        if (world == null) return;
        File folder = world.getWorldFolder();

        List<Player> players = world.getPlayers();
        if (!players.isEmpty()) {
            Location safeLoc = Bukkit.getWorlds().get(0).getSpawnLocation();
            for (Player p : players) {
                p.teleport(safeLoc);
            }
            Bukkit.getScheduler().runTaskLater(plugin, () -> performUnload(plugin, world, folder), 1L);
        } else {
            performUnload(plugin, world, folder);
        }
    }

    private static void performUnload(SinceDungeon plugin, World world, File folder) {
        if (Bukkit.unloadWorld(world, false)) {
            plugin.getLogger().info("Unloaded dungeon world: " + world.getName());


            Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
                if (!WorldUtils.deleteWorld(folder)) {
                    plugin.getLogger().warning("Failed to fully delete world folder: " + folder.getName() + ". It may be locked by another process.");
                }
            }, 40L);
        } else {
            plugin.getLogger().warning("Could not unload world: " + world.getName());
        }
    }

    public static void forceUnloadAndDelete(SinceDungeon plugin, World world) {
        if (world == null) return;
        File folder = world.getWorldFolder();

        for (Player p : world.getPlayers()) {
            p.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
        }

        if (Bukkit.unloadWorld(world, false)) {
            plugin.getLogger().info("Force unloaded dungeon world: " + world.getName());

            Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
                WorldUtils.deleteWorld(folder);
            }, 40L);

        } else {
            plugin.getLogger().severe("CRITICAL: Failed to force-unload world " + world.getName() + " during shutdown!");
        }
    }
}