package net.danh.sinceDungeon.actions.impl;

import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.actions.DungeonAction;
import net.danh.sinceDungeon.actions.Tickable;
import net.danh.sinceDungeon.hooks.MythicMobsHook;
import net.danh.sinceDungeon.models.DungeonGame;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public class MythicMobWaveAction extends DungeonAction implements Tickable {
    private final String internalName;
    private final int amount;
    private final int level;
    private final List<Vector> locations;
    private final boolean scaleWithParty;
    private final Map<UUID, Location> spawnedMobs = new HashMap<>();
    private final Set<Chunk> lockedChunks = new HashSet<>();

    public MythicMobWaveAction(String internalName, int amount, int level, List<Vector> locations, boolean scaleWithParty) {
        this.internalName = internalName;
        this.amount = amount;
        this.level = Math.max(1, level);
        this.locations = locations;
        this.scaleWithParty = scaleWithParty;
    }

    private void debug(String message) {
        if (SinceDungeon.getPlugin().getConfigFile().getBoolean("debug", false)) {
            SinceDungeon.getPlugin().getLogger().info("[Debug-MythicWave] " + message);
        }
    }

    @Override
    public String getObjectiveText() {
        String base = SinceDungeon.getPlugin().getLanguageManager().getString("objective.mythic_wave", "<dark_red>Defeat Boss <red><mob> <gray>(Remaining: <remain>)");
        return base.replace("<mob>", internalName).replace("<remain>", String.valueOf(spawnedMobs.size()));
    }

    @Override
    public void cleanup(DungeonGame game) {
        super.cleanup(game);
        unlockChunks();
        spawnedMobs.clear();
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
            if (!block.getType().isSolid() && !head.getType().isSolid()) {
                return check;
            }
            check.add(0, 1, 0);
        }
        return original;
    }

    @Override
    public void start(DungeonGame game) {
        if (!Bukkit.getPluginManager().isPluginEnabled("MythicMobs")) {
            game.sendMessage("error.mob_spawn_fail", "<mob>", internalName, "<error>", "MythicMobs not installed");
            this.completed = true;
            return;
        }

        if (!MythicMobsHook.isValidMythicMob(internalName)) {
            game.sendMessage("error.mythic_mob_not_found", "<mob>", internalName);
            this.completed = true;
            return;
        }

        int count = 0;
        String mobName = internalName;

        if (locations.isEmpty()) {
            this.completed = true;
            return;
        }

        int finalAmount = scaleWithParty ? this.amount * game.getParticipants().size() : this.amount;
        if (finalAmount <= 0) finalAmount = 1;

        for (Vector vec : locations) {
            Location loc = new Location(game.getWorld(), vec.getX(), vec.getY(), vec.getZ());
            for (int i = 0; i < finalAmount; i++) {
                double offsetX = (Math.random() - 0.5) * 1.5;
                double offsetZ = (Math.random() - 0.5) * 1.5;
                Location finalLoc = findSafeSpawn(loc.clone().add(0.5 + offsetX, 0, 0.5 + offsetZ));

                try {
                    Entity bukkitEntity = MythicMobsHook.spawnMythicMob(finalLoc, internalName, this.level);
                    if (bukkitEntity != null) {
                        if (bukkitEntity instanceof LivingEntity le) {
                            le.setRemoveWhenFarAway(false);
                            le.setPersistent(true);
                        }
                        Chunk c = finalLoc.getChunk();
                        c.addPluginChunkTicket(SinceDungeon.getPlugin());
                        lockedChunks.add(c);

                        spawnedMobs.put(bukkitEntity.getUniqueId(), finalLoc);
                        this.spawnedEntities.add(bukkitEntity.getUniqueId());
                        count++;

                        String activeName = MythicMobsHook.getActiveMobName(bukkitEntity.getUniqueId());
                        if (activeName != null) mobName = activeName;
                    }
                } catch (Exception e) {
                    game.sendMessage("error.mob_spawn_fail", "<mob>", internalName, "<error>", e.getMessage());
                }
            }
        }

        if (count == 0) {
            this.completed = true;
            game.sendActionMessage(this, "complete", "action.mythic_wave_complete", "<mob>", mobName);
        } else {
            game.sendActionMessage(this, "init", "action.mythic_wave_start", "<amount>", String.valueOf(count), "<mob>", mobName);
        }
    }

    @Override
    public void onTick(DungeonGame game) {
        if (completed) return;

        Set<Chunk> currentChunks = new HashSet<>();
        AtomicReference<String> displayName = new AtomicReference<>(internalName);

        if (!MythicMobsHook.isValidMythicMob(internalName)) {
            this.completed = true;
            return;
        }

        List<Location> mobsToRespawn = new ArrayList<>();
        spawnedMobs.entrySet().removeIf(entry -> {
            UUID uuid = entry.getKey();
            Entity ent = Bukkit.getEntity(uuid);

            if (ent != null) {
                String name = MythicMobsHook.getActiveMobName(uuid);
                if (name != null) displayName.set(name);

                if (ent.isDead()) return true;

                Chunk c = ent.getLocation().getChunk();
                currentChunks.add(c);
                entry.setValue(ent.getLocation());
                return false;
            } else {
                Location lastLoc = entry.getValue();
                if (lastLoc.getWorld().isChunkLoaded(lastLoc.getBlockX() >> 4, lastLoc.getBlockZ() >> 4)) {
                    mobsToRespawn.add(lastLoc);
                    return true;
                } else {
                    lastLoc.getChunk().load();
                    currentChunks.add(lastLoc.getChunk());
                    return false;
                }
            }
        });

        for (Location loc : mobsToRespawn) {
            try {
                Entity bukkitEntity = MythicMobsHook.spawnMythicMob(loc, internalName, this.level);
                if (bukkitEntity != null) {
                    if (bukkitEntity instanceof LivingEntity le) {
                        le.setRemoveWhenFarAway(false);
                        le.setPersistent(true);
                    }
                    spawnedMobs.put(bukkitEntity.getUniqueId(), loc);
                    this.spawnedEntities.add(bukkitEntity.getUniqueId());
                    String name = MythicMobsHook.getActiveMobName(bukkitEntity.getUniqueId());
                    if (name != null) displayName.set(name);
                }
            } catch (Exception ignored) {
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
            this.completed = true;
            game.sendActionMessage(this, "complete", "action.mythic_wave_complete", "<mob>", displayName.get());
        }
    }

    @Override
    public void onEvent(DungeonGame game, Event event) {
        if (event instanceof EntityDeathEvent e) {
            if (MythicMobsHook.isMythicMob(e.getEntity())) {
                if (spawnedMobs.remove(e.getEntity().getUniqueId()) != null) {
                    if (spawnedMobs.isEmpty()) {
                        this.completed = true;

                        String mobName = MythicMobsHook.getActiveMobName(e.getEntity().getUniqueId());
                        if (mobName == null) mobName = internalName;

                        game.sendActionMessage(this, "complete", "action.mythic_wave_complete", "<mob>", mobName);
                    } else {
                        String mobName = MythicMobsHook.getActiveMobName(e.getEntity().getUniqueId());
                        if (mobName == null) mobName = internalName;

                        game.sendActionMessage(this, "progress", "action.mythic_wave_remain", "<amount>", String.valueOf(spawnedMobs.size()), "<mob>", mobName);
                    }
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