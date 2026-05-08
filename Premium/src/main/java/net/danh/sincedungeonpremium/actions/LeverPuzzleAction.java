package net.danh.sincedungeonpremium.actions;

import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.actions.DungeonAction;
import net.danh.sinceDungeon.managers.DungeonLoader;
import net.danh.sinceDungeon.models.DungeonGame;
import net.danh.sinceDungeon.utils.SoundUtils;
import net.danh.sincedungeonpremium.SinceDungeonPremium;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.FaceAttachable;
import org.bukkit.block.data.type.Switch;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Handles the Lever Puzzle objective.
 * Physically places levers with random states at defined locations.
 * Requires players to interact with them in the exact order.
 * Optimized: Caches sounds during start() to prevent YAML IO reads during interact events.
 */
public class LeverPuzzleAction extends DungeonAction {

    private final List<String> rawLevers;
    private final int failTimePenalty;
    private final List<Location> parsedLevers = new ArrayList<>();
    private final Random random = new Random();
    private int currentIndex = 0;

    private Sound soundSuccess;
    private Sound soundFail;

    public LeverPuzzleAction(List<String> rawLevers, int failTimePenalty) {
        this.rawLevers = rawLevers;
        this.failTimePenalty = failTimePenalty;
    }

    @Override
    public void start(DungeonGame game) {
        if (game.getWorld() == null) {
            this.forceComplete();
            return;
        }

        // JIT Optimization: Pre-fetch sounds to prevent config lookups mid-combat
        String s1 = SinceDungeonPremium.getInstance().getFileManager().getConfig().getString("sounds.puzzle_success", "block.note_block.chime");
        soundSuccess = SoundUtils.getSound(s1);
        String s2 = SinceDungeonPremium.getInstance().getFileManager().getConfig().getString("sounds.puzzle_fail", "block.note_block.bass");
        soundFail = SoundUtils.getSound(s2);

        for (String s : rawLevers) {
            Vector vec = DungeonLoader.parseVector(s);
            Location loc = new Location(game.getWorld(), vec.getBlockX(), vec.getBlockY(), vec.getBlockZ());
            parsedLevers.add(loc);

            Block block = loc.getBlock();
            block.setType(Material.LEVER);

            if (block.getBlockData() instanceof Switch leverData) {
                leverData.setAttachedFace(FaceAttachable.AttachedFace.FLOOR);
                leverData.setPowered(random.nextBoolean()); // Randomize initial visual state
                block.setBlockData(leverData);
            }
        }
    }

    @Override
    public void cleanup(DungeonGame game) {
        super.cleanup(game);
        if (game.getWorld() != null) {
            for (Location loc : parsedLevers) {
                if (loc.getBlock().getType() == Material.LEVER) {
                    loc.getBlock().setType(Material.AIR);
                }
            }
        }
        parsedLevers.clear();
        currentIndex = 0;
    }

    @Override
    public void onEvent(DungeonGame game, Event event) {
        if (completed || parsedLevers.isEmpty()) return;

        if (event instanceof PlayerInteractEvent e) {
            if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
            Block b = e.getClickedBlock();

            if (b != null && b.getType() == Material.LEVER && b.getWorld().equals(game.getWorld())) {
                Location clickedLoc = b.getLocation();

                boolean isPartOfPuzzle = parsedLevers.stream().anyMatch(l ->
                        l.getBlockX() == clickedLoc.getBlockX() &&
                                l.getBlockY() == clickedLoc.getBlockY() &&
                                l.getBlockZ() == clickedLoc.getBlockZ()
                );

                if (isPartOfPuzzle) {
                    Location expectedLoc = parsedLevers.get(currentIndex);

                    if (expectedLoc.getBlockX() == clickedLoc.getBlockX() &&
                            expectedLoc.getBlockY() == clickedLoc.getBlockY() &&
                            expectedLoc.getBlockZ() == clickedLoc.getBlockZ()) {

                        currentIndex++;
                        if (soundSuccess != null) {
                            e.getPlayer().playSound(clickedLoc, soundSuccess, 1f, 2f);
                        }

                        if (currentIndex >= parsedLevers.size()) {
                            game.broadcastMessage("action.puzzle_solved");
                            this.forceComplete();
                        }
                    } else {
                        e.setCancelled(true);
                        currentIndex = 0; // Reset progression

                        if (soundFail != null) {
                            e.getPlayer().playSound(clickedLoc, soundFail, 1f, 0.5f);
                        }

                        // Apply Time Penalty by pushing the start time further into the past
                        if (this.getTimeLimitSeconds() > 0 && failTimePenalty > 0) {
                            this.setStartTimeMillis(this.getStartTimeMillis() - (failTimePenalty * 1000L));
                            game.broadcastMessage("action.puzzle_failed_penalty", "<time>", String.valueOf(failTimePenalty));
                        } else {
                            game.broadcastMessage("action.puzzle_failed");
                        }
                    }
                }
            }
        }
    }

    @Override
    public String getObjectiveText() {
        return SinceDungeon.getPlugin().getLanguageManager().getString("objective.lever_puzzle");
    }
}