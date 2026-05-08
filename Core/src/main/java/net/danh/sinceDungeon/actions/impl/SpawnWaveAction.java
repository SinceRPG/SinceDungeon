package net.danh.sinceDungeon.actions.impl;

import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.actions.DungeonAction;
import net.danh.sinceDungeon.actions.Tickable;
import net.danh.sinceDungeon.models.DungeonGame;
import net.danh.sinceDungeon.utils.ColorUtils;
import net.danh.sinceDungeon.utils.ItemBuilder;
import net.danh.sinceDungeon.utils.ServerVersion;
import net.danh.sinceDungeon.utils.SoundUtils;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.*;

public class SpawnWaveAction extends DungeonAction implements Tickable {
    private final EntityType type;
    private final int amount;
    private final List<Vector> locations;
    private final String customName;
    private final boolean isBaby;
    private final List<String> attributesList;
    private final List<String> equipmentList;
    private final boolean scaleWithParty;
    private final List<String> customDrops;

    private final Map<UUID, Location> spawnedMobs = new HashMap<>();

    public SpawnWaveAction(EntityType type, int amount, List<Vector> locations,
                           String customName, boolean isBaby, List<String> attributesList, List<String> equipmentList, boolean scaleWithParty, List<String> customDrops) {
        this.type = type;
        this.amount = amount;
        this.locations = locations;
        this.customName = customName;
        this.isBaby = isBaby;
        this.attributesList = attributesList;
        this.equipmentList = equipmentList;
        this.scaleWithParty = scaleWithParty;
        this.customDrops = customDrops;
    }

    @Override
    public String getObjectiveText() {
        String base = SinceDungeon.getPlugin().getLanguageManager().getString("objective.spawn_wave", "<yellow>Eliminate <red><mob> <gray>(Remaining: <remain>)");
        return base.replace("<mob>", type.name()).replace("<remain>", String.valueOf(spawnedMobs.size()));
    }

    @Override
    public void cleanup(DungeonGame game) {
        super.cleanup(game);
        spawnedMobs.clear();
    }

    @Override
    public void trackChildEntity(UUID uuid, Location loc, String internalName) {
        super.trackChildEntity(uuid, loc, internalName);
        spawnedMobs.put(uuid, loc);
    }

    @Override
    public void onTick(DungeonGame game) {
        if (completed) return;

        // JIT Optimization: Removed massive Ticket/Chunk Memory Leak.
        spawnedMobs.entrySet().removeIf(entry -> {
            UUID uuid = entry.getKey();
            Entity ent = Bukkit.getEntity(uuid);

            if (ent != null) {
                if (ent.isDead() || !ent.isValid()) return true;
                entry.setValue(ent.getLocation());
                return false;
            } else {
                Location lastLoc = entry.getValue();
                if (lastLoc.getWorld() != null && !lastLoc.isChunkLoaded()) {
                    return false; // Safely skip unloaded entities
                }
                return true;
            }
        });

        if (spawnedMobs.isEmpty()) {
            this.completed = true;
            game.sendActionMessage(this, "complete", "action.kill_complete", "<mob>", type.name());
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

                String slot = parts[0].toLowerCase().trim();
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

    @Override
    public void start(DungeonGame game) {
        int count = 0;

        if (locations.isEmpty()) {
            this.completed = true;
            return;
        }

        int finalAmount = scaleWithParty ? this.amount * game.getParticipants().size() : this.amount;
        if (finalAmount <= 0) finalAmount = 1;

        String pName = SinceDungeon.getPlugin().getConfigFile().getString("particles.mob_spawn", "CAMPFIRE_COSY_SMOKE");
        Particle pType;
        try {
            pType = Particle.valueOf(pName.toUpperCase());
        } catch (Exception e) {
            pType = Particle.CAMPFIRE_COSY_SMOKE;
        }

        String sName = SinceDungeon.getPlugin().getConfigFile().getString("sounds.mob_spawn", "entity.zombie.break_wooden_door");
        Sound sType = SoundUtils.getSound(sName);

        for (Vector vec : locations) {
            Location loc = new Location(game.getWorld(), vec.getX(), vec.getY(), vec.getZ());

            for (int i = 0; i < finalAmount; i++) {
                double offsetX = (Math.random() - 0.5) * 1.5;
                double offsetZ = (Math.random() - 0.5) * 1.5;
                Location finalLoc = findSafeSpawn(loc.clone().add(0.5 + offsetX, 0, 0.5 + offsetZ));

                finalLoc.getChunk().load(true);

                try {
                    Entity ent = game.getWorld().spawnEntity(finalLoc, type);

                    if (ent instanceof LivingEntity living) {
                        applyCustomProperties(living);

                        game.getWorld().spawnParticle(pType, finalLoc.clone().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0.05);
                        if (sType != null) {
                            game.getWorld().playSound(finalLoc, sType, 0.5f, 0.5f);
                        }

                        spawnedMobs.put(ent.getUniqueId(), finalLoc);
                        this.spawnedEntities.add(ent.getUniqueId());
                        count++;
                    } else if (ent != null) {
                        ent.remove();
                    }
                } catch (Exception ignored) {
                }
            }
        }

        if (count == 0) {
            this.completed = true;
        } else {
            game.sendActionMessage(this, "init", "action.spawn_wave", "<amount>", String.valueOf(count), "<mob>", type.name());
        }
    }

    @Override
    public void onEvent(DungeonGame game, Event event) {
        if (event instanceof EntityDeathEvent e) {
            if (spawnedMobs.remove(e.getEntity().getUniqueId()) != null) {
                handleCustomDrops(e.getEntity().getLocation());
                if (spawnedMobs.isEmpty()) {
                    this.completed = true;
                    game.sendActionMessage(this, "complete", "action.kill_complete", "<mob>", type.name());
                } else {
                    game.sendActionMessage(this, "progress", "action.kill_remain", "<amount>", String.valueOf(spawnedMobs.size()));
                }
            }
        }
    }

    private void handleCustomDrops(Location loc) {
        if (customDrops == null || customDrops.isEmpty()) return;
        for (String dropStr : customDrops) {
            try {
                String[] split = dropStr.split(";");
                if (split.length < 2) continue;
                String itemData = split[0].trim();
                double chance = Double.parseDouble(split[1].trim());

                if (Math.random() * 100.0 <= chance) {
                    ItemStack item = ItemBuilder.parseDynamicItem(itemData);
                    if (item != null) loc.getWorld().dropItemNaturally(loc, item);
                }
            } catch (Exception ignored) {
            }
        }
    }
}