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

    private void debug(String message) {
        if (SinceDungeon.getPlugin().getConfigFile().getBoolean("debug", false)) {
            SinceDungeon.getPlugin().getLogger().info("[Debug-SpawnWave] " + message);
        }
    }

    @Override
    public String getObjectiveText() {
        String base = SinceDungeon.getPlugin().getMessagesFile().getString("objective.spawn_wave", "<yellow>Eliminate <red><mob> <gray>(Remaining: <remain>)");
        return base.replace("<mob>", type.name()).replace("<remain>", String.valueOf(spawnedMobs.size()));
    }

    @Override
    public void cleanup(DungeonGame game) {
        debug("Cleaning up SpawnWaveAction, unlocking chunks.");
        unlockChunks();
    }

    @Override
    public void onTick(DungeonGame game) {
        if (completed) return;

        Set<Chunk> currentChunks = new HashSet<>();
        List<Location> mobsToRespawn = new ArrayList<>();
        spawnedMobs.entrySet().removeIf(entry -> {
            UUID uuid = entry.getKey();
            Entity ent = Bukkit.getEntity(uuid);

            if (ent != null) {
                if (ent.isDead()) {
                    debug("Mob " + uuid + " is dead during tick check. Removing from tracker.");
                    return true;
                }
                Chunk c = ent.getLocation().getChunk();
                currentChunks.add(c);
                entry.setValue(ent.getLocation());
                return false;
            } else {
                Location lastLoc = entry.getValue();
                if (lastLoc.getWorld().isChunkLoaded(lastLoc.getBlockX() >> 4, lastLoc.getBlockZ() >> 4)) {
                    debug("Mob " + uuid + " is missing in a loaded chunk. Assuming despawned. Marking for respawn.");
                    mobsToRespawn.add(lastLoc);
                    return true;
                } else {
                    debug("Chunk unloaded for mob " + uuid + ". Forcing chunk load.");
                    lastLoc.getChunk().load();
                    currentChunks.add(lastLoc.getChunk());
                    return false;
                }
            }
        });

        for (Location loc : mobsToRespawn) {
            try {
                Entity ent = game.getWorld().spawnEntity(loc, type);
                if (ent instanceof org.bukkit.entity.LivingEntity living) {
                    living.setRemoveWhenFarAway(false);
                    living.setPersistent(true);
                    spawnedMobs.put(ent.getUniqueId(), loc);
                    debug("Respawned missing Vanilla mob with new UUID: " + ent.getUniqueId());
                } else if (ent != null) {
                    ent.remove();
                }
            } catch (Exception e) {
                debug("EXCEPTION caught while respawning entity: " + e.getMessage());
            }
        }

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
            debug("All mobs eliminated. Completing wave.");
            unlockChunks();
            this.completed = true;
            game.sendActionMessage(this, "complete", "action.kill_complete", "<mob>", type.name());
        }
    }

    private Location findSafeSpawn(Location original) {
        Location check = original.clone();
        for (int i = 0; i < 10; i++) {
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
        debug("Starting SpawnWaveAction for " + type.name() + " | Amount per location: " + amount);
        int count = 0;

        if (locations.isEmpty()) {
            debug("Locations list is empty! Completing immediately.");
            this.completed = true;
            return;
        }

        for (Vector vec : locations) {
            Location loc = new Location(game.getWorld(), vec.getX(), vec.getY(), vec.getZ());
            debug("Processing base location: " + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ());

            for (int i = 0; i < amount; i++) {
                double offsetX = (Math.random() - 0.5) * 1.5;
                double offsetZ = (Math.random() - 0.5) * 1.5;
                Location finalLoc = findSafeSpawn(loc.clone().add(0.5 + offsetX, 0, 0.5 + offsetZ));

                debug("Attempting to spawn at calculated safe location: " + finalLoc.getBlockX() + ", " + finalLoc.getBlockY() + ", " + finalLoc.getBlockZ());
                boolean chunkLoaded = finalLoc.getChunk().load(true);
                debug("Chunk loaded status: " + chunkLoaded);

                Entity ent = null;
                try {
                    ent = game.getWorld().spawnEntity(finalLoc, type);
                } catch (Exception e) {
                    debug("EXCEPTION caught while spawning entity: " + e.getMessage());
                    e.printStackTrace();
                }

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
                    debug("Successfully spawned LivingEntity with UUID: " + ent.getUniqueId());
                } else if (ent != null) {
                    debug("Spawned entity is not a LivingEntity. Removing it.");
                    ent.remove();
                } else {
                    debug("Entity returned null after spawn attempt.");
                }
            }
        }

        if (count == 0) {
            debug("Failed to spawn any mobs. Auto-completing stage to prevent softlock.");
            this.completed = true;
        } else {
            debug("Successfully spawned a total of " + count + " mobs.");
            game.sendActionMessage(this, "init", "action.spawn_wave", "<amount>", String.valueOf(count), "<mob>", type.name());
        }
    }

    @Override
    public void onEvent(DungeonGame game, Event event) {
        if (event instanceof EntityDeathEvent e) {
            if (spawnedMobs.remove(e.getEntity().getUniqueId()) != null) {
                debug("EntityDeathEvent registered for tracked mob: " + e.getEntity().getUniqueId() + ". Remaining: " + spawnedMobs.size());
                if (spawnedMobs.isEmpty()) {
                    unlockChunks();
                    this.completed = true;
                    game.sendActionMessage(this, "complete", "action.kill_complete", "<mob>", type.name());
                } else {
                    game.sendActionMessage(this, "progress", "action.kill_remain", "<amount>", String.valueOf(spawnedMobs.size()));
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