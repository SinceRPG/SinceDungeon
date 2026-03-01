package net.danh.sinceDungeon.system;

import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.utils.WorldUtils;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.concurrent.CompletableFuture;

public class WorldManager {

    /**
     * Tạo dungeon world mới từ template (Async copy -> Sync load)
     */
    public static CompletableFuture<World> createDungeonWorldAsync(SinceDungeon plugin, String templateName, String instanceId) {
        // BƯỚC 1: Copy file ở Async Thread (Sử dụng WorldUtils tối ưu NIO)
        return CompletableFuture.supplyAsync(() -> {
            File source = new File(Bukkit.getWorldContainer(), templateName);
            File target = new File(Bukkit.getWorldContainer(), instanceId);

            if (!source.exists()) {
                throw new RuntimeException("Template world folder not found: " + templateName);
            }

            // [OPTIMIZED] Sử dụng WorldUtils để copy nhanh và an toàn
            boolean copied = WorldUtils.copyWorld(source, target);
            if (!copied) {
                throw new RuntimeException("Failed to copy world files using WorldUtils.");
            }

            // Xóa uid.dat để tránh lỗi trùng UID world (WorldUtils đã ignore khi copy, nhưng check lại cho chắc)
            new File(target, "uid.dat").delete();

            return instanceId;
        }).thenCompose(id -> {
            // BƯỚC 2: Chuyển về Main Thread để Load World (Bắt buộc phải sync với Bukkit API)
            CompletableFuture<World> mainThreadFuture = new CompletableFuture<>();

            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    WorldCreator creator = new WorldCreator(id);
                    creator.generatorSettings(""); // Giữ setting trống để tránh load generator mặc định nặng
                    creator.generateStructures(false); // Tắt structure để load nhanh hơn

                    World world = Bukkit.createWorld(creator);
                    if (world != null) {
                        // Tắt autosave để tối ưu hiệu năng dungeon (vì sẽ xóa sau khi chơi)
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

    /**
     * Unload và xóa world (Sử dụng WorldUtils)
     */
    public static void unloadAndDeleteWorld(SinceDungeon plugin, World world) {
        if (world == null) return;
        File folder = world.getWorldFolder();

        for (Player p : world.getPlayers()) {
            p.teleport(Bukkit.getWorlds().getFirst().getSpawnLocation());
        }

        // 1. Unload ở Main Thread (Bắt buộc)
        if (Bukkit.unloadWorld(world, false)) {
            plugin.getLogger().info("Unloaded dungeon world: " + world.getName());

            // 2. Delete file ở Async Thread (Sử dụng WorldUtils tối ưu)
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                if (WorldUtils.deleteWorld(folder)) {
                    // Log debug nếu cần
                } else {
                    plugin.getLogger().warning("Failed to delete world folder: " + folder.getName());
                }
            });
        } else {
            plugin.getLogger().warning("Could not unload world: " + world.getName() + ". Players might still be inside.");
        }
    }
}