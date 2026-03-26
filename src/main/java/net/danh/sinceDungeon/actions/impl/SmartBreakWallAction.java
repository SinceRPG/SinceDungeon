package net.danh.sinceDungeon.actions.impl;

import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.actions.DungeonAction;
import net.danh.sinceDungeon.actions.Tickable;
import net.danh.sinceDungeon.manager.DungeonGame;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.util.Vector;

public class SmartBreakWallAction extends DungeonAction implements Tickable {
    private final Vector trigger;
    private final Vector c1;
    private final Vector c2;
    private Location centerLoc;

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

    // UX: Bắn Particle liên tục để người chơi biết khối nào cần đập
    @Override
    public void onTick(DungeonGame game) {
        if (completed || centerLoc == null) return;

        // Tạo hiệu ứng hạt lửa lấp lánh thu hút sự chú ý
        game.getWorld().spawnParticle(Particle.FLAME, centerLoc, 3, 0.2, 0.2, 0.2, 0.01);
    }

    @Override
    public void onEvent(DungeonGame game, Event event) {
        if (event instanceof PlayerInteractEvent e) {
            if (e.getAction() != Action.LEFT_CLICK_BLOCK) return;
            if (!e.getPlayer().getUniqueId().equals(game.getPlayer().getUniqueId())) return;
            if (!e.hasBlock()) return;

            Block b = e.getClickedBlock();
            if (b != null && b.getWorld().equals(game.getWorld()) &&
                    b.getX() == trigger.getBlockX() &&
                    b.getY() == trigger.getBlockY() &&
                    b.getZ() == trigger.getBlockZ()) {

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

        // BẢO VỆ MÁY CHỦ: Nếu số lượng block lớn hơn 10.000, hủy lệnh để chống sập Server
        long volume = (long)(maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
        if (volume > 10000) {
            SinceDungeon.getPlugin().getLogger().severe("NGUY HIỂM: Tọa độ phá tường quá lớn (" + volume + " blocks). Đã tự động hủy để chống sập Server!");
            return;
        }

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    game.getWorld().getBlockAt(x, y, z).setType(Material.AIR);
                }
            }
        }

        Location center = new Location(game.getWorld(), (minX + maxX) / 2.0, (minY + maxY) / 2.0, (minZ + maxZ) / 2.0);
        game.getWorld().playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1f, 1f);
        game.getWorld().spawnParticle(Particle.EXPLOSION, center, 3);
    }
}