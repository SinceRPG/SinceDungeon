package net.danh.sinceDungeon.system;

import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.utils.ServerVersion;
import net.danh.sinceDungeon.utils.WorldUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class WorldManager {

    public static CompletableFuture<World> createDungeonWorldAsync(SinceDungeon plugin, String templateName, String instanceId) {
        return CompletableFuture.supplyAsync(() -> {
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
        }).thenCompose(id -> {
            CompletableFuture<World> mainThreadFuture = new CompletableFuture<>();

            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    WorldCreator creator = new WorldCreator(id);
                    creator.generatorSettings("");
                    creator.generateStructures(false);
                    if (ServerVersion.isAtMost(1, 21, 9))
                        creator.keepSpawnLoaded(false);

                    World world = Bukkit.createWorld(creator);
                    if (world != null) {
                        world.setAutoSave(false);
                        mainThreadFuture.complete(world);
                    } else {
                        mainThreadFuture.completeExceptionally(new RuntimeException("Bukkit returned null for created world."));
                    }
                } catch (Exception e) {
                    mainThreadFuture.completeExceptionally(e);
                }
            });

            return mainThreadFuture;
        }).exceptionally(ex -> {
            plugin.getLogger().severe("[WorldManager] Lỗi tạo world dungeon: " + ex.getMessage());
            ex.printStackTrace();
            return null;
        });
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

            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                if (!WorldUtils.deleteWorld(folder)) {
                    plugin.getLogger().warning("Failed to fully delete world folder: " + folder.getName() + " (Some files might be locked by OS)");
                }
            });
        } else {
            plugin.getLogger().warning("Could not unload world: " + world.getName() + " (Bukkit refused - Players or chunks might be stuck).");
        }
    }
}