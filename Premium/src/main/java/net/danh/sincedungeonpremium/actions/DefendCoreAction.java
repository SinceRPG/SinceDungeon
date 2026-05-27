package net.danh.sincedungeonpremium.actions;

import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.actions.DungeonAction;
import net.danh.sinceDungeon.actions.Tickable;
import net.danh.sinceDungeon.api.events.DungeonEndEvent;
import net.danh.sinceDungeon.hooks.MythicMobsHook;
import net.danh.sinceDungeon.managers.DungeonLoader;
import net.danh.sinceDungeon.models.DungeonGame;
import net.danh.sinceDungeon.utils.AttributeUtils;
import net.danh.sinceDungeon.utils.ColorUtils;
import net.danh.sinceDungeon.utils.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.*;

/**
 * Premium Action: Defend Core
 * Players must protect a stationary entity (e.g., an Ender Crystal or Iron Golem)
 * from waves of enemies for a specific duration.
 * Now universally supports Non-Living Entities (Crystals) via independent event-based Health Tracking,
 * and seamlessly allows MythicMob attackers.
 */
public class DefendCoreAction extends DungeonAction implements Tickable {

    private final String locationStr;
    private final String coreTypeStr;
    private final String coreName;
    private final double coreHealth;
    private final int durationTicks;
    private final String attackerMob;
    private final int attackerAmount;
    private final int attackerInterval;
    private final String attackerName;
    private final boolean attackerIsBaby;
    private final List<String> attackerAttributes;
    private final List<String> attackerEquipment;
    private final Map<UUID, Entity> attackerEntities = new HashMap<>();
    private UUID coreId = null;
    private Entity cachedCore = null;
    private Location coreLoc = null;
    private int ticksElapsed = 0;
    private int unloadedTicks = 0; // Failsafe for chunks unloading

    private double currentCoreHealth; // Independent Tracking Support for Ender Crystals

    public DefendCoreAction(String locationStr, String coreTypeStr, String coreName, double coreHealth, int durationTicks, String attackerMob, int attackerAmount, int attackerInterval, String attackerName, boolean attackerIsBaby, List<String> attackerAttributes, List<String> attackerEquipment) {
        this.locationStr = locationStr;
        this.coreTypeStr = coreTypeStr;
        this.coreName = coreName;
        this.coreHealth = coreHealth;
        this.durationTicks = durationTicks;
        this.attackerMob = attackerMob;
        this.attackerAmount = attackerAmount;
        this.attackerInterval = attackerInterval;
        this.attackerName = attackerName;
        this.attackerIsBaby = attackerIsBaby;
        this.attackerAttributes = attackerAttributes;
        this.attackerEquipment = attackerEquipment;
    }

    @Override
    public void cleanup(DungeonGame game) {
        super.cleanup(game); // Note: Flushes active entities
        attackerEntities.clear();
        cachedCore = null;
        coreLoc = null;
    }

