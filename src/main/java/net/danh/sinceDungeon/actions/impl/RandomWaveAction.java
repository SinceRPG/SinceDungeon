package net.danh.sinceDungeon.actions.impl;

import io.lumine.mythic.bukkit.BukkitAdapter;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.mobs.ActiveMob;
import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.actions.DungeonAction;
import net.danh.sinceDungeon.actions.Tickable;
import net.danh.sinceDungeon.models.DungeonGame;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.util.Vector;

import java.util.*;

/**
 * A wave action that randomly selects mobs from a configured pool.
 * Supports both Vanilla (Bukkit EntityType) and MythicMobs.
 * <p>
 * Format for each entry in the random_mobs list:
 * VANILLA:<EntityType>:<weight>
 * MYTHIC:<MobId>:<weight>:<level>   (level is optional, defaults to 1)
 * <p>
 * Example:
 * - VANILLA:ZOMBIE:50
 * - VANILLA:SKELETON:30
 * - MYTHIC:SkeletonKing:20:5
 */
public class RandomWaveAction extends DungeonAction implements Tickable {

    private final int amount;
    private final List<Vector> locations;
    private final List<MobOption> mobPool;
    private final boolean scaleWithParty;
    private final Map<UUID, Location> spawnedMobs = new HashMap<>();
    private final Set<Chunk> lockedChunks = new HashSet<>();
    private final Map<UUID, String> mobDisplayNames = new HashMap<>();

    public RandomWaveAction(int amount, List<Vector> locations, List<MobOption> mobPool, boolean scaleWithParty) {
        this.amount = amount;
        this.locations = locations;
        this.mobPool = mobPool;
        this.scaleWithParty = scaleWithParty;
    }

    /**
     * Parses a list of raw mob pool strings into MobOption records.
     * Format: VANILLA:<id>:<weight>  or  MYTHIC:<id>:<weight>[:<level>]
     */
    public static List<MobOption> parseMobPool(List<String> raw) {
        List<MobOption> pool = new ArrayList<>();
        for (String entry : raw) {
            try {
                String[] parts = entry.replace(" ", "").split(":");
                if (parts.length < 3) continue;
                boolean isMythic = parts[0].equalsIgnoreCase("MYTHIC");
                String id = parts[1];
                double weight = Double.parseDouble(parts[2]);
                int level = parts.length >= 4 ? Integer.parseInt(parts[3]) : 1;
                if (weight > 0) pool.add(new MobOption(isMythic, id, level, weight));
            } catch (Exception ignored) {
                SinceDungeon.getPlugin().getLogger().warning("[RandomWave] Failed to parse mob pool entry: " + entry
                        + " — expected format: VANILLA:<EntityType>:<weight> or MYTHIC:<MobId>:<weight>[:<level>]");
            }
        }
        return pool;
    }

    private void debug(String message) {
        if (SinceDungeon.getPlugin().getConfigFile().getBoolean("settings.debug", false)) {
            SinceDungeon.getPlugin().getLogger().info("[Debug-RandomWave] " + message);
        }
    }

    /**
     * Picks a MobOption from the pool using weighted random selection.
     */
    private MobOption pickRandom() {
        if (mobPool.isEmpty()) return null;
        double totalWeight = mobPool.stream().mapToDouble(MobOption::weight).sum();
        double roll = Math.random() * totalWeight;
        double cumulative = 0;
        for (MobOption opt : mobPool) {
            cumulative += opt.weight();
            if (roll <= cumulative) return opt;
        }
        return mobPool.get(mobPool.size() - 1);
    }

    /**
     * Enhanced safe spawn logic: Scans both upwards and downwards to ensure
     * mobs don't suffocate in ceilings or spawn on unreachable roofs.
     */
    private Location findSafeSpawn(Location original) {
        Location check = original.clone();

        // Check downwards first to snap to the floor
        for (int i = 0; i < 5; i++) {
            if (check.getBlock().getType().isSolid()) {
                check.add(0, 1, 0);
                break;
            }
            check.subtract(0, 1, 0);
        }

        // Check upwards for head clearance
        for (int i = 0; i < 5; i++) {
            Block block = check.getBlock();
            Block head = check.clone().add(0, 1, 0).getBlock();
            if (!block.getType().isSolid() && !head.getType().isSolid()) {
                return check;
            }
            check.add(0, 1, 0);
        }
        return original; // Fallback
    }

