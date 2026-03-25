package net.danh.sinceDungeon.system;

import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.utils.ServerVersion;
import net.danh.sinceDungeon.utils.WorldUtils;
import net.kyori.adventure.util.TriState;
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

        World templateW = Bukkit.getWorld(templateName);

        if (templateW != null) {
            plugin.getLogger().warning("[An Toàn Dữ Liệu] Thế giới mẫu '" + templateName + "' đang hoạt động. Tiến hành Unload để mở khóa file hệ thống (Moonrise I/O)...");
            Location safeLoc = Bukkit.getWorlds().get(0).getSpawnLocation();
            for (Player p : templateW.getPlayers()) {
                p.teleport(safeLoc);
                p.sendMessage("§cThế giới mẫu đang được hệ thống đóng gói để phục vụ người chơi đi Dungeon!");
            }
            Bukkit.unloadWorld(templateW, true);
        }

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
                    if (ServerVersion.isAtMost(1, 21, 9)) creator.keepSpawnLoaded(TriState.FALSE);

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
            plugin.getLogger().severe("[WorldManager] Lỗi nạp thế giới Dungeon: " + ex.getMessage());
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
                p.teleportAsync(safeLoc);
            }
            Bukkit.getScheduler().runTaskLater(plugin, () -> performUnload(plugin, world, folder), 10L);
        } else {
            performUnload(plugin, world, folder);
        }
    }

    private static void performUnload(SinceDungeon plugin, World world, File folder) {
        if (Bukkit.unloadWorld(world, false)) {
            plugin.getLogger().info("Đã đóng Dungeon: " + world.getName());

            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                if (!WorldUtils.deleteWorld(folder)) {
                    plugin.getLogger().warning("Không thể dọn dẹp sạch sẽ: " + folder.getName() + " (Hệ điều hành đang khóa file)");
                }
            });
        } else {
            plugin.getLogger().warning("Không thể Unload thế giới: " + world.getName() + " (Lỗi từ Bukkit Core).");
        }
    }

    public static void forceUnloadAndDelete(SinceDungeon plugin, World world) {
        if (world == null) return;
        File folder = world.getWorldFolder();

        for (Player p : world.getPlayers()) {
            p.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
        }

        if (Bukkit.unloadWorld(world, false)) {
            plugin.getLogger().info("Ép đóng Dungeon: " + world.getName());
            WorldUtils.deleteWorld(folder);
        } else {
            plugin.getLogger().severe("NGUY HIỂM: Không thể ép đóng thế giới " + world.getName() + " lúc tắt server!");
        }
    }
}