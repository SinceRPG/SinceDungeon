package net.danh.sinceDungeon.actions.impl;

import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.actions.DungeonAction;
import net.danh.sinceDungeon.actions.Tickable;
import net.danh.sinceDungeon.manager.DungeonGame;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

public class SmartBreakWallAction extends DungeonAction implements Tickable {
    private final Vector trigger;
    private final Vector c1;
    private final Vector c2;
    private Location centerLoc;

    // VÁ LỖI TRÀN RAM (Orphaned Task Leak)
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

        // VÁ LỖI CẤU TRÚC: Cho phép block bị phá bởi Vụ nổ Creeper/TNT tự động kích hoạt
        if (!isBreaking && centerLoc.getBlock().getType() == Material.AIR) {
            isBreaking = true;
            removeWall(game);
            this.completed = true;
            game.sendMessage("action.wall_break");
        }
    }

    @Override
    public void onEvent(DungeonGame game, Event event) {
        if (event instanceof PlayerInteractEvent e) {
            if (e.getAction() != Action.LEFT_CLICK_BLOCK) return;

            Player p = e.getPlayer();
            // VÁ LỖI BÓNG MA QUAN SÁT (Spectator Ghosting Exploit)
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
                game.sendMessage("action.wall_break");
            }
        }
    }

    private void removeWall(DungeonGame game) {
        int minX = Math.min(c1.getBlockX(), c2.getBlockX());
        int maxX = Math.max(c1.getBlockX(), c2.getBlockX());
        int minY = Math.min(c1.getBlockY(), c2.getBlockY());
        int maxY = Math.max(c1.getBlockY(), c2.getBlockY());
        int minZ = Math.min(c1.getBlockZ(), c2.getBlockZ());
        int maxZ = Math.max(c1.getBlockZ(), c2.getBlockZ());

        long volume = (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
        if (volume > 200000) {
            String msg = SinceDungeon.getPlugin().getMessagesFile().getString("admin.warning.wall_too_large", "Wall volume too large (<volume> blocks). Cancelled to prevent crash!");
            SinceDungeon.getPlugin().getLogger().severe(msg.replace("<volume>", String.valueOf(volume)));
            return;
        }

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
                while (blocksProcessed < 2500) {
                    game.getWorld().getBlockAt(currentX, currentY, currentZ).setType(Material.AIR, false);
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
                                game.getWorld().playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1f, 1f);
                                game.getWorld().spawnParticle(Particle.EXPLOSION, center, 3);
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