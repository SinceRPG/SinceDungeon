package net.danh.sinceDungeon.actions.impl;

import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.actions.DungeonAction;
import net.danh.sinceDungeon.actions.Tickable;
import net.danh.sinceDungeon.manager.DungeonGame;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class SpawnWaveAction extends DungeonAction implements Tickable {
    private final EntityType type;
    private final int amount;
    private final List<Vector> locations;
    private final Map<UUID, Location> spawnedMobs = new HashMap<>();
    private final Set<Chunk> lockedChunks = new HashSet<>();

    public SpawnWaveAction(EntityType type, int amount, List<Vector> locations) {
        this.type = type;
        this.amount = amount;
        this.locations = locations;
    }

    @Override
    public String getObjectiveText() {
        String base = SinceDungeon.getPlugin().getMessagesFile().getString("objective.spawn_wave", "<yellow>Eliminate <red><mob> <gray>(Remaining: <remain>)");
        return base.replace("<mob>", type.name()).replace("<remain>", String.valueOf(spawnedMobs.size()));
    }

    @Override
    public void cleanup(DungeonGame game) {
        unlockChunks();
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
                return true;
            }
        });

        if (spawnedMobs.isEmpty()) {
            unlockChunks();
            this.completed = true;
            game.sendMessage("action.kill_complete");
        }
    }

    // VÁ LỖI KẸT TƯỜNG (Suffocation Soft-lock)
    private Location findSafeSpawn(Location original) {
        Location check = original.clone();
        for (int i = 0; i < 3; i++) {
            Block block = check.getBlock();
            Block head = check.clone().add(0, 1, 0).getBlock();
            // Nếu vị trí dưới chân và trên đầu không đặc (Không phải Solid Block) thì duyệt
            if (!block.getType().isSolid() && !head.getType().isSolid()) {
                return check;
            }
            check.add(0, 1, 0); // Đẩy lên 1 block để tìm không gian thở
        }
        return original; // Nếu xui quá kẹt cứng 3 block thì đành cho xuất hiện ở chỗ cũ
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

            double offsetX = (Math.random() - 0.5) * 1.5;
            double offsetZ = (Math.random() - 0.5) * 1.5;
            Location finalLoc = findSafeSpawn(loc.add(0.5 + offsetX, 0, 0.5 + offsetZ));

            Entity ent = game.getWorld().spawnEntity(finalLoc, type);
            if (ent instanceof LivingEntity living) {
                living.setRemoveWhenFarAway(false);
                living.setPersistent(true);

                Chunk c = finalLoc.getChunk();
                c.addPluginChunkTicket(SinceDungeon.getPlugin());
                lockedChunks.add(c);

                game.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, finalLoc.clone().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0.05);
                game.getWorld().playSound(finalLoc, Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 0.5f, 0.5f);

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
                    unlockChunks();
                    this.completed = true;
                    game.sendMessage("action.kill_complete");
                } else {
                    game.sendMessage("action.kill_remain", "<amount>", String.valueOf(spawnedMobs.size()));
                }
            }
        }
    }

    private void unlockChunks() {
        for (Chunk c : lockedChunks) {
            try {
                c.removePluginChunkTicket(SinceDungeon.getPlugin());
            } catch (Exception ignored) {}
        }
        lockedChunks.clear();
    }
}