package net.danh.sincedungeonpremium.systems.instancing;

import net.danh.sinceDungeon.api.interfaces.InstanceProvider;
import net.danh.sinceDungeon.utils.SchedulerCompat;
import net.danh.sinceDungeon.utils.WorldUtils;
import net.danh.sincedungeonpremium.SinceDungeonPremium;
import net.danh.sincedungeonpremium.hooks.PremiumWorldEditHook;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Advanced schematic instancing engine.
 * In shared-world mode it keeps one void world loaded, pastes each dungeon into
 * an isolated coordinate region, and clears only that region when the run ends.
 */
public class SchematicInstanceProvider implements InstanceProvider {

    private final SinceDungeonPremium plugin;
    private final Map<String, InstanceRegion> regions = new ConcurrentHashMap<>();
    private final Queue<Integer> reusableSlots = new ConcurrentLinkedQueue<>();
    private final AtomicInteger nextSlot = new AtomicInteger(0);

    private World sharedWorld;

    public SchematicInstanceProvider(SinceDungeonPremium plugin) {
        this.plugin = plugin;
    }

    @Override
    public void initialize() {
        File schematicsFolder = new File(plugin.getDataFolder(), "schematics");
        if (!schematicsFolder.exists()) {
            schematicsFolder.mkdirs();
        }

        if (isSharedWorld()) {
            SchedulerCompat.runGlobal(plugin, this::prepareSharedWorld);
        }
    }

    @Override
    public void cleanup() {
        regions.clear();
        reusableSlots.clear();
        sharedWorld = null;
    }

    @Override
    public boolean isSharedWorld() {
        return plugin.getFileManager().getConfig().getBoolean("instancing.schematic.shared-world.enabled", true);
    }

