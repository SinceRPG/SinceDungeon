package net.danh.sinceDungeon.actions.impl;

import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.actions.DungeonAction;
import net.danh.sinceDungeon.actions.Tickable;
import net.danh.sinceDungeon.hooks.MythicMobsHook;
import net.danh.sinceDungeon.models.DungeonGame;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Handles the spawning and tracking of MythicMobs.
 * Now supports Multi-Phase bosses through the 'targetToKill' variable.
 */
public class MythicMobWaveAction extends DungeonAction implements Tickable {
    private final String internalName;
    private final int amount;
    private final int level;
    private final List<Vector> locations;
    private final boolean scaleWithParty;
    private final String targetToKill;

    private final Map<UUID, Entity> spawnedMobs = new HashMap<>();
    private boolean hasTargetSpawned = false;

    public MythicMobWaveAction(String internalName, int amount, int level, List<Vector> locations, boolean scaleWithParty, String targetToKill) {
        this.internalName = internalName;
        this.amount = amount;
        this.level = Math.max(1, level);
        this.locations = locations;
        this.scaleWithParty = scaleWithParty;
        this.targetToKill = (targetToKill != null && !targetToKill.trim().isEmpty()) ? targetToKill : "NONE";
    }

    @Override
    public String getObjectiveText() {
        String displayMob = (targetToKill.equalsIgnoreCase("NONE")) ? internalName : targetToKill;
        String base = SinceDungeon.getPlugin().getLanguageManager().getString("objective.mythic_wave", "<dark_red>Defeat Boss <red><mob> <gray>(Remaining: <remain>)");
        return base.replace("<mob>", displayMob).replace("<remain>", String.valueOf(Math.max(1, spawnedMobs.size())));
    }

    @Override
    public void cleanup(DungeonGame game) {
        super.cleanup(game);
        spawnedMobs.clear();
    }

    @Override
    public void trackChildEntity(UUID uuid, Location loc, String internalName) {
        super.trackChildEntity(uuid, loc, internalName);
        Entity ent = Bukkit.getEntity(uuid);
        if (ent != null) spawnedMobs.put(uuid, ent);
    }

    /**
     * Checks if a newly spawned MythicMob is the final phase target we are waiting for.
     * This is dynamically fired via the DungeonGame checking mechanism triggered by MythicListener.
     */
    public void checkAndTrackTarget(UUID uuid, Location loc, String spawnedInternalName) {
        if (!targetToKill.equalsIgnoreCase("NONE") && targetToKill.equalsIgnoreCase(spawnedInternalName)) {
            this.hasTargetSpawned = true;
            Entity ent = Bukkit.getEntity(uuid);
            if (ent != null) {
                this.spawnedMobs.put(uuid, ent);
                this.activeEntities.add(ent); // OPTIMIZATION: Cache physical entity
            }
            this.spawnedEntities.add(uuid);
        }
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

                        spawnedMobs.put(bukkitEntity.getUniqueId(), bukkitEntity);
                        this.spawnedEntities.add(bukkitEntity.getUniqueId());
                        this.activeEntities.add(bukkitEntity); // OPTIMIZATION: Cache physical entity
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

        // [Performance Fix] Eliminated Bukkit.getEntity
        spawnedMobs.entrySet().removeIf(entry -> {
            Entity ent = entry.getValue();

            if (ent.isDead()) return true;
            if (!ent.isValid()) {
                Location lastLoc = ent.getLocation();
                if (lastLoc.getWorld() != null && !lastLoc.isChunkLoaded()) {
                    return false;
                }
                return true;
            }
            return false;
        });

        // Custom Phase Logic: Wait until the target mob spawns before completing the wave.
        if (!targetToKill.equalsIgnoreCase("NONE")) {
            if (hasTargetSpawned && spawnedMobs.isEmpty()) {
                this.completed = true;
                game.sendActionMessage(this, "complete", "action.mythic_wave_complete", "<mob>", targetToKill);
            }
        } else {
            if (spawnedMobs.isEmpty()) {
                this.completed = true;
                game.sendActionMessage(this, "complete", "action.mythic_wave_complete", "<mob>", internalName);
            }
        }
    }

    @Override
    public void onEvent(DungeonGame game, Event event) {
        if (event instanceof EntityDeathEvent e) {
            if (MythicMobsHook.isMythicMob(e.getEntity())) {
                if (spawnedMobs.remove(e.getEntity().getUniqueId()) != null) {

                    // Do not broadcast progress if we are waiting for a phase transition
                    if (!targetToKill.equalsIgnoreCase("NONE") && !hasTargetSpawned) {
                        return;
                    }

                    if (spawnedMobs.isEmpty() && (targetToKill.equalsIgnoreCase("NONE") || hasTargetSpawned)) {
                        this.completed = true;
                        String mobName = MythicMobsHook.getActiveMobName(e.getEntity().getUniqueId());
                        if (mobName == null)
                            mobName = (!targetToKill.equalsIgnoreCase("NONE")) ? targetToKill : internalName;

                        game.sendActionMessage(this, "complete", "action.mythic_wave_complete", "<mob>", mobName);
                    } else if (targetToKill.equalsIgnoreCase("NONE") || hasTargetSpawned) {
                        String mobName = MythicMobsHook.getActiveMobName(e.getEntity().getUniqueId());
                        if (mobName == null)
                            mobName = (!targetToKill.equalsIgnoreCase("NONE")) ? targetToKill : internalName;

                        game.sendActionMessage(this, "progress", "action.mythic_wave_remain", "<amount>", String.valueOf(spawnedMobs.size()), "<mob>", mobName);
                    }
                }
            }
        }
    }
}