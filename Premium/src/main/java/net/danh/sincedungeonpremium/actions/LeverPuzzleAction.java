package net.danh.sincedungeonpremium.actions;

import net.danh.sinceDungeon.actions.DungeonAction;
import net.danh.sinceDungeon.managers.DungeonLoader;
import net.danh.sinceDungeon.models.DungeonGame;
import net.danh.sincedungeonpremium.SinceDungeonPremium;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * Premium-Exclusive Action: Lever Puzzle
 * Responsibilities:
 * - Requires players to flip levers in a precise, configured sequence.
 * - Resets progress and plays error feedback if a wrong lever is flipped.
 */
public class LeverPuzzleAction extends DungeonAction {

    private final List<String> rawLevers;
    private final String objectiveText;
    private final List<Location> parsedLevers = new ArrayList<>();
    private int currentIndex = 0;

    public LeverPuzzleAction(List<String> rawLevers, String objectiveText) {
        this.rawLevers = rawLevers;
        this.objectiveText = objectiveText;
    }

    @Override
    public void start(DungeonGame game) {
        if (game.getWorld() == null) {
            this.forceComplete();
            return;
        }

        for (String s : rawLevers) {
            Vector vec = DungeonLoader.parseVector(s);
            parsedLevers.add(new Location(game.getWorld(), vec.getBlockX(), vec.getBlockY(), vec.getBlockZ()));
        }
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

                        // Correct lever pulled
                        currentIndex++;
                        e.getPlayer().playSound(clickedLoc, Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 2f);

                        if (currentIndex >= parsedLevers.size()) {
                            SinceDungeonPremium.getInstance().getFileManager().sendMessage(e.getPlayer(), "puzzle.solved");
                            this.forceComplete();
                        }
                    } else {
                        // Wrong lever pulled, reset progress
                        e.setCancelled(true);
                        currentIndex = 0;
                        e.getPlayer().playSound(clickedLoc, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f);
                        SinceDungeonPremium.getInstance().getFileManager().sendMessage(e.getPlayer(), "puzzle.failed");
                    }
                }
            }
        }
    }

    @Override
    public String getObjectiveText() {
        return objectiveText;
    }
}