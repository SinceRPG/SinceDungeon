package net.danh.sinceDungeon.actions.impl;

import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.actions.DungeonAction;
import net.danh.sinceDungeon.actions.Tickable;
import net.danh.sinceDungeon.hooks.MythicMobsHook;
import net.danh.sinceDungeon.models.DungeonGame;
import net.danh.sinceDungeon.utils.ColorUtils;
import net.danh.sinceDungeon.utils.ItemBuilder;
import net.danh.sinceDungeon.utils.ServerVersion;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Locale;

/**
 * Handles the logic for the Control Zone objective.
 * Players must stay within a shrinking circular radius for a specific duration.
 * Spawns interference mobs dynamically. Entity cleanup is handled by the parent class.
 * Uses real-time millisecond tracking to ensure absolute accuracy against TPS drops.
 */
public class ControlZoneAction extends DungeonAction implements Tickable {
    private final Vector center;
    private final double startRadius;
    private final double endRadius;
    private final long requiredMillis;
    private final String mob;
    private final int mobInterval;
    private final int mobLevel;
    private final String customName;
    private final boolean isBaby;
    private final List<String> attributesList;
    private final List<String> equipmentList;
    private long accumulatedMillis = 0;
    private long lastTickTime = 0;
    private int tickCounter = 0;
    private Location centerLoc;

    public ControlZoneAction(Vector center, double startRadius, double endRadius, int requiredSeconds,
                             String mob, int mobInterval, int mobLevel, String customName, boolean isBaby,
                             List<String> attributesList, List<String> equipmentList) {
        this.center = center;
        this.startRadius = startRadius;
        this.endRadius = endRadius;
        this.requiredMillis = requiredSeconds * 1000L;
        this.mob = mob;
        this.mobInterval = mobInterval;
        this.mobLevel = mobLevel;
        this.customName = customName;
        this.isBaby = isBaby;
        this.attributesList = attributesList;
        this.equipmentList = equipmentList;
    }

    @Override
    public String getObjectiveText() {
        int percent = (int) (((double) accumulatedMillis / requiredMillis) * 100);
        percent = Math.min(100, Math.max(0, percent));
        String base = SinceDungeon.getPlugin().getMessagesFile().getString("objective.control_zone", "<aqua>Control the Zone: <percent>%");
        return base.replace("<percent>", String.valueOf(percent));
    }

    @Override
    public void start(DungeonGame game) {
        if (game.getWorld() == null) return;
        this.centerLoc = new Location(game.getWorld(), center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5);
        this.lastTickTime = System.currentTimeMillis();
        game.sendActionMessage(this, "init", "action.zone_start");
    }

    @Override
    public void onTick(DungeonGame game) {
        if (completed || centerLoc == null) return;

        long now = System.currentTimeMillis();
        long delta = now - lastTickTime;
        lastTickTime = now;
        tickCounter++;

        double progress = (double) accumulatedMillis / requiredMillis;
        double currentRadius = startRadius - ((startRadius - endRadius) * progress);
        if (currentRadius <= 0) currentRadius = 1.0;

        if (tickCounter % 5 == 0) {
            String pName = SinceDungeon.getPlugin().getConfigFile().getString("particles.zone_border", "FLAME");
            Particle pType = Particle.FLAME;
            try {
                pType = Particle.valueOf(pName.toUpperCase(Locale.ROOT));
            } catch (Exception ignored) {
            }

            for (int i = 0; i < 360; i += 15) {
                double angle = i * Math.PI / 180;
                double x = currentRadius * Math.cos(angle);
                double z = currentRadius * Math.sin(angle);
                centerLoc.getWorld().spawnParticle(pType, centerLoc.clone().add(x, 0.2, z), 1, 0, 0, 0, 0);
            }
        }

        int insideCount = 0;
        int activePlayers = 0;

        for (Player p : game.getParticipants()) {
            if (p.isOnline() && !p.isDead() && p.getGameMode() != GameMode.SPECTATOR) {
                activePlayers++;
                if (p.getLocation().getWorld().equals(game.getWorld())) {
                    if (p.getLocation().distanceSquared(centerLoc) <= currentRadius * currentRadius) {
                        insideCount++;
                    }
                }
            }
        }

        int requiredPlayers = Math.max(1, (int) Math.ceil(activePlayers * 0.75));

        if (insideCount >= requiredPlayers) {
            accumulatedMillis += delta;

            if (mob != null && !mob.equalsIgnoreCase("NONE") && tickCounter % mobInterval == 0) {
                spawnInterferenceMob(game, currentRadius);
            }

            if (accumulatedMillis >= requiredMillis) {
                this.completed = true;
                game.sendActionMessage(this, "complete", "action.zone_complete");

                String pName = SinceDungeon.getPlugin().getConfigFile().getString("particles.zone_complete", "TOTEM_OF_UNDYING");
                try {
                    centerLoc.getWorld().spawnParticle(Particle.valueOf(pName.toUpperCase(Locale.ROOT)), centerLoc.clone().add(0, 1, 0), 50, 2, 2, 2, 0.1);
                } catch (Exception ignored) {
                }
            }
        } else {
            if (tickCounter % 40 == 0) {
                game.sendActionMessage(this, "warning", "action.zone_warning", "<required>", String.valueOf(requiredPlayers));
            }
        }
    }