    @Override
    public CompletableFuture<World> createInstance(String templateName, String instanceId) {
        if (!isSharedWorld()) {
            return createDedicatedWorldInstance(templateName, instanceId);
        }

        CompletableFuture<World> future = new CompletableFuture<>();

        SchedulerCompat.runGlobal(plugin, () -> {
            try {
                World world = ensureSharedWorld();
                InstanceRegion region = allocateRegion(instanceId, world);

                SchedulerCompat.runAsync(plugin, () -> {
                    File schemFile = resolveSchematicFile(templateName);
                    if (schemFile.exists()) {
                        boolean pasteAir = plugin.getFileManager().getConfig().getBoolean("instancing.schematic.paste-air", true);
                        boolean success = PremiumWorldEditHook.pasteSchematic(schemFile, region.pasteLocation(), pasteAir);
                        if (!success) {
                            String errMsg = plugin.getFileManager().getMessageRaw("log.schematic_paste_fail")
                                    .replace("<file>", schemFile.getName())
                                    .replace("<error>", plugin.getFileManager().getMessageRaw("log.worldedit_unknown_fail"));
                            plugin.getLogger().warning(errMsg);
                        }
                    } else {
                        String warnMsg = plugin.getFileManager().getMessageRaw("log.schematic_not_found").replace("<template>", templateName);
                        plugin.getLogger().warning(warnMsg);
                    }

                    SchedulerCompat.runGlobal(plugin, () -> {
                        configureWorld(world);
                        String logMsg = plugin.getFileManager().getMessageRaw("log.schematic_region_allocated");
                        plugin.getLogger().info(logMsg
                                .replace("<instance>", instanceId)
                                .replace("<world>", world.getName())
                                .replace("<x>", String.valueOf(region.origin().getBlockX()))
                                .replace("<z>", String.valueOf(region.origin().getBlockZ())));
                        future.complete(world);
                    });
                });
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });

        return future;
    }

    @Override
    public Location getInstanceOrigin(String instanceId, World world) {
        InstanceRegion region = regions.get(instanceId);
        if (region != null) return region.origin().clone();
        return InstanceProvider.super.getInstanceOrigin(instanceId, world);
    }

    @Override
    public Location getInstanceSpawnLocation(String instanceId, World world) {
        InstanceRegion region = regions.get(instanceId);
        if (region == null) return InstanceProvider.super.getInstanceSpawnLocation(instanceId, world);

        Vector spawnOffset = readVector("instancing.schematic.shared-world.spawn-location", "0,65,0");
        return new Location(
                world,
                region.origin().getX() + spawnOffset.getX() + 0.5,
                region.origin().getY() + spawnOffset.getY(),
                region.origin().getZ() + spawnOffset.getZ() + 0.5
        );
    }

    @Override
    public int getInstanceRadius(String instanceId) {
        InstanceRegion region = regions.get(instanceId);
        return region != null ? region.radius() : InstanceProvider.super.getInstanceRadius(instanceId);
    }

    @Override
    public void releaseInstance(String instanceId, World world) {
        if (isSharedWorldInstance(world)) {
            releaseSharedRegion(instanceId, world);
        } else {
            unloadAndDeleteInstance(world);
        }
    }

    @Override
    public void forceReleaseInstance(String instanceId, World world) {
        if (isSharedWorldInstance(world)) {
            releaseSharedRegion(instanceId, world);
        } else {
            forceUnloadAndDeleteInstance(world);
        }
    }

    private World ensureSharedWorld() {
        if (sharedWorld != null) return sharedWorld;

        String worldName = getSharedWorldName();
        World existing = Bukkit.getWorld(worldName);
        if (existing != null) {
            sharedWorld = existing;
            configureWorld(sharedWorld);
            return sharedWorld;
        }

        WorldCreator creator = new WorldCreator(worldName);
        creator.generator(new VoidGenerator());
        creator.generateStructures(false);

        sharedWorld = creator.createWorld();
        if (sharedWorld == null) {
            String errorMsg = plugin.getFileManager().getMessageRaw("log.void_world_fail").replace("<instance>", worldName);
            plugin.getLogger().severe(errorMsg);
            throw new IllegalStateException(errorMsg);
        }

        configureWorld(sharedWorld);
        String logMsg = plugin.getFileManager().getMessageRaw("log.shared_world_ready");
        plugin.getLogger().info(logMsg.replace("<world>", sharedWorld.getName()));
        return sharedWorld;
    }

    private void prepareSharedWorld() {
        try {
            ensureSharedWorld();
        } catch (IllegalStateException e) {
            plugin.getLogger().warning(e.getMessage());
        }
    }

    private CompletableFuture<World> createDedicatedWorldInstance(String templateName, String instanceId) {
        CompletableFuture<World> future = new CompletableFuture<>();

        SchedulerCompat.runGlobal(plugin, () -> {
            WorldCreator creator = new WorldCreator(instanceId);
            creator.generator(new VoidGenerator());
            creator.generateStructures(false);

            World world = creator.createWorld();
            if (world == null) {
                String errorMsg = plugin.getFileManager().getMessageRaw("log.void_world_fail").replace("<instance>", instanceId);
                plugin.getLogger().severe(errorMsg);
                future.completeExceptionally(new RuntimeException(errorMsg));
                return;
            }

            SchedulerCompat.runAsync(plugin, () -> {
                File schemFile = resolveSchematicFile(templateName);
                if (schemFile.exists()) {
                    int yLevel = plugin.getFileManager().getConfig().getInt("instancing.paste-y-level", 64);
                    boolean pasteAir = plugin.getFileManager().getConfig().getBoolean("instancing.schematic.paste-air", true);
                    Location pasteLoc = new Location(world, 0, yLevel, 0);

                    boolean success = PremiumWorldEditHook.pasteSchematic(schemFile, pasteLoc, pasteAir);
                    if (!success) {
                        String errMsg = plugin.getFileManager().getMessageRaw("log.schematic_paste_fail")
                                .replace("<file>", schemFile.getName())
                                .replace("<error>", plugin.getFileManager().getMessageRaw("log.worldedit_unknown_fail"));
                        plugin.getLogger().warning(errMsg);
                    }
                } else {
                    String warnMsg = plugin.getFileManager().getMessageRaw("log.schematic_not_found").replace("<template>", templateName);
                    plugin.getLogger().warning(warnMsg);
                }

                SchedulerCompat.runGlobal(plugin, () -> {
                    configureWorld(world);
                    future.complete(world);
                });
            });
        });

        return future;
    }

    private InstanceRegion allocateRegion(String instanceId, World world) {
        Integer reused = reusableSlots.poll();
        int slot = reused != null ? reused : nextSlot.getAndIncrement();
        int gridWidth = Math.max(1, plugin.getFileManager().getConfig().getInt("instancing.schematic.shared-world.grid-width", 128));
        int spacing = Math.max(128, plugin.getFileManager().getConfig().getInt("instancing.schematic.shared-world.region-spacing", 2048));
        int radius = Math.max(32, plugin.getFileManager().getConfig().getInt("instancing.schematic.shared-world.region-radius", spacing / 2 - 16));
        int coordinateYOffset = plugin.getFileManager().getConfig().getInt("instancing.schematic.shared-world.coordinate-y-offset", 0);
        int pasteY = plugin.getFileManager().getConfig().getInt("instancing.paste-y-level", 64);

        int x = (slot % gridWidth) * spacing;
        int z = (slot / gridWidth) * spacing;
        Location origin = new Location(world, x, coordinateYOffset, z);
        Location pasteLocation = new Location(world, x, pasteY, z);

        InstanceRegion region = new InstanceRegion(slot, origin, pasteLocation, radius);
        regions.put(instanceId, region);
        return region;
    }

    private void releaseSharedRegion(String instanceId, World world) {
        InstanceRegion region = regions.remove(instanceId);
        if (region == null || world == null) return;

        boolean clearOnRelease = plugin.getFileManager().getConfig().getBoolean("instancing.schematic.shared-world.clear-on-release", true);
        if (!clearOnRelease) {
            SchedulerCompat.runAtLocation(plugin, region.origin(), () -> {
                removeRegionEntities(world, region);
                reusableSlots.offer(region.slot());
            });
            return;
        }

        SchedulerCompat.runAtLocation(plugin, region.origin(), () -> {
            removeRegionEntities(world, region);
            SchedulerCompat.runAsync(plugin, () -> {
                boolean cleared = PremiumWorldEditHook.clearCuboid(getClearMin(world, region), getClearMax(world, region));
                SchedulerCompat.runAtLocation(plugin, region.origin(), () -> {
                    if (cleared) {
                        reusableSlots.offer(region.slot());
                        String logMsg = plugin.getFileManager().getMessageRaw("log.schematic_region_released");
                        plugin.getLogger().info(logMsg.replace("<instance>", instanceId));
                    }
                });
            });
        });
    }

    private void removeRegionEntities(World world, InstanceRegion region) {
        for (Entity entity : world.getNearbyEntities(region.entityScanCenter(world), region.radius(), region.verticalRadius(world), region.radius())) {
            if (!(entity instanceof Player) && region.contains(entity.getLocation())) {
                entity.remove();
            }
        }
    }

    private Location getClearMin(World world, InstanceRegion region) {
        int minY = Math.max(world.getMinHeight(), plugin.getFileManager().getConfig().getInt("instancing.schematic.shared-world.clear-min-y", world.getMinHeight()));
        return new Location(
                world,
                region.origin().getBlockX() - region.radius(),
                minY,
                region.origin().getBlockZ() - region.radius()
        );
    }

    private Location getClearMax(World world, InstanceRegion region) {
        int maxY = Math.min(world.getMaxHeight() - 1, plugin.getFileManager().getConfig().getInt("instancing.schematic.shared-world.clear-max-y", world.getMaxHeight() - 1));
        return new Location(
                world,
                region.origin().getBlockX() + region.radius(),
                maxY,
                region.origin().getBlockZ() + region.radius()
        );
    }

    private File resolveSchematicFile(String templateName) {
        File schemFolder = new File(plugin.getDataFolder(), "schematics");
        File schemFile = new File(schemFolder, templateName + ".schem");
        if (!schemFile.exists()) {
            schemFile = new File(schemFolder, templateName + ".schematic");
        }
        return schemFile;
    }

    private boolean isSharedWorldInstance(World world) {
        return isSharedWorld() && world != null && world.getName().equalsIgnoreCase(getSharedWorldName());
    }

    private String getSharedWorldName() {
        return plugin.getFileManager().getConfig().getString("instancing.schematic.shared-world.name", "SDPremium_Schematic");
    }

    private Vector readVector(String path, String fallback) {
        String value = plugin.getFileManager().getConfig().getString(path, fallback);
        try {
            return parseVector(value);
        } catch (Exception e) {
            try {
                return parseVector(fallback);
            } catch (Exception ignored) {
                return new Vector(0, 0, 0);
            }
        }
    }

    private Vector parseVector(String value) {
        String[] parts = value.replace(" ", "").split(",");
        if (parts.length < 3) throw new IllegalArgumentException("Missing XYZ values");
        return new Vector(
                Double.parseDouble(parts[0]),
                Double.parseDouble(parts[1]),
                Double.parseDouble(parts[2])
        );
    }

    private void configureWorld(World world) {
        world.setAutoSave(plugin.getFileManager().getConfig().getBoolean("instancing.world-settings.autosave", false));
        world.setTime(plugin.getFileManager().getConfig().getInt("instancing.world-settings.time", 6000));
        world.setStorm(plugin.getFileManager().getConfig().getBoolean("instancing.world-settings.storm", false));
        world.setThundering(plugin.getFileManager().getConfig().getBoolean("instancing.world-settings.thundering", false));
    }

    @Override
    public void unloadAndDeleteInstance(World world) {
        if (world == null || isSharedWorldInstance(world)) return;
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
            String logSuccess = plugin.getFileManager().getMessageRaw("log.world_unloaded").replace("<world>", world.getName());
            plugin.getLogger().info(logSuccess);

            SchedulerCompat.runAsyncLater(plugin, () -> {
                if (!WorldUtils.deleteWorld(folder)) {
                    String logWarn = plugin.getFileManager().getMessageRaw("log.world_delete_fail").replace("<world>", folder.getName());
                    plugin.getLogger().warning(logWarn);
                }
            }, deleteDelayTicks());
                return;
            }
            scheduleUnloadRetry(world, folder, retries);
        });
    }

    @Override
    public void forceUnloadAndDeleteInstance(World world) {
        if (world == null || isSharedWorldInstance(world)) return;
        File folder = world.getWorldFolder();

        for (Player p : world.getPlayers()) {
            p.teleportAsync(Bukkit.getWorlds().get(0).getSpawnLocation());
        }

        SchedulerCompat.unloadWorld(plugin, world, false).whenComplete((unloaded, throwable) -> {
            if (throwable == null && Boolean.TRUE.equals(unloaded)) {
                String logSuccess = plugin.getFileManager().getMessageRaw("log.world_force_unloaded").replace("<world>", world.getName());
                plugin.getLogger().info(logSuccess);
                SchedulerCompat.runAsyncLater(plugin, () -> WorldUtils.deleteWorld(folder), deleteDelayTicks());
            } else {
                String logCritical = plugin.getFileManager().getMessageRaw("log.world_force_unload_fail").replace("<world>", world.getName());
                plugin.getLogger().severe(logCritical);
            }
        });
    }

    private void scheduleUnloadRetry(World world, File folder, int retries) {
        if (retries > 0) {
            String logRetry = plugin.getFileManager().getMessageRaw("log.world_unload_retry").replace("<world>", world.getName());
            plugin.getLogger().warning(logRetry);
            SchedulerCompat.runGlobalLater(plugin, () -> performUnload(world, folder, retries - 1), retryDelayTicks());
            return;
        }
        String logWarn = plugin.getFileManager().getMessageRaw("log.world_unload_fail").replace("<world>", world.getName());
        plugin.getLogger().warning(logWarn);
    }

    private int unloadRetries() {
        return Math.max(0, plugin.getFileManager().getConfig().getInt("instancing.world-settings.unload-retries", 5));
    }

    private long unloadDelayTicks() {
        return Math.max(1L, plugin.getFileManager().getConfig().getInt("instancing.world-settings.unload-delay-ticks", 10));
    }

    private long retryDelayTicks() {
        return Math.max(1L, plugin.getFileManager().getConfig().getInt("instancing.world-settings.unload-retry-delay-ticks", 100));
    }

    private long deleteDelayTicks() {
        return Math.max(1L, plugin.getFileManager().getConfig().getInt("instancing.world-settings.delete-delay-ticks", 40));
    }

    private record InstanceRegion(int slot, Location origin, Location pasteLocation, int radius) {
        boolean contains(Location location) {
            return location != null
                    && location.getWorld() != null
                    && location.getWorld().equals(origin.getWorld())
                    && Math.abs(location.getX() - origin.getX()) <= radius
                    && Math.abs(location.getZ() - origin.getZ()) <= radius;
        }

        int verticalRadius(World world) {
            return Math.max(1, (world.getMaxHeight() - world.getMinHeight()) / 2);
        }

        Location entityScanCenter(World world) {
            return new Location(world, origin.getX(), world.getMinHeight() + verticalRadius(world), origin.getZ());
        }
    }
}
