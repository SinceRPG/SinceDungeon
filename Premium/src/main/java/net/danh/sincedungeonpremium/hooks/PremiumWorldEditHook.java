package net.danh.sincedungeonpremium.hooks;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.world.block.BlockTypes;
import net.danh.sincedungeonpremium.SinceDungeonPremium;
import org.bukkit.Location;

import java.io.File;
import java.io.FileInputStream;

/**
 * Hooks into WorldEdit / FastAsyncWorldEdit to safely load and paste schematics.
 * Operations are strictly handled with try-with-resources to prevent EditSession memory leaks.
 */
public class PremiumWorldEditHook {

    /**
     * Pastes a schematic file into the world safely.
     *
     * @param file     The schematic file.
     * @param location The Bukkit location to paste at.
     * @param pasteAir Whether air blocks in the schematic should overwrite existing blocks.
     * @return True if successful, false otherwise.
     */
    public static boolean pasteSchematic(File file, Location location, boolean pasteAir) {
        ClipboardFormat format = ClipboardFormats.findByFile(file);
        if (format == null) return false;

        // Try-with-resources ensures buffers are closed to prevent RAM leaks
        try (ClipboardReader reader = format.getReader(new FileInputStream(file))) {
            Clipboard clipboard = reader.read();

            try (EditSession editSession = WorldEdit.getInstance().newEditSession(BukkitAdapter.adapt(location.getWorld()))) {
                Operation operation = new ClipboardHolder(clipboard)
                        .createPaste(editSession)
                        .to(BlockVector3.at(location.getBlockX(), location.getBlockY(), location.getBlockZ()))
                        .ignoreAirBlocks(!pasteAir)
                        .build();

                Operations.complete(operation);
                return true;
            }
        } catch (Exception e) {
            String logMsg = SinceDungeonPremium.getInstance().getFileManager().getMessageRaw("log.schematic_paste_fail");
            SinceDungeonPremium.getInstance().getLogger().severe(logMsg.replace("<file>", file.getName()) + " - " + e.getMessage());
            return false;
        }
    }

    /**
     * Clears a pasted schematic region without unloading the shared dungeon world.
     * The provider calls this after a run ends so the slot can be reused safely.
     *
     * @param min The minimum cuboid corner.
     * @param max The maximum cuboid corner.
     * @return True if WorldEdit accepted the clear operation.
     */
    public static boolean clearCuboid(Location min, Location max) {
        if (min == null || max == null || min.getWorld() == null || !min.getWorld().equals(max.getWorld())) {
            return false;
        }

        try (EditSession editSession = WorldEdit.getInstance().newEditSession(BukkitAdapter.adapt(min.getWorld()))) {
            CuboidRegion region = new CuboidRegion(
                    BukkitAdapter.adapt(min.getWorld()),
                    BlockVector3.at(min.getBlockX(), min.getBlockY(), min.getBlockZ()),
                    BlockVector3.at(max.getBlockX(), max.getBlockY(), max.getBlockZ())
            );
            editSession.setBlocks((Region) region, BlockTypes.AIR.getDefaultState());
            return true;
        } catch (Exception e) {
            String logMsg = SinceDungeonPremium.getInstance().getFileManager().getMessageRaw("log.region_clear_fail");
            SinceDungeonPremium.getInstance().getLogger().warning(logMsg.replace("<error>", e.getMessage()));
            return false;
        }
    }
}
