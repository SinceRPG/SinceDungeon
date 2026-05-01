package net.danh.sinceDungeon.actions.impl;

import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.actions.DungeonAction;
import net.danh.sinceDungeon.actions.Tickable;
import net.danh.sinceDungeon.hooks.MythicMobsHook;
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

    private Location findSafeSpawn(Location original) {
        Location check = original.clone();
        for (int i = 0; i < 5; i++) {
            if (check.getBlock().getType().isSolid()) {
                check.add(0, 1, 0);
                break;
            }
            check.subtract(0, 1, 0);
        }
        for (int i = 0; i < 5; i++) {
            Block block = check.getBlock();
            Block head = check.clone().add(0, 1, 0).getBlock();
            if (!block.getType().isSolid() && !head.getType().isSolid()) return check;
            check.add(0, 1, 0);
        }
        return original;
    }

    /**
     * Executes the internal spawning logic based on the provided MobOption configuration.
     * Dynamically routes the request to either the Vanilla Bukkit entity spawner or the MythicMobs API hook.
     * Applies visual particle effects and sound cues upon successful generation.
     *
     * @param game The active dungeon game instance.
     * @param loc  The pre-calculated safe location to spawn the entity.
     * @param opt  The selected mob configuration containing type, ID, and level data.
     * @return The UUID of the spawned entity, or null if the spawn process failed.
     */
    private UUID spawnMob(DungeonGame game, Location loc, MobOption opt) {
        try {
            String pName = SinceDungeon.getPlugin().getConfigFile().getString("particles.mob_spawn", "CAMPFIRE_COSY_SMOKE");
            Particle pType;
            try {
                pType = Particle.valueOf(pName.toUpperCase());
            } catch (Exception e) {
                pType = Particle.CAMPFIRE_COSY_SMOKE;
            }

            String sName = SinceDungeon.getPlugin().getConfigFile().getString("sounds.mob_spawn", "entity.zombie.break_wooden_door");
            Sound sType = net.danh.sinceDungeon.utils.SoundUtils.getSound(sName);

            if (opt.isMythic()) {
                if (!Bukkit.getPluginManager().isPluginEnabled("MythicMobs")) {
                    debug("MythicMobs not installed. Cannot spawn MYTHIC mob.");
                    return null;
                }
                if (!MythicMobsHook.isValidMythicMob(opt.id())) {
                    debug("MythicMob ID '" + opt.id() + "' not found.");
                    return null;
                }

                Entity e = MythicMobsHook.spawnMythicMob(loc, opt.id(), opt.level());
                if (e instanceof LivingEntity le) {
                    le.setRemoveWhenFarAway(false);
                    le.setPersistent(true);

                    game.getWorld().spawnParticle(pType, loc.clone().add(0, 1, 0), 15, 0.3, 0.3, 0.3, 0.05);
                    if (sType != null) game.getWorld().playSound(loc, sType, 0.5f, 0.8f);

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

                    game.getWorld().spawnParticle(pType, loc.clone().add(0, 1, 0), 15, 0.3, 0.3, 0.3, 0.05);
                    if (sType != null) game.getWorld().playSound(loc, sType, 0.5f, 0.8f);

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
        if (mobPool.isEmpty() || locations.isEmpty()) {
            this.completed = true;
            return;
        }

        int count = 0;
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
                }
            }
        }

        if (count == 0) {
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

    public record MobOption(boolean isMythic, String id, int level, double weight) {
    }
}