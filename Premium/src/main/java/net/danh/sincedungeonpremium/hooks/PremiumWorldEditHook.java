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
import com.sk89q.worldedit.session.ClipboardHolder;
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
     * @param file The schematic file.
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
}