    /**
     * Spawns a mob at a given location based on the MobOption.
     * Returns the entity UUID if successful, else null.
     */
    private UUID spawnMob(DungeonGame game, Location loc, MobOption opt) {
        try {
            if (opt.isMythic()) {
                if (!Bukkit.getPluginManager().isPluginEnabled("MythicMobs")) {
                    debug("MythicMobs not installed. Cannot spawn MYTHIC mob.");
                    return null;
                }
                io.lumine.mythic.api.mobs.MythicMob mythicMob =
                        MythicBukkit.inst().getMobManager().getMythicMob(opt.id()).orElse(null);
                if (mythicMob == null) {
                    debug("MythicMob ID '" + opt.id() + "' not found.");
                    return null;
                }
                loc.getChunk().load(true);
                ActiveMob am = mythicMob.spawn(BukkitAdapter.adapt(loc), opt.level());
                if (am != null && am.getEntity() != null) {
                    Entity e = am.getEntity().getBukkitEntity();
                    if (e instanceof LivingEntity le) {
                        le.setRemoveWhenFarAway(false);
                        le.setPersistent(true);
                    }
                    return e.getUniqueId();
                }
            } else {
                EntityType type;
                try {
                    type = EntityType.valueOf(opt.id().toUpperCase());
                } catch (IllegalArgumentException ex) {
                    debug("Invalid Vanilla EntityType: " + opt.id());
                    return null;
                }
                loc.getChunk().load(true);
                Entity e = game.getWorld().spawnEntity(loc, type);
                if (e instanceof LivingEntity le) {
                    le.setRemoveWhenFarAway(false);
                    le.setPersistent(true);
                    game.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, loc.clone().add(0, 1, 0), 15, 0.3, 0.3, 0.3, 0.05);
                    game.getWorld().playSound(loc, Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 0.5f, 0.8f);
                    return e.getUniqueId();
                } else if (e != null) {
                    e.remove();
                }
            }
        } catch (Exception e) {
            debug("Exception during spawn: " + e.getMessage());
        }
        return null;
    }

    @Override
    public String getObjectiveText() {
        String base = SinceDungeon.getPlugin().getMessagesFile().getString(
                "objective.random_wave", "<red>Eliminate all enemies <gray>(Remaining: <remain>)");
        return base.replace("<remain>", String.valueOf(spawnedMobs.size()));
    }

    @Override
    public void start(DungeonGame game) {
        if (mobPool.isEmpty()) {
            debug("mob pool is empty — completing immediately.");
            this.completed = true;
            return;
        }
        if (locations.isEmpty()) {
            debug("locations list is empty — completing immediately.");
            this.completed = true;
            return;
        }

        int count = 0;

        // --- SCALING LOGIC ---
        int finalAmount = scaleWithParty ? this.amount * game.getParticipants().size() : this.amount;
        if (finalAmount <= 0) finalAmount = 1;

        for (Vector vec : locations) {
            for (int i = 0; i < finalAmount; i++) {
                MobOption opt = pickRandom();
                if (opt == null) continue;

                double offsetX = (Math.random() - 0.5) * 1.5;
                double offsetZ = (Math.random() - 0.5) * 1.5;
                Location spawnLoc = findSafeSpawn(
                        new Location(game.getWorld(), vec.getX() + 0.5 + offsetX, vec.getY(), vec.getZ() + 0.5 + offsetZ)
                );

                UUID uid = spawnMob(game, spawnLoc, opt);
                if (uid != null) {
                    spawnedMobs.put(uid, spawnLoc);
                    mobDisplayNames.put(uid, opt.id());

                    Chunk c = spawnLoc.getChunk();
                    c.addPluginChunkTicket(SinceDungeon.getPlugin());
                    lockedChunks.add(c);
                    count++;
                    debug("Spawned " + (opt.isMythic() ? "MYTHIC" : "VANILLA") + ":" + opt.id() + " at " + spawnLoc.toVector());
                }
            }
        }

        if (count == 0) {
            debug("Failed to spawn any mobs — auto-completing.");
            this.completed = true;
        } else {
            game.sendActionMessage(this, "init", "action.random_wave_start", "<amount>", String.valueOf(count));
        }
    }

    @Override
    public void onTick(DungeonGame game) {
        if (completed) return;

        Set<Chunk> currentChunks = new HashSet<>();
        List<Map.Entry<UUID, Location>> toRespawn = new ArrayList<>();

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
                    debug("Mob " + uuid + " vanished in loaded chunk — marking for respawn.");
                    toRespawn.add(Map.entry(uuid, lastLoc));
                    return true;
                } else {
                    lastLoc.getChunk().load();
                    currentChunks.add(lastLoc.getChunk());
                    return false;
                }
            }
        });

        for (Map.Entry<UUID, Location> entry : toRespawn) {
            MobOption opt = pickRandom();
            if (opt == null) continue;
            UUID newUid = spawnMob(game, entry.getValue(), opt);
            if (newUid != null) {
                spawnedMobs.put(newUid, entry.getValue());
                mobDisplayNames.put(newUid, opt.id());
                debug("Respawned missing mob with new selection: " + opt.id());
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
            debug("All random mobs eliminated — completing wave.");
            unlockChunks();
            this.completed = true;
            game.sendActionMessage(this, "complete", "action.random_wave_complete");
        }
    }

    @Override
    public void onEvent(DungeonGame game, Event event) {
        if (event instanceof EntityDeathEvent e) {
            UUID uid = e.getEntity().getUniqueId();
            if (spawnedMobs.remove(uid) != null) {
                mobDisplayNames.remove(uid);
                debug("Mob " + uid + " killed via event. Remaining: " + spawnedMobs.size());
                if (spawnedMobs.isEmpty()) {
                    unlockChunks();
                    this.completed = true;
                    game.sendActionMessage(this, "complete", "action.random_wave_complete");
                } else {
                    game.sendActionMessage(this, "progress", "action.random_wave_remain",
                            "<amount>", String.valueOf(spawnedMobs.size()));
                }
            }
        }
    }

    @Override
    public void cleanup(DungeonGame game) {
        /**
         * Cleans up the action by removing all spawned mobs and unlocking chunks.
         */
        debug("Cleaning up RandomWaveAction, removing entities and unlocking chunks.");
        for (UUID uuid : spawnedMobs.keySet()) {
            Entity ent = Bukkit.getEntity(uuid);
            if (ent != null && !ent.isDead()) {
                ent.remove();
            }
        }
        spawnedMobs.clear();
        unlockChunks();
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

    /**
     * Represents a single mob option in the random pool.
     */
    public record MobOption(boolean isMythic, String id, int level, double weight) {
    }
}