package net.danh.sincedungeonpremium.systems.instancing;

import net.danh.sinceDungeon.api.interfaces.InstanceProvider;
import net.danh.sinceDungeon.utils.ServerVersion;
import net.danh.sinceDungeon.utils.WorldUtils;
import net.danh.sincedungeonpremium.SinceDungeonPremium;
import net.danh.sincedungeonpremium.hooks.PremiumWorldEditHook;
import net.kyori.adventure.util.TriState;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Advanced Instancing Engine utilizing FAWE/WorldEdit.
 * Generates a void world and rapidly pastes a pre-configured .schem file to build the dungeon.
 * Eliminates I/O bottlenecking associated with full-folder world cloning.
 */
public class SchematicInstanceProvider implements InstanceProvider {

    private final SinceDungeonPremium plugin;

    public SchematicInstanceProvider(SinceDungeonPremium plugin) {
        this.plugin = plugin;
    }

    @Override
    public void initialize() {
        File schematicsFolder = new File(plugin.getDataFolder(), "schematics");
        if (!schematicsFolder.exists()) {
            schematicsFolder.mkdirs();
        }
    }

    @Override
    public void cleanup() {
        // No caching necessary for Schematic provider
    }

    @Override
    public CompletableFuture<World> createInstance(String templateName, String instanceId) {
        CompletableFuture<World> future = new CompletableFuture<>();

        // World Creation MUST happen on the Main Thread
        Bukkit.getScheduler().runTask(plugin, () -> {
            WorldCreator creator = new WorldCreator(instanceId);
            creator.generator(new VoidGenerator());
            creator.generateStructures(false);

            if (ServerVersion.isAtMost(1, 21, 9)) {
                creator.keepSpawnLoaded(TriState.FALSE);
            }

            World world = Bukkit.createWorld(creator);

            if (world == null) {
                String errorMsg = plugin.getFileManager().getMessageRaw("log.void_world_fail").replace("<instance>", instanceId);
                plugin.getLogger().severe(errorMsg);
                future.completeExceptionally(new RuntimeException(errorMsg));
                return;
            }

            // Move the heavy schematic pasting to an Asynchronous thread using FAWE
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                File schemFolder = new File(plugin.getDataFolder(), "schematics");
                File schemFile = new File(schemFolder, templateName + ".schem");

                if (!schemFile.exists()) {
                    schemFile = new File(schemFolder, templateName + ".schematic");
                }

                if (schemFile.exists()) {
                    int yLevel = plugin.getFileManager().getConfig().getInt("instancing.paste-y-level", 64);
                    Location pasteLoc = new Location(world, 0, yLevel, 0);

                    boolean success = PremiumWorldEditHook.pasteSchematic(schemFile, pasteLoc, true);
                    if (!success) {
                        String errMsg = plugin.getFileManager().getMessageRaw("log.schematic_paste_fail")
                                .replace("<file>", schemFile.getName())
                                .replace("<error>", "Unknown FAWE Operation Failure");
                        plugin.getLogger().warning(errMsg);
                    }
                } else {
                    String warnMsg = plugin.getFileManager().getMessageRaw("log.schematic_not_found").replace("<template>", templateName);
                    plugin.getLogger().warning(warnMsg);
                }

                // Return to Main Thread to finalize world settings and complete the Dungeon Game loop
                Bukkit.getScheduler().runTask(plugin, () -> {
                    world.setAutoSave(false);
                    world.setTime(6000);
                    world.setStorm(false);
                    world.setThundering(false);
                    future.complete(world);
                });
            });
        });

        return future;
    }

    @Override
    public void unloadAndDeleteInstance(World world) {
        if (world == null) return;
        File folder = world.getWorldFolder();

        List<Player> players = world.getPlayers();
        if (!players.isEmpty()) {
            Location safeLoc = Bukkit.getWorlds().get(0).getSpawnLocation();
            for (Player p : players) p.teleport(safeLoc);
            Bukkit.getScheduler().runTaskLater(plugin, () -> performUnload(world, folder, 5), 10L);
        } else {
            performUnload(world, folder, 5);
        }
    }

    private void performUnload(World world, File folder, int retries) {
        for (Entity e : world.getEntities()) {
            if (!(e instanceof Player)) {
                e.remove();
            }
        }

        if (Bukkit.unloadWorld(world, false)) {
            String logSuccess = plugin.getFileManager().getMessageRaw("log.world_unloaded").replace("<world>", world.getName());
            plugin.getLogger().info(logSuccess);

            Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
                if (!WorldUtils.deleteWorld(folder)) {
                    String logWarn = plugin.getFileManager().getMessageRaw("log.world_delete_fail").replace("<world>", folder.getName());
                    plugin.getLogger().warning(logWarn);
                }
            }, 40L);
        } else if (retries > 0) {
            String logRetry = plugin.getFileManager().getMessageRaw("log.world_unload_retry").replace("<world>", world.getName());
            plugin.getLogger().warning(logRetry);
            Bukkit.getScheduler().runTaskLater(plugin, () -> performUnload(world, folder, retries - 1), 100L);
        } else {
            String logWarn = plugin.getFileManager().getMessageRaw("log.world_unload_fail").replace("<world>", world.getName());
            plugin.getLogger().warning(logWarn);
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
            String logSuccess = plugin.getFileManager().getMessageRaw("log.world_force_unloaded").replace("<world>", world.getName());
            plugin.getLogger().info(logSuccess);

            Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> WorldUtils.deleteWorld(folder), 40L);
        } else {
            String logCritical = plugin.getFileManager().getMessageRaw("log.world_force_unload_fail").replace("<world>", world.getName());
            plugin.getLogger().severe(logCritical);
        }
    }
}