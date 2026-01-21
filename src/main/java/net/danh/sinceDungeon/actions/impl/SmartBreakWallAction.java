package net.danh.sinceDungeon.actions.impl;

import net.danh.sinceDungeon.actions.DungeonAction;
import net.danh.sinceDungeon.manager.DungeonGame;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.Event;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.util.Vector;

public class SmartBreakWallAction extends DungeonAction {
    private final Vector trigger;
    private final Vector c1;
    private final Vector c2;

    public SmartBreakWallAction(Vector trigger, Vector c1, Vector c2) {
        this.trigger = trigger;
        this.c1 = c1;
        this.c2 = c2;
    }

    @Override
    public void start(DungeonGame game) {
        // Passive action
    }

    @Override
    public void onEvent(DungeonGame game, Event event) {
        if (event instanceof BlockBreakEvent e) {
            Block b = e.getBlock();
            if (b.getX() == trigger.getBlockX() && b.getY() == trigger.getBlockY() && b.getZ() == trigger.getBlockZ()) {
                e.setCancelled(true);
                b.setType(Material.AIR);
                removeWall(game);
                this.completed = true;
                game.sendMessage("action.wall_break");
            }
        }
    }

    private void removeWall(DungeonGame game) {
        int minX = Math.min(c1.getBlockX(), c2.getBlockX()), maxX = Math.max(c1.getBlockX(), c2.getBlockX());
        int minY = Math.min(c1.getBlockY(), c2.getBlockY()), maxY = Math.max(c1.getBlockY(), c2.getBlockY());
        int minZ = Math.min(c1.getBlockZ(), c2.getBlockZ()), maxZ = Math.max(c1.getBlockZ(), c2.getBlockZ());

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    game.getWorld().getBlockAt(x, y, z).setType(Material.AIR);
                }
            }
        }

        Location center = new Location(game.getWorld(), (minX + maxX) / 2.0, (minY + maxY) / 2.0, (minZ + maxZ) / 2.0);
        game.getWorld().createExplosion(center, 2F, false);
    }
}