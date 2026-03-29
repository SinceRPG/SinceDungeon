package net.danh.sinceDungeon.actions.impl;

import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.actions.DungeonAction;
import net.danh.sinceDungeon.actions.Tickable;
import net.danh.sinceDungeon.manager.DungeonGame;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.util.Vector;

import java.util.*;

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

        Set<Chunk> currentChunks = new HashSet<>();

        spawnedMobs.entrySet().removeIf(entry -> {
            UUID uuid = entry.getKey();
            Entity ent = Bukkit.getEntity(uuid);

            if (ent != null) {
                if (ent.isDead()) return true;
                Chunk c = ent.getLocation().getChunk();
                currentChunks.add(c);
                entry.setValue(ent.getLocation());
                return false;
            } else {
                Location lastLoc = entry.getValue();
                if (lastLoc.getWorld().isChunkLoaded(lastLoc.getBlockX() >> 4, lastLoc.getBlockZ() >> 4)) {
                    return true;
                } else {
                    lastLoc.getChunk().load();
                    currentChunks.add(lastLoc.getChunk());
                    return false;
                }
            }
        });

        for (Chunk c : currentChunks) {
            if (!lockedChunks.contains(c)) {
                c.addPluginChunkTicket(SinceDungeon.getPlugin());
                lockedChunks.add(c);
            }
        }

        lockedChunks.removeIf(c -> {
            if (!currentChunks.contains(c)) {
                c.removePluginChunkTicket(SinceDungeon.getPlugin());
                return true;
            }
            return false;
        });

        if (spawnedMobs.isEmpty()) {
            unlockChunks();
            this.completed = true;
            game.sendMessage("action.kill_complete");
        }
    }

    private Location findSafeSpawn(Location original) {
        Location check = original.clone();
        for (int i = 0; i < 3; i++) {
            Block block = check.getBlock();
            Block head = check.clone().add(0, 1, 0).getBlock();
            if (!block.getType().isSolid() && !head.getType().isSolid()) {
                return check;
            }
            check.add(0, 1, 0);
        }
        return original;
    }

    @Override
    public void start(DungeonGame game) {
        int count = 0;
        if (locations.isEmpty()) {
            this.completed = true;
            return;
        }

        for (Vector vec : locations) {
            Location loc = new Location(game.getWorld(), vec.getX(), vec.getY(), vec.getZ());
            for (int i = 0; i < amount; i++) {
                double offsetX = (Math.random() - 0.5) * 1.5;
                double offsetZ = (Math.random() - 0.5) * 1.5;
                Location finalLoc = findSafeSpawn(loc.clone().add(0.5 + offsetX, 0, 0.5 + offsetZ));

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
            } catch (Exception ignored) {
            }
        }
        lockedChunks.clear();
    }
}