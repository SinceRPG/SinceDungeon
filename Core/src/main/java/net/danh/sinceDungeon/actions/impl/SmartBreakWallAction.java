package net.danh.sinceDungeon.actions.impl;

import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.actions.DungeonAction;
import net.danh.sinceDungeon.actions.Tickable;
import net.danh.sinceDungeon.models.DungeonGame;
import net.danh.sinceDungeon.utils.SchedulerCompat;
import net.danh.sinceDungeon.utils.SoundUtils;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.util.Vector;

/**
 * Objective that requires players to locate and physically attack a trigger block.
 * Upon success, it visually dismantles an entire boundary to unlock a new path.
 */
public class SmartBreakWallAction extends DungeonAction implements Tickable {
    private final Vector trigger;
    private final Vector c1;
    private final Vector c2;
    private Location centerLoc;
    private Location triggerBlockLoc;

    private SchedulerCompat.TaskHandle breakTask = null;
    private boolean isBreaking = false;

    public SmartBreakWallAction(Vector trigger, Vector c1, Vector c2) {
        this.trigger = trigger;
        this.c1 = c1;
        this.c2 = c2;
    }

    @Override
    public String getObjectiveText() {
        return SinceDungeon.getPlugin().getLanguageManager().getString("objective.break_wall", "<aqua>Find and break the sealing block");
    }

    @Override
    public void start(DungeonGame game) {
        this.triggerBlockLoc = game.resolveBlockLocation(trigger);
        this.centerLoc = triggerBlockLoc.clone().add(0.5, 0.5, 0.5);
    }

    @Override
    public void cleanup(DungeonGame game) {
        super.cleanup(game); // Note: Added call to clear tracked collections cleanly
        if (breakTask != null && !breakTask.isCancelled()) {
            breakTask.cancel();
        }
        breakTask = null;
        centerLoc = null;
        triggerBlockLoc = null;
    }

    @Override
    public void onTick(DungeonGame game) {
        if (completed || centerLoc == null) return;
        game.getWorld().spawnParticle(Particle.FLAME, centerLoc, 3, 0.2, 0.2, 0.2, 0.01);

        if (!isBreaking && centerLoc.getBlock().getType() == Material.AIR) {
            isBreaking = true;
            removeWall(game);
            this.completed = true;
            game.sendActionMessage(this, "complete", "action.wall_break");
        }
    }

    @Override
    public void onEvent(DungeonGame game, Event event) {
        if (event instanceof PlayerInteractEvent e) {
            if (e.getAction() != Action.LEFT_CLICK_BLOCK) return;

            Player p = e.getPlayer();
            if (p.getGameMode() == GameMode.SPECTATOR) return;

            if (!game.getParticipants().contains(p)) return;
            if (!e.hasBlock() || isBreaking) return;

            Block b = e.getClickedBlock();
            if (b != null && triggerBlockLoc != null && b.getWorld().equals(game.getWorld()) &&
                    b.getX() == triggerBlockLoc.getBlockX() &&
                    b.getY() == triggerBlockLoc.getBlockY() &&
                    b.getZ() == triggerBlockLoc.getBlockZ()) {

                isBreaking = true;
                b.setType(Material.AIR);
                removeWall(game);

                this.completed = true;
                game.sendActionMessage(this, "complete", "action.wall_break");
            }
        }
    }

    private void removeWall(DungeonGame game) {
        Location cornerA = game.resolveBlockLocation(c1);
        Location cornerB = game.resolveBlockLocation(c2);
        int minX = Math.min(cornerA.getBlockX(), cornerB.getBlockX());
        int maxX = Math.max(cornerA.getBlockX(), cornerB.getBlockX());
        int minY = Math.min(cornerA.getBlockY(), cornerB.getBlockY());
        int maxY = Math.max(cornerA.getBlockY(), cornerB.getBlockY());
        int minZ = Math.min(cornerA.getBlockZ(), cornerB.getBlockZ());
        int maxZ = Math.max(cornerA.getBlockZ(), cornerB.getBlockZ());

        long volume = (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);

        if (volume > 50000) {
            String msg = SinceDungeon.getPlugin().getLanguageManager().getString("admin.warning.wall_too_large", "Wall volume too large (<volume> blocks). Cancelled to prevent crash!");
            SinceDungeon.getPlugin().getLogger().severe(msg.replace("<volume>", String.valueOf(volume)));
            this.completed = true; // Permanently break out of the tick-loop spam condition!
            return;
        }

        String crumbleName = SinceDungeon.getPlugin().getConfigFile().getString("particles.wall_crumble", "BLOCK_CRUMBLE");
        Particle crumbleParticle;
        try {
            crumbleParticle = Particle.valueOf(crumbleName.toUpperCase());
        } catch (Exception e) {
            crumbleParticle = Particle.BLOCK_CRUMBLE;
        }

        final Particle finalCrumble = crumbleParticle;

        final Location particleLoc = new Location(game.getWorld(), 0, 0, 0);
        final int[] currentX = {minX};
        final int[] currentY = {minY};
        final int[] currentZ = {minZ};

        breakTask = SchedulerCompat.runAtLocationTimer(SinceDungeon.getPlugin(), centerLoc, () -> {
            if (game.getWorld() == null || !game.isRunning()) {
                if (breakTask != null) breakTask.cancel();
                return;
            }

            int blocksProcessed = 0;
            while (blocksProcessed < 50) {
                Block block = game.getWorld().getBlockAt(currentX[0], currentY[0], currentZ[0]);
                if (block.getType() != Material.AIR) {
                    try {
                        particleLoc.set(currentX[0] + 0.5, currentY[0] + 0.5, currentZ[0] + 0.5);
                        if (finalCrumble.getDataType() == BlockData.class) {
                            game.getWorld().spawnParticle(finalCrumble, particleLoc, 5, 0.2, 0.2, 0.2, 0.05, block.getBlockData());
                        } else {
                            game.getWorld().spawnParticle(finalCrumble, particleLoc, 5, 0.2, 0.2, 0.2, 0.05);
                        }
                    } catch (Exception ignored) {
                    }
                    block.setType(Material.AIR, false);
                }
                blocksProcessed++;

                currentX[0]++;
                if (currentX[0] > maxX) {
                    currentX[0] = minX;
                    currentY[0]++;
                    if (currentY[0] > maxY) {
                        currentY[0] = minY;
                        currentZ[0]++;
                        if (currentZ[0] > maxZ) {
                            Location center = new Location(game.getWorld(), (minX + maxX) / 2.0, (minY + maxY) / 2.0, (minZ + maxZ) / 2.0);

                            String soundExplode = SinceDungeon.getPlugin().getConfigFile().getString("sounds.wall_break", "entity.generic.explode");
                            game.getWorld().playSound(center, SoundUtils.getSound(soundExplode), 1f, 1f);

                            String explosionName = SinceDungeon.getPlugin().getConfigFile().getString("particles.wall_break", "EXPLOSION");
                            try {
                                game.getWorld().spawnParticle(Particle.valueOf(explosionName.toUpperCase()), center, 3);
                            } catch (Exception ignored) {
                            }

                            if (breakTask != null) breakTask.cancel();
                            return;
                        }
                    }
                }
            }
        }, 0L, 1L);
    }
}
