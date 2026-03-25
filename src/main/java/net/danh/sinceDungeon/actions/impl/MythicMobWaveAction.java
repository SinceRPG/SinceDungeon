package net.danh.sinceDungeon.actions.impl;

import io.lumine.mythic.api.mobs.MythicMob;
import io.lumine.mythic.bukkit.BukkitAdapter;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.bukkit.events.MythicMobDeathEvent;
import io.lumine.mythic.core.mobs.ActiveMob;
import net.danh.sinceDungeon.actions.DungeonAction;
import net.danh.sinceDungeon.actions.Tickable;
import net.danh.sinceDungeon.manager.DungeonGame;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.event.Event;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class MythicMobWaveAction extends DungeonAction implements Tickable {
    private final String internalName;
    private final int amount;
    private final List<Vector> locations;
    private final Set<UUID> spawnedMobIds = new HashSet<>();

    public MythicMobWaveAction(String internalName, int amount, List<Vector> locations) {
        this.internalName = internalName;
        this.amount = amount;
        this.locations = locations;
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

        for (int i = 0; i < amount; i++) {
            Vector vec = locations.get(i % locations.size());
            Location loc = new Location(game.getWorld(), vec.getX(), vec.getY(), vec.getZ());
            double offsetX = (Math.random() - 0.5) * 1.5;
            double offsetZ = (Math.random() - 0.5) * 1.5;
            loc.add(0.5 + offsetX, 0, 0.5 + offsetZ);

            try {
                ActiveMob am = mob.spawn(BukkitAdapter.adapt(loc), 1);
                if (am != null) {
                    spawnedMobIds.add(am.getEntity().getUniqueId());
                    count++;
                    mobName = am.getDisplayName();
                }
            } catch (Exception e) {
                game.sendMessage("error.mob_spawn_fail", "<mob>", internalName, "<error>", e.getMessage());
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
        spawnedMobIds.removeIf(uuid -> {
            org.bukkit.entity.Entity ent = Bukkit.getEntity(uuid);
            return ent == null || !ent.isValid() || ent.isDead();
        });

        if (spawnedMobIds.isEmpty()) {
            this.completed = true;
            game.sendMessage("action.mythic_wave_complete");
        }
    }

    @Override
    public void onEvent(DungeonGame game, Event event) {
        if (event instanceof MythicMobDeathEvent e) {
            if (spawnedMobIds.remove(e.getEntity().getUniqueId())) {
                if (spawnedMobIds.isEmpty()) {
                    this.completed = true;
                    game.sendMessage("action.mythic_wave_complete");
                } else {
                    game.sendMessage("action.mythic_wave_remain", "<amount>", String.valueOf(spawnedMobIds.size()), "<mob>", e.getMob().getDisplayName());
                }
            }
        }
    }
}