package net.danh.sinceDungeon.actions.impl;

import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.actions.DungeonAction;
import net.danh.sinceDungeon.actions.Tickable;
import net.danh.sinceDungeon.manager.DungeonGame;
import net.danh.sinceDungeon.utils.ColorUtils;
import net.danh.sinceDungeon.utils.ServerVersion;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
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

    private final Map<UUID, Location> spawnedMobs = new HashMap<>();
    private final Set<Chunk> lockedChunks = new HashSet<>();

    public SpawnWaveAction(EntityType type, int amount, List<Vector> locations,
                           String customName, boolean isBaby, List<String> attributesList, List<String> equipmentList) {
        this.type = type;
        this.amount = amount;
        this.locations = locations;
        this.customName = customName;
        this.isBaby = isBaby;
        this.attributesList = attributesList;
        this.equipmentList = equipmentList;
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
                Entity ent = game.getWorld().spawnEntity(loc, type);
                if (ent instanceof LivingEntity living) {
                    applyCustomProperties(living);
                    spawnedMobs.put(ent.getUniqueId(), loc);
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
            if (!block.getType().isSolid() && !head.getType().isSolid()) return check;
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
        } else if (isBaby && living instanceof org.bukkit.entity.Zombie zombie) {
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
                } else {
                    debug("Failed to find Bukkit Attribute mapping for: " + attrName);
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
                ItemStack item = parseItem(itemData);

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

    private ItemStack parseItem(String data) {
        try {
            String cleanData = data.replace(" ", "");
            String[] parts = cleanData.split(":");
            if (parts.length >= 3 && parts[0].equalsIgnoreCase("MMOITEMS")) {
                if (Bukkit.getPluginManager().isPluginEnabled("MMOItems")) {
                    int amount = parts.length > 3 ? Integer.parseInt(parts[3]) : 1;
                    return net.danh.sinceDungeon.system.MMOItemsHook.getMMOItem(parts[1], parts[2], amount);
                }
            } else {
                Material mat = Material.matchMaterial(parts[0]);
                if (mat != null) {
                    int amount = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;
                    return new ItemStack(mat, amount);
                }
            }
        } catch (Exception ignored) {
        }
        return null;
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

                finalLoc.getChunk().load(true);

                try {
                    Entity ent = game.getWorld().spawnEntity(finalLoc, type);

                    if (ent instanceof LivingEntity living) {
                        applyCustomProperties(living);

                        Chunk c = finalLoc.getChunk();
                        c.addPluginChunkTicket(SinceDungeon.getPlugin());
                        lockedChunks.add(c);

                        game.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, finalLoc.clone().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0.05);
                        game.getWorld().playSound(finalLoc, Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 0.5f, 0.5f);

                        spawnedMobs.put(ent.getUniqueId(), finalLoc);
                        count++;
                    } else if (ent != null) {
                        ent.remove();
                    }
                } catch (Exception e) {
                    debug("EXCEPTION caught while spawning entity: " + e.getMessage());
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