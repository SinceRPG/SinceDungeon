package net.danh.sinceDungeon.actions.impl;

import net.danh.sinceDungeon.actions.DungeonAction;
import net.danh.sinceDungeon.actions.Tickable;
import net.danh.sinceDungeon.manager.DungeonGame;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SpawnWaveAction extends DungeonAction implements Tickable {
    private final EntityType type;
    private final int amount;
    private final List<Vector> locations;

    // [ĐÃ SỬA]: Lưu trữ thêm Location để track Chunk, chống lỗi ClearLag
    private final Map<UUID, Location> spawnedMobs = new HashMap<>();

    public SpawnWaveAction(EntityType type, int amount, List<Vector> locations) {
        this.type = type;
        this.amount = amount;
        this.locations = locations;
    }

    @Override
    public void onTick(DungeonGame game) {
        if (completed) return;

        spawnedMobs.entrySet().removeIf(entry -> {
            UUID uuid = entry.getKey();
            Location spawnLoc = entry.getValue();
            Entity ent = Bukkit.getEntity(uuid);

            if (ent != null) {
                return ent.isDead();
            } else {
                return spawnLoc.getWorld() != null && spawnLoc.getWorld().isChunkLoaded(spawnLoc.getBlockX() >> 4, spawnLoc.getBlockZ() >> 4);
            }
        });

        if (spawnedMobs.isEmpty()) {
            this.completed = true;
            game.sendMessage("action.kill_complete");
        }
    }

    @Override
    public void start(DungeonGame game) {
        int count = 0;
        if (locations.isEmpty()) {
            this.completed = true;
            return;
        }

        for (int i = 0; i < amount; i++) {
            Location finalLoc = MythicMobWaveAction.convertVector(game, i, locations);

            Entity ent = game.getWorld().spawnEntity(finalLoc, type);
            if (ent instanceof LivingEntity living) {
                living.setRemoveWhenFarAway(false);
                living.setPersistent(true);

                spawnedMobs.put(ent.getUniqueId(), finalLoc);
                count++;
            } else {
                ent.remove();
            }
        }

        if (count == 0) {
            this.completed = true;
        } else {
            game.sendMessage("action.spawn_wave", "<amount>", String.valueOf(count), "<mob>", type.name());
        }
    }

    @Override
    public void onEvent(DungeonGame game, Event event) {
        if (event instanceof EntityDeathEvent e) {
            if (spawnedMobs.remove(e.getEntity().getUniqueId()) != null) {
                if (spawnedMobs.isEmpty()) {
                    this.completed = true;
                    game.sendMessage("action.kill_complete");
                } else {
                    game.sendMessage("action.kill_remain", "<amount>", String.valueOf(spawnedMobs.size()));
                }
            }
        }
    }
}