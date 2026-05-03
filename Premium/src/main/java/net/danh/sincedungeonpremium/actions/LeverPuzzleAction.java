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
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

public class LeverPuzzleAction extends DungeonAction {

    private final List<String> rawLevers;
    private final List<Location> parsedLevers = new ArrayList<>();
    private int currentIndex = 0;

    public LeverPuzzleAction(List<String> rawLevers) {
        this.rawLevers = rawLevers;
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

                        currentIndex++;
                        String soundSuccessStr = SinceDungeonPremium.getInstance().getFileManager().getConfig().getString("sounds.puzzle_success", "block.note_block.chime");
                        Sound soundSuccess = SoundUtils.getSound(soundSuccessStr);
                        if (soundSuccess != null) {
                            e.getPlayer().playSound(clickedLoc, soundSuccess, 1f, 2f);
                        }

                        if (currentIndex >= parsedLevers.size()) {
                            game.broadcastMessage("action.puzzle_solved");
                            this.forceComplete();
                        }
                    } else {
                        e.setCancelled(true);
                        currentIndex = 0;

                        String soundFailStr = SinceDungeonPremium.getInstance().getFileManager().getConfig().getString("sounds.puzzle_fail", "block.note_block.bass");
                        Sound soundFail = SoundUtils.getSound(soundFailStr);
                        if (soundFail != null) {
                            e.getPlayer().playSound(clickedLoc, soundFail, 1f, 0.5f);
                        }

                        game.broadcastMessage("action.puzzle_failed");
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