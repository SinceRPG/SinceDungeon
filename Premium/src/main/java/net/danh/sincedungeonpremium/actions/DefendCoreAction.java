package net.danh.sincedungeonpremium.actions;

import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.actions.DungeonAction;
import net.danh.sinceDungeon.actions.Tickable;
import net.danh.sinceDungeon.api.events.DungeonEndEvent;
import net.danh.sinceDungeon.managers.DungeonLoader;
import net.danh.sinceDungeon.models.DungeonGame;
import net.danh.sinceDungeon.utils.ColorUtils;
import net.danh.sinceDungeon.utils.ItemBuilder;
import net.danh.sinceDungeon.utils.ServerVersion;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Premium Action: Defend Core
 * Players must protect a stationary entity (e.g., an Ender Crystal or Iron Golem)
 * from waves of enemies for a specific duration.
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

    private UUID coreId = null;
    private Location coreLoc = null;
    private int ticksElapsed = 0;

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
    public void start(DungeonGame game) {
        if (game.getWorld() == null) {
            forceComplete();
            return;
        }

        Vector vec = DungeonLoader.parseVector(locationStr);
        this.coreLoc = new Location(game.getWorld(), vec.getX() + 0.5, vec.getY(), vec.getZ() + 0.5);
        coreLoc.getChunk().load(true);

        EntityType type;
        try {
            type = EntityType.valueOf(coreTypeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            type = EntityType.IRON_GOLEM;
        }

        Entity entity = game.getWorld().spawnEntity(coreLoc, type);
        if (entity instanceof LivingEntity core) {
            core.setRemoveWhenFarAway(false);
            core.setPersistent(true);

            if (coreName != null && !coreName.isEmpty()) {
                core.customName(ColorUtils.parse(coreName));
                core.setCustomNameVisible(true);
            }

            AttributeInstance healthAttr = core.getAttribute(Attribute.MAX_HEALTH);
            if (healthAttr != null) {
                healthAttr.setBaseValue(coreHealth);
                core.setHealth(coreHealth);
            }

            // Immobilize the core
            AttributeInstance speedAttr = core.getAttribute(Attribute.MOVEMENT_SPEED);
            if (speedAttr != null) {
                speedAttr.setBaseValue(0.0);
            }

            if (core instanceof Mob mobCore) {
                mobCore.setTarget(null);
                mobCore.setAware(false);
            }

            this.coreId = core.getUniqueId();
            this.spawnedEntities.add(coreId);
        } else {
            entity.remove();
            forceComplete();
        }
    }

    @Override
    public void onTick(DungeonGame game) {
        if (completed || coreId == null) return;

        Entity entity = Bukkit.getEntity(coreId);

        if (entity == null) {
            // Entities might temporarily return null if chunks unload. Don't fail the mission immediately.
            return;
        }

        if (entity.isDead()) {
            game.broadcastMessage("action.defend_failed");
            game.stop(true, DungeonEndEvent.EndReason.FAILED);
            forceComplete();
            return;
        }

        ticksElapsed++;

        if (ticksElapsed >= durationTicks) {
            forceComplete();
            return;
        }

        if (attackerMob != null && !attackerMob.equalsIgnoreCase("NONE") && attackerInterval > 0) {
            if (ticksElapsed % attackerInterval == 0) {
                spawnAttackers(game, entity.getLocation());
            }
        }

        // Force attackers to target the core
        if (ticksElapsed % 20 == 0) {
            for (UUID id : spawnedEntities) {
                if (id.equals(coreId)) continue;
                Entity e = Bukkit.getEntity(id);
                if (e instanceof Mob attacker && entity instanceof LivingEntity livingCore) {
                    attacker.setTarget(livingCore);
                }
            }
        }
    }

    @Override
    public void onEvent(DungeonGame game, Event event) {
        if (completed || coreId == null) return;

        if (event instanceof EntityDamageByEntityEvent e) {
            if (e.getEntity().getUniqueId().equals(coreId)) {
                if (e.getDamager() instanceof Player) {
                    e.setCancelled(true); // Players cannot damage the core
                }
            }
        } else if (event instanceof EntityTargetEvent e) {
            if (spawnedEntities.contains(e.getEntity().getUniqueId()) && !e.getEntity().getUniqueId().equals(coreId)) {
                if (e.getTarget() instanceof Player) {
                    Entity core = Bukkit.getEntity(coreId);
                    if (core instanceof LivingEntity livingCore) {
                        e.setTarget(livingCore); // Lock onto core
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

    private void spawnAttackers(DungeonGame game, Location centerLoc) {
        try {
            EntityType type = EntityType.valueOf(attackerMob.toUpperCase(Locale.ROOT));
            for (int i = 0; i < attackerAmount; i++) {
                double offsetX = (Math.random() - 0.5) * 12.0;
                double offsetZ = (Math.random() - 0.5) * 12.0;

                Location spawnLoc = findSafeSpawn(centerLoc.clone().add(offsetX, 0, offsetZ));
                Entity attacker = game.getWorld().spawnEntity(spawnLoc, type);

                if (attacker instanceof Mob attMob) {
                    applyCustomProperties(attMob, attackerName, attackerIsBaby, attackerAttributes, attackerEquipment);
                    Entity core = Bukkit.getEntity(coreId);
                    if (core instanceof LivingEntity livingCore) {
                        attMob.setTarget(livingCore);
                    }
                    this.spawnedEntities.add(attMob.getUniqueId());
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

                Attribute attribute = null;
                if (ServerVersion.isAtLeast(1, 21, 3)) {
                    try {
                        attribute = Registry.ATTRIBUTE.get(NamespacedKey.minecraft(attrName));
                    } catch (Throwable ignored) {
                    }
                } else {
                    try {
                        attribute = Attribute.valueOf(attrName.toUpperCase(Locale.ROOT));
                    } catch (Exception e) {
                        try {
                            attribute = Attribute.valueOf("GENERIC_" + attrName.toUpperCase(Locale.ROOT));
                        } catch (Exception ignored) {
                        }
                    }
                }

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