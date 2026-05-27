package net.danh.sinceDungeon.actions.impl;

import net.danh.sinceDungeon.managers.DungeonLoader;
import net.danh.sinceDungeon.models.DungeonGame;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Temporarily places seal blocks for boss encounters and restores the original
 * blocks when the action ends.
 */
public class BossSeal {
    private final boolean enabled;
    private final Material material;
    private final List<Region> regions;
    private final List<BlockState> changedBlocks = new ArrayList<>();
    private boolean applied = false;

    private BossSeal(boolean enabled, Material material, List<Region> regions) {
        this.enabled = enabled;
        this.material = material;
        this.regions = regions;
    }

    public static BossSeal disabled() {
        return new BossSeal(false, Material.BARRIER, new ArrayList<>());
    }

    public static BossSeal fromConfig(Object raw) {
        if (raw == null) return disabled();

        Object enabledObj = get(raw, "enabled");
        boolean enabled = enabledObj != null && Boolean.parseBoolean(enabledObj.toString());
        if (!enabled) return disabled();

        Material material = Material.BARRIER;
        Object materialObj = get(raw, "material");
        if (materialObj != null) {
            Material parsed = Material.matchMaterial(materialObj.toString());
            if (parsed != null && parsed.isBlock()) {
                material = parsed;
            }
        }

        List<Region> regions = new ArrayList<>();
        Object regionsObj = get(raw, "regions");
        if (regionsObj instanceof List<?> list) {
            for (Object item : list) {
                Region region = parseRegion(item);
                if (region != null) regions.add(region);
            }
        }

        Region directRegion = parseRegion(raw);
        if (directRegion != null) regions.add(directRegion);

        return regions.isEmpty() ? disabled() : new BossSeal(true, material, regions);
    }

    private static Region parseRegion(Object raw) {
        Object corner1Obj = get(raw, "corner1");
        Object corner2Obj = get(raw, "corner2");
        if (corner1Obj == null || corner2Obj == null) return null;

        Vector corner1 = DungeonLoader.parseVector(corner1Obj.toString());
        Vector corner2 = DungeonLoader.parseVector(corner2Obj.toString());
        return new Region(corner1, corner2);
    }

    private static Object get(Object raw, String key) {
        if (raw instanceof ConfigurationSection section) {
            return section.get(key);
        }
        if (raw instanceof Map<?, ?> map) {
            return map.get(key);
        }
        return null;
    }

    public void apply(DungeonGame game) {
        if (!enabled || applied || game.getWorld() == null) return;

        World world = game.getWorld();
        for (Region region : regions) {
            int minX = Math.min(region.corner1.getBlockX(), region.corner2.getBlockX());
            int maxX = Math.max(region.corner1.getBlockX(), region.corner2.getBlockX());
            int minY = Math.min(region.corner1.getBlockY(), region.corner2.getBlockY());
            int maxY = Math.max(region.corner1.getBlockY(), region.corner2.getBlockY());
            int minZ = Math.min(region.corner1.getBlockZ(), region.corner2.getBlockZ());
            int maxZ = Math.max(region.corner1.getBlockZ(), region.corner2.getBlockZ());

            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        Block block = world.getBlockAt(x, y, z);
                        changedBlocks.add(block.getState());
                        block.setType(material, false);
                    }
                }
            }
        }

        applied = true;
    }

    public void remove() {
        if (!applied) return;

        for (int i = changedBlocks.size() - 1; i >= 0; i--) {
            changedBlocks.get(i).update(true, false);
        }
        changedBlocks.clear();
        applied = false;
    }

    private record Region(Vector corner1, Vector corner2) {
    }
}