    /**
     * Calculates the exact perimeter of the current control zone circle and generates
     * an interference monster on the edge to disrupt the players.
     */
    private void spawnInterferenceMob(DungeonGame game, double currentRadius) {
        try {
            double angle = Math.random() * Math.PI * 2;
            double spawnRadius = currentRadius + 1.0;
            double x = spawnRadius * Math.cos(angle);
            double z = spawnRadius * Math.sin(angle);
            Location spawnLoc = centerLoc.clone().add(x, 0, z);

            spawnLoc.getChunk().load(true);

            boolean isMythic = mob.toUpperCase(Locale.ROOT).startsWith("MYTHIC:");
            String mobId = mob;

            if (isMythic) {
                mobId = mob.substring(7);
            } else if (mob.toUpperCase(Locale.ROOT).startsWith("VANILLA:")) {
                mobId = mob.substring(8);
            }

            String pName = SinceDungeon.getPlugin().getConfigFile().getString("particles.mob_spawn", "CAMPFIRE_COSY_SMOKE");
            Particle pType;
            try {
                pType = Particle.valueOf(pName.toUpperCase());
            } catch (Exception e) {
                pType = Particle.CAMPFIRE_COSY_SMOKE;
            }

            if (isMythic) {
                if (Bukkit.getPluginManager().isPluginEnabled("MythicMobs")) {
                    Entity e = MythicMobsHook.spawnMythicMob(spawnLoc, mobId, mobLevel);
                    if (e instanceof LivingEntity le) {
                        le.setRemoveWhenFarAway(false);
                        le.setPersistent(true);
                        game.getWorld().spawnParticle(pType, spawnLoc.clone().add(0, 1, 0), 10, 0.2, 0.2, 0.2, 0.05);
                        this.spawnedEntities.add(le.getUniqueId());
                    }
                }
            } else {
                EntityType type = EntityType.valueOf(mobId.toUpperCase(Locale.ROOT));
                Entity ent = game.getWorld().spawnEntity(spawnLoc, type);
                if (ent instanceof LivingEntity living) {
                    applyCustomProperties(living);
                    game.getWorld().spawnParticle(pType, spawnLoc.clone().add(0, 1, 0), 10, 0.2, 0.2, 0.2, 0.05);
                    this.spawnedEntities.add(living.getUniqueId());
                }
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * Injects configured attributes and properties into the newly spawned Vanilla entity.
     */
    private void applyCustomProperties(LivingEntity living) {
        living.setRemoveWhenFarAway(false);
        living.setPersistent(true);

        if (customName != null && !customName.trim().isEmpty()) {
            living.customName(ColorUtils.parse(customName));
            living.setCustomNameVisible(true);
        }

        if (isBaby && living instanceof Ageable ageable) {
            ageable.setBaby();
        } else if (isBaby && living instanceof Zombie zombie) {
            zombie.setBaby();
        }

        if (attributesList != null && !attributesList.isEmpty()) {
            for (String attrStr : attributesList) {
                String[] parts = attrStr.split(":", 2);
                if (parts.length < 2) continue;

                String attrName = parts[0].trim().toLowerCase(Locale.ROOT).replace("generic.", "");
                double value;
                try {
                    value = Double.parseDouble(parts[1].trim());
                } catch (NumberFormatException e) {
                    continue;
                }

                Attribute attribute = null;

                if (ServerVersion.isAtLeast(1, 21, 3)) {
                    try {
                        NamespacedKey key = NamespacedKey.minecraft(attrName);
                        attribute = Registry.ATTRIBUTE.get(key);
                    } catch (Throwable ignored) {
                    }
                } else {
                    attribute = getLegacyAttribute(attrName);
                }

                if (attribute != null) {
                    AttributeInstance instance = living.getAttribute(attribute);
                    if (instance != null) {
                        instance.setBaseValue(value);
                        if (attrName.equals("max_health")) {
                            living.setHealth(value);
                        }
                    }
                }
            }
        }

        if (equipmentList != null && !equipmentList.isEmpty() && living.getEquipment() != null) {
            living.getEquipment().setHelmetDropChance(0f);
            living.getEquipment().setChestplateDropChance(0f);
            living.getEquipment().setLeggingsDropChance(0f);
            living.getEquipment().setBootsDropChance(0f);
            living.getEquipment().setItemInMainHandDropChance(0f);
            living.getEquipment().setItemInOffHandDropChance(0f);

            for (String equipStr : equipmentList) {
                String[] parts = equipStr.split(":", 2);
                if (parts.length < 2) continue;

                String slot = parts[0].toLowerCase(Locale.ROOT).trim();
                String itemData = parts[1].trim();
                ItemStack item = ItemBuilder.parseDynamicItem(itemData);

                if (item != null) {
                    switch (slot) {
                        case "helmet", "head" -> living.getEquipment().setHelmet(item);
                        case "chestplate", "chest" -> living.getEquipment().setChestplate(item);
                        case "leggings", "legs" -> living.getEquipment().setLeggings(item);
                        case "boots", "feet" -> living.getEquipment().setBoots(item);
                        case "mainhand", "hand" -> living.getEquipment().setItemInMainHand(item);
                        case "offhand", "shield" -> living.getEquipment().setItemInOffHand(item);
                    }
                }
            }
        }
    }

    @SuppressWarnings("deprecation")
    private Attribute getLegacyAttribute(String attrName) {
        Attribute attr = null;
        try {
            attr = Attribute.valueOf(attrName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
        }
        if (attr == null) {
            try {
                attr = Attribute.valueOf("GENERIC_" + attrName.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return attr;
    }
}