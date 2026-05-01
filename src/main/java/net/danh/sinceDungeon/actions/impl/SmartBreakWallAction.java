package net.danh.sinceDungeon.actions.impl;

import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.actions.DungeonAction;
import net.danh.sinceDungeon.actions.Tickable;
import net.danh.sinceDungeon.models.DungeonGame;
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
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
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

    private BukkitTask breakTask = null;
    private boolean isBreaking = false;

    public SmartBreakWallAction(Vector trigger, Vector c1, Vector c2) {
        this.trigger = trigger;
        this.c1 = c1;
        this.c2 = c2;
    }

    @Override
    public String getObjectiveText() {
        return SinceDungeon.getPlugin().getMessagesFile().getString("objective.break_wall", "<aqua>Find and break the sealing block");
    }

    @Override
    public void start(DungeonGame game) {
        this.centerLoc = new Location(game.getWorld(), trigger.getBlockX() + 0.5, trigger.getBlockY() + 0.5, trigger.getBlockZ() + 0.5);
    }

    @Override
    public void cleanup(DungeonGame game) {
        if (breakTask != null && !breakTask.isCancelled()) {
            breakTask.cancel();
        }
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
            if (b != null && b.getWorld().equals(game.getWorld()) &&
                    b.getX() == trigger.getBlockX() &&
                    b.getY() == trigger.getBlockY() &&
                    b.getZ() == trigger.getBlockZ()) {

                isBreaking = true;
                b.setType(Material.AIR);
                removeWall(game);

                this.completed = true;
                game.sendActionMessage(this, "complete", "action.wall_break");
            }
        }
    }

    /**
     * Executes the asynchronous block removal process to simulate a wall crumbling.
     * Modifies blocks in chunks of 50 per tick to prevent main-thread lag spikes.
     *
     * @param game The active dungeon instance handling the task.
     */
    private void removeWall(DungeonGame game) {
        int minX = Math.min(c1.getBlockX(), c2.getBlockX());
        int maxX = Math.max(c1.getBlockX(), c2.getBlockX());
        int minY = Math.min(c1.getBlockY(), c2.getBlockY());
        int maxY = Math.max(c1.getBlockY(), c2.getBlockY());
        int minZ = Math.min(c1.getBlockZ(), c2.getBlockZ());
        int maxZ = Math.max(c1.getBlockZ(), c2.getBlockZ());

        long volume = (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);

        if (volume > 50000) {
            String msg = SinceDungeon.getPlugin().getMessagesFile().getString("admin.warning.wall_too_large", "Wall volume too large (<volume> blocks). Cancelled to prevent crash!");
            SinceDungeon.getPlugin().getLogger().severe(msg.replace("<volume>", String.valueOf(volume)));
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

        breakTask = new BukkitRunnable() {
            int currentX = minX;
            int currentY = minY;
            int currentZ = minZ;

            @Override
            public void run() {
                if (game.getWorld() == null || !game.isRunning()) {
                    cancel();
                    return;
                }

                int blocksProcessed = 0;
                while (blocksProcessed < 50) {
                    Block block = game.getWorld().getBlockAt(currentX, currentY, currentZ);
                    if (block.getType() != Material.AIR) {
                        try {
                            if (finalCrumble.getDataType() == BlockData.class) {
                                game.getWorld().spawnParticle(finalCrumble, block.getLocation().add(0.5, 0.5, 0.5), 5, 0.2, 0.2, 0.2, 0.05, block.getBlockData());
                            } else {
                                game.getWorld().spawnParticle(finalCrumble, block.getLocation().add(0.5, 0.5, 0.5), 5, 0.2, 0.2, 0.2, 0.05);
                            }
                        } catch (Exception ignored) {
                        }
                        block.setType(Material.AIR, false);
                    }
                    blocksProcessed++;

                    currentX++;
                    if (currentX > maxX) {
                        currentX = minX;
                        currentY++;
                        if (currentY > maxY) {
                            currentY = minY;
                            currentZ++;
                            if (currentZ > maxZ) {
                                Location center = new Location(game.getWorld(), (minX + maxX) / 2.0, (minY + maxY) / 2.0, (minZ + maxZ) / 2.0);

                                String soundExplode = SinceDungeon.getPlugin().getConfigFile().getString("sounds.wall_break", "entity.generic.explode");
                                game.getWorld().playSound(center, SoundUtils.getSound(soundExplode), 1f, 1f);

                                String explosionName = SinceDungeon.getPlugin().getConfigFile().getString("particles.wall_break", "EXPLOSION");
                                try {
                                    game.getWorld().spawnParticle(Particle.valueOf(explosionName.toUpperCase()), center, 3);
                                } catch (Exception ignored) {
                                }

                                cancel();
                                return;
                            }
                        }
                    }
                }
            }
        }.runTaskTimer(SinceDungeon.getPlugin(), 0L, 1L);
    }
}