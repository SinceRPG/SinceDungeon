package net.danh.sinceDungeon.actions.impl;

import io.lumine.mythic.api.mobs.MythicMob;
import io.lumine.mythic.bukkit.BukkitAdapter;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.bukkit.events.MythicMobDeathEvent;
import io.lumine.mythic.core.mobs.ActiveMob;
import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.actions.DungeonAction;
import net.danh.sinceDungeon.actions.Tickable;
import net.danh.sinceDungeon.manager.DungeonGame;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.event.Event;
import org.bukkit.util.Vector;

import java.util.*;

public class MythicMobWaveAction extends DungeonAction implements Tickable {
    private final String internalName;
    private final int amount;
    private final int level;
    private final List<Vector> locations;
    private final Map<UUID, Location> spawnedMobs = new HashMap<>();
    private final Set<Chunk> lockedChunks = new HashSet<>();

    public MythicMobWaveAction(String internalName, int amount, int level, List<Vector> locations) {
        this.internalName = internalName;
        this.amount = amount;
        this.level = Math.max(1, level);
        this.locations = locations;
    }

    @Override
    public String getObjectiveText() {
        String base = SinceDungeon.getPlugin().getMessagesFile().getString("objective.mythic_wave", "<dark_red>Defeat Boss <red><mob> <gray>(Remaining: <remain>)");
        return base.replace("<mob>", internalName).replace("<remain>", String.valueOf(spawnedMobs.size()));
    }

    @Override
    public void cleanup(DungeonGame game) {
        unlockChunks();
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
        if (!Bukkit.getPluginManager().isPluginEnabled("MythicMobs")) {
            game.sendMessage("error.mob_spawn_fail", "<mob>", internalName, "<error>", "MythicMobs not installed");
            this.completed = true;
            return;
        }

        MythicMob mob = MythicBukkit.inst().getMobManager().getMythicMob(internalName).orElse(null);
        if (mob == null) {
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

        for (Vector vec : locations) {
            Location loc = new Location(game.getWorld(), vec.getX(), vec.getY(), vec.getZ());
            for (int i = 0; i < amount; i++) {
                double offsetX = (Math.random() - 0.5) * 1.5;
                double offsetZ = (Math.random() - 0.5) * 1.5;
                Location finalLoc = findSafeSpawn(loc.clone().add(0.5 + offsetX, 0, 0.5 + offsetZ));

                try {
                    ActiveMob am = mob.spawn(BukkitAdapter.adapt(finalLoc), this.level);
                    if (am != null) {
                        Entity bukkitEntity = am.getEntity().getBukkitEntity();
                        if (bukkitEntity instanceof org.bukkit.entity.LivingEntity le) {
                            le.setRemoveWhenFarAway(false);
                            le.setPersistent(true);
                        }

                        Chunk c = finalLoc.getChunk();
                        c.addPluginChunkTicket(SinceDungeon.getPlugin());
                        lockedChunks.add(c);

                        spawnedMobs.put(am.getEntity().getUniqueId(), finalLoc);
                        count++;
                        mobName = am.getDisplayName();
                    }
                } catch (Exception e) {
                    game.sendMessage("error.mob_spawn_fail", "<mob>", internalName, "<error>", e.getMessage());
                }
            }
        }

        if (count == 0) {
            this.completed = true;
            game.sendMessage("action.mythic_wave_complete");
        } else {
            game.sendMessage("action.mythic_wave_start", "<amount>", String.valueOf(count), "<mob>", mobName);
        }
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
            game.sendMessage("action.mythic_wave_complete");
        }
    }

    @Override
    public void onEvent(DungeonGame game, Event event) {
        if (event instanceof MythicMobDeathEvent e) {
            if (spawnedMobs.remove(e.getEntity().getUniqueId()) != null) {
                if (spawnedMobs.isEmpty()) {
                    unlockChunks();
                    this.completed = true;
                    game.sendMessage("action.mythic_wave_complete");
                } else {
                    game.sendMessage("action.mythic_wave_remain", "<amount>", String.valueOf(spawnedMobs.size()), "<mob>", e.getMob().getDisplayName());
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