    @Override
    public void start(DungeonGame game) {
        if (game.getWorld() == null) {
            forceComplete();
            return;
        }

        Vector vec = DungeonLoader.parseVector(locationStr);
        this.coreLoc = game.resolveLocation(vec, 0.5, 0, 0.5);
        coreLoc.getChunk().load(true);

        EntityType type;
        try {
            type = EntityType.valueOf(coreTypeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            type = EntityType.IRON_GOLEM;
        }

        this.currentCoreHealth = this.coreHealth;
        Entity entity = game.getWorld().spawnEntity(coreLoc, type);

        entity.setPersistent(true);
        if (coreName != null && !coreName.isEmpty()) {
            entity.customName(ColorUtils.parse(coreName));
            entity.setCustomNameVisible(true);
        }

        if (entity instanceof LivingEntity core) {
            core.setRemoveWhenFarAway(false);

            // Immobilize the core if it's a living entity
            AttributeInstance speedAttr = core.getAttribute(Attribute.MOVEMENT_SPEED);
            if (speedAttr != null) {
                speedAttr.setBaseValue(0.0);
            }

            if (core instanceof Mob mobCore) {
                mobCore.setTarget(null);
                mobCore.setAware(false);
            }
        }

        this.coreId = entity.getUniqueId();
        this.cachedCore = entity;
        this.spawnedEntities.add(coreId);
        this.activeEntities.add(entity); // OPTIMIZATION: Cache physical entity
    }

    @Override
    public void onTick(DungeonGame game) {
        if (completed || coreId == null || cachedCore == null) return;

        if (cachedCore.isDead()) {
            return; // Handled by Event Listener
        }

        if (!cachedCore.isValid()) {
            if (cachedCore.getLocation().getWorld() != null && !cachedCore.getLocation().isChunkLoaded()) return;
            game.broadcastMessage("action.defend_failed");
            game.stop(true, DungeonEndEvent.EndReason.FAILED);
            this.forceComplete();
            return;
        }

        ticksElapsed++;

        if (ticksElapsed >= durationTicks) {
            forceComplete();
            return;
        }

        if (attackerMob != null && !attackerMob.equalsIgnoreCase("NONE") && attackerInterval > 0) {
            if (ticksElapsed % attackerInterval == 0) {
                spawnAttackers(game, cachedCore.getLocation());
            }
        }

        // [Performance Fix] Loop direct Entity tracker to force AI targeting
        if (ticksElapsed % 20 == 0) {
            attackerEntities.values().removeIf(e -> e.isDead() || !e.isValid());
            for (Entity e : attackerEntities.values()) {
                if (e instanceof Mob attacker && cachedCore instanceof LivingEntity livingCore) {
                    attacker.setTarget(livingCore);
                } else if (e instanceof Mob attacker) {
                    attacker.getPathfinder().moveTo(cachedCore.getLocation());
                }
            }
        }
    }

    @Override
    public void onEvent(DungeonGame game, Event event) {
        if (completed || coreId == null || cachedCore == null) return;

        // Guaranteed exact detection if the Core dies (Void/Delete)
        if (event instanceof EntityDeathEvent e) {
            if (e.getEntity().getUniqueId().equals(cachedCore.getUniqueId())) {
                game.broadcastMessage("action.defend_failed");
                game.stop(true, DungeonEndEvent.EndReason.FAILED);
                this.forceComplete();
            }
        }

        if (event instanceof EntityDamageEvent e) {
            if (e.getEntity().getUniqueId().equals(cachedCore.getUniqueId())) {
                e.setCancelled(true); // Always cancel to prevent knockback and standard death

                // Players cannot damage the core
                if (e instanceof EntityDamageByEntityEvent edbe && edbe.getDamager() instanceof Player) {
                    return;
                }

                currentCoreHealth -= e.getFinalDamage();

                if (currentCoreHealth <= 0) {
                    e.getEntity().remove();
                    game.broadcastMessage("action.defend_failed");
                    game.stop(true, DungeonEndEvent.EndReason.FAILED);
                    forceComplete();
                }
            }
        } else if (event instanceof EntityTargetEvent e) {
            if (spawnedEntities.contains(e.getEntity().getUniqueId()) && !e.getEntity().getUniqueId().equals(cachedCore.getUniqueId())) {
                if (e.getTarget() instanceof Player) {
                    if (cachedCore instanceof LivingEntity livingCore) {
                        e.setTarget(livingCore); // Lock onto core without UUID lookup
                    }
                }
            }
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
            if (!block.getType().isSolid() && !head.getType().isSolid()) return check;
            check.add(0, 1, 0);
        }
        return original;
    }

    /**
     * Spawns configured attackers dynamically parsing for MythicMobs prefixes.
     * Overrides internal targeting immediately to force aggression upon the Core entity.
     *
     * @param game      The active dungeon instance.
     * @param centerLoc The target Core location to spawn invaders around.
     */
    private void spawnAttackers(DungeonGame game, Location centerLoc) {
        try {
            boolean isMythic = attackerMob.toUpperCase(Locale.ROOT).startsWith("MYTHIC:");
            String actualMobId = attackerMob;
            if (isMythic) {
                actualMobId = attackerMob.substring(7);
            } else if (actualMobId.toUpperCase(Locale.ROOT).startsWith("VANILLA:")) {
                actualMobId = actualMobId.substring(8);
            }

            for (int i = 0; i < attackerAmount; i++) {
                double offsetX = (Math.random() - 0.5) * 12.0;
                double offsetZ = (Math.random() - 0.5) * 12.0;

                Location spawnLoc = findSafeSpawn(centerLoc.clone().add(offsetX, 0, offsetZ));

                Entity attacker = null;

                if (isMythic) {
                    if (Bukkit.getPluginManager().isPluginEnabled("MythicMobs")) {
                        attacker = MythicMobsHook.spawnMythicMob(spawnLoc, actualMobId, 1);
                    }
                } else {
                    try {
                        attacker = game.getWorld().spawnEntity(spawnLoc, EntityType.valueOf(actualMobId.toUpperCase(Locale.ROOT)));
                    } catch (Exception ignored) {
                    }
                }

                if (attacker instanceof Mob attMob) {
                    applyCustomProperties(attMob, attackerName, attackerIsBaby, attackerAttributes, attackerEquipment);

                    if (cachedCore instanceof LivingEntity livingCore) {
                        attMob.setTarget(livingCore);
                    } else if (cachedCore != null) {
                        attMob.getPathfinder().moveTo(cachedCore.getLocation());
                    }
                    this.spawnedEntities.add(attMob.getUniqueId());
                    this.activeEntities.add(attMob); // OPTIMIZATION: Cache physical entity
                    this.attackerEntities.put(attMob.getUniqueId(), attMob);
                    game.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, spawnLoc.add(0, 1, 0), 10, 0.2, 0.2, 0.2, 0.05);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void applyCustomProperties(LivingEntity living, String name, boolean isBaby, List<String> attributesList, List<String> equipmentList) {
        living.setRemoveWhenFarAway(false);
        living.setPersistent(true);

        if (name != null && !name.trim().isEmpty()) {
            living.customName(ColorUtils.parse(name));
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

                Attribute attribute = AttributeUtils.resolve(attrName);

                if (attribute != null) {
                    AttributeInstance instance = living.getAttribute(attribute);
                    if (instance != null) {
                        instance.setBaseValue(value);
                        if (attrName.equals("max_health")) living.setHealth(value);
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
                ItemStack item = ItemBuilder.parseDynamicItem(parts[1].trim());

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

    @Override
    public String getObjectiveText() {
        return SinceDungeon.getPlugin().getLanguageManager().getString("objective.defend_core");
    }
}
