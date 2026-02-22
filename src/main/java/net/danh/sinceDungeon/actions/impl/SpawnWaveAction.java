package net.danh.sinceDungeon.actions.impl;

import net.danh.sinceDungeon.actions.DungeonAction;
import net.danh.sinceDungeon.manager.DungeonGame;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class SpawnWaveAction extends DungeonAction {
    private final EntityType type;
    private final int amount;
    private final List<Vector> locations;
    private final Set<UUID> mobIds = new HashSet<>();

    public SpawnWaveAction(EntityType type, int amount, List<Vector> locations) {
        this.type = type;
        this.amount = amount;
        this.locations = locations;
    }

    @Override
    public void start(DungeonGame game) {
        int count = 0;
        if (locations.isEmpty()) {
            this.completed = true;
            return;
        }

        for (int i = 0; i < amount; i++) {
            Vector vec = locations.get(i % locations.size());
            Location loc = new Location(game.getWorld(), vec.getX(), vec.getY(), vec.getZ());

            // Spawn mob
            Entity ent = game.getWorld().spawnEntity(loc.add(0.5, 0, 0.5), type); // +0.5 để mob đứng giữa block
            if (ent instanceof LivingEntity living) {
                living.setRemoveWhenFarAway(false); // Quan trọng: Không cho despawn
                living.setPersistent(true);        // Quan trọng: Lưu giữ entity

                mobIds.add(ent.getUniqueId());
                count++;
            } else {
                ent.remove(); // Nếu không phải LivingEntity (vd: Thuyền, Xe mỏ) thì xóa cho nhẹ
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
            if (mobIds.remove(e.getEntity().getUniqueId())) {
                if (mobIds.isEmpty()) {
                    this.completed = true;
                    game.sendMessage("action.kill_complete");
                } else {
                    game.sendMessage("action.kill_remain", "<amount>", String.valueOf(mobIds.size()));
                }
            }
        }
    }
}