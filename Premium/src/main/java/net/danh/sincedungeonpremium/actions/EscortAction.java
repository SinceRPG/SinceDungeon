package net.danh.sincedungeonpremium.actions;

import com.destroystokyo.paper.entity.Pathfinder;
import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.actions.DungeonAction;
import net.danh.sinceDungeon.actions.Tickable;
import net.danh.sinceDungeon.api.events.DungeonEndEvent;
import net.danh.sinceDungeon.hooks.MythicMobsHook;
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
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.*;

/**
 * Premium Action: Escort NPC
 * Spawns an NPC that walks to a target location.
 * Attackers will explicitly target the VIP, and players cannot damage the VIP.
 * Pathfinding range is boosted so the VIP never gets stuck.
 * Now universally supports MythicMob attackers.
 */
public class EscortAction extends DungeonAction implements Tickable {

    private final String entityTypeStr;
    private final String customName;
    private final double maxHealth;
    private final String startLocStr;
    private final String targetLocStr;
    private final double speed;
    private final double successRadius;

    private final boolean vipIsBaby;
    private final List<String> vipAttributes;
    private final List<String> vipEquipment;

    private final String attackerMob;
    private final int attackerAmount;
    private final int attackerInterval;
    private final String attackerName;
    private final boolean attackerIsBaby;
    private final List<String> attackerAttributes;
    private final List<String> attackerEquipment;
    private final Map<UUID, Entity> attackerEntities = new HashMap<>();
    private UUID npcId = null;
    private Mob cachedVip = null;
    private Location targetLocation = null;
    // JIT Optimization: Pre-calculated destination particle location
    private Location targetParticleLoc = null;
    private int tickCounter = 0;

    public EscortAction(String entityTypeStr, String customName, double maxHealth, String startLocStr, String targetLocStr, double speed, double successRadius, boolean vipIsBaby, List<String> vipAttributes, List<String> vipEquipment, String attackerMob, int attackerAmount, int attackerInterval, String attackerName, boolean attackerIsBaby, List<String> attackerAttributes, List<String> attackerEquipment) {
        this.entityTypeStr = entityTypeStr;
        this.customName = customName;
        this.maxHealth = maxHealth;
        this.startLocStr = startLocStr;
        this.targetLocStr = targetLocStr;
        this.speed = speed;
        this.successRadius = successRadius;
        this.vipIsBaby = vipIsBaby;
        this.vipAttributes = vipAttributes;
        this.vipEquipment = vipEquipment;
        this.attackerMob = attackerMob;
        this.attackerAmount = attackerAmount;
        this.attackerInterval = attackerInterval;
        this.attackerName = attackerName;
        this.attackerIsBaby = attackerIsBaby;
        this.attackerAttributes = attackerAttributes;
        this.attackerEquipment = attackerEquipment;
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
                    attribute = getLegacyAttribute(attrName);
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

    @SuppressWarnings("deprecation")
    private Attribute getLegacyAttribute(String attrName) {
        try {
            return Attribute.valueOf(attrName.toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            try {
                return Attribute.valueOf("GENERIC_" + attrName.toUpperCase(Locale.ROOT));
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    @Override
    public void start(DungeonGame game) {
        if (game.getWorld() == null) {
            this.forceComplete();
            return;
        }

        Vector startVec = DungeonLoader.parseVector(startLocStr);
        Vector targetVec = DungeonLoader.parseVector(targetLocStr);

        Location startLocation = new Location(game.getWorld(), startVec.getX() + 0.5, startVec.getY(), startVec.getZ() + 0.5);
        this.targetLocation = new Location(game.getWorld(), targetVec.getX() + 0.5, targetVec.getY(), targetVec.getZ() + 0.5);
        this.targetParticleLoc = this.targetLocation.clone().add(0, 1, 0);

        startLocation.getChunk().load(true);
        targetLocation.getChunk().load(true);

        EntityType type;
        try {
            type = EntityType.valueOf(entityTypeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            type = EntityType.VILLAGER;
        }

        Entity entity = game.getWorld().spawnEntity(startLocation, type);
        if (!(entity instanceof Mob mob)) {
            entity.remove();
            this.forceComplete();
            return;
        }

        applyCustomProperties(mob, customName, vipIsBaby, vipAttributes, vipEquipment);

        AttributeInstance healthAttr = mob.getAttribute(Attribute.MAX_HEALTH);
        if (healthAttr != null) {
            healthAttr.setBaseValue(maxHealth);
            mob.setHealth(maxHealth);
        }

        AttributeInstance speedAttr = mob.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speedAttr != null) {
            speedAttr.setBaseValue(speedAttr.getBaseValue() * speed);
        }

        mob.setTarget(null);
        this.npcId = mob.getUniqueId();
        this.cachedVip = mob;
        this.spawnedEntities.add(npcId);
        this.activeEntities.add(mob); // OPTIMIZATION: Cache physical entity

        forcePathfind(mob);
    }

    @Override
    public void onTick(DungeonGame game) {
        if (completed || npcId == null || cachedVip == null || targetLocation == null || targetParticleLoc == null)
            return;

        tickCounter++;

        if (cachedVip.isDead()) {
            return;
        }

        if (!cachedVip.isValid()) {
            if (cachedVip.getLocation().getWorld() != null && !cachedVip.getLocation().isChunkLoaded()) return;
            game.broadcastMessage("action.escort_failed");
            game.stop(true, DungeonEndEvent.EndReason.FAILED);
            this.forceComplete();
            return;
        }

        if (cachedVip.getLocation().distanceSquared(targetLocation) <= (successRadius * successRadius)) {
            this.forceComplete();
            return;
        }

        if (game.getWorld().getTime() % 10 == 0) {
            game.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, targetParticleLoc, 5, 0.3, 0.3, 0.3, 0);
        }

        if (tickCounter % 20 == 0) {
            forcePathfind(cachedVip);
        }

        if (attackerMob != null && !attackerMob.equalsIgnoreCase("NONE") && attackerInterval > 0) {
            if (tickCounter % attackerInterval == 0) spawnAttackers(game, cachedVip.getLocation());
        }

        // [Performance Fix] Loop direct Entity tracker to force AI targeting
        if (tickCounter % 20 == 0) {
            attackerEntities.values().removeIf(e -> e.isDead() || !e.isValid());
            for (Entity e : attackerEntities.values()) {
                if (e instanceof Mob attackerMobInstance) {
                    attackerMobInstance.setTarget(cachedVip);
                }
            }
        }
    }

    @Override
    public void onEvent(DungeonGame game, Event event) {
        if (completed || npcId == null || cachedVip == null) return;

        // Guaranteed exact detection if the VIP dies
        if (event instanceof EntityDeathEvent e) {
            if (e.getEntity().getUniqueId().equals(cachedVip.getUniqueId())) {
                game.broadcastMessage("action.escort_failed");
                game.stop(true, DungeonEndEvent.EndReason.FAILED);
                this.forceComplete();
            }
        }

        // Prevent players from damaging the VIP
        if (event instanceof EntityDamageByEntityEvent e) {
            if (e.getEntity().getUniqueId().equals(cachedVip.getUniqueId())) {
                if (e.getDamager() instanceof Player || (e.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player)) {
                    e.setCancelled(true);
                }
            }
        }
        // Prevent attackers from switching targets to players
        else if (event instanceof EntityTargetEvent e) {
            if (spawnedEntities.contains(e.getEntity().getUniqueId()) && !e.getEntity().getUniqueId().equals(cachedVip.getUniqueId())) {
                if (e.getTarget() instanceof Player) {
                    e.setTarget(cachedVip);
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
            if (!block.getType().isSolid() && !head.getType().isSolid()) {
                return check;
            }
            check.add(0, 1, 0);
        }
        return original;
    }

    /**
     * Spawns configured attackers dynamically parsing for MythicMobs prefixes.
     * Overrides internal targeting immediately to force aggression upon the VIP entity.
     *
     * @param game   The active dungeon instance.
     * @param npcLoc The target VIP location to spawn assassins around.
     */
    private void spawnAttackers(DungeonGame game, Location npcLoc) {
        try {
            boolean isMythic = attackerMob.toUpperCase(Locale.ROOT).startsWith("MYTHIC:");
            String actualMobId = attackerMob;
            if (isMythic) {
                actualMobId = attackerMob.substring(7);
            } else if (actualMobId.toUpperCase(Locale.ROOT).startsWith("VANILLA:")) {
                actualMobId = actualMobId.substring(8);
            }

            for (int i = 0; i < attackerAmount; i++) {
                double offsetX = (Math.random() - 0.5) * 6.0;
                double offsetZ = (Math.random() - 0.5) * 6.0;

                Location spawnLoc = findSafeSpawn(npcLoc.clone().add(offsetX, 0, offsetZ));

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

                    attMob.setTarget(cachedVip);

                    this.spawnedEntities.add(attMob.getUniqueId());
                    this.activeEntities.add(attMob); // OPTIMIZATION: Cache physical entity
                    this.attackerEntities.put(attMob.getUniqueId(), attMob);
                    game.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, spawnLoc.add(0, 1, 0), 10, 0.2, 0.2, 0.2, 0.05);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void forcePathfind(Mob mob) {
        if (targetLocation == null) return;
        Pathfinder pathfinder = mob.getPathfinder();
        if (!pathfinder.hasPath()) {
            mob.setTarget(null);
            pathfinder.moveTo(targetLocation);
        }
    }

    @Override
    public String getObjectiveText() {
        return SinceDungeon.getPlugin().getLanguageManager().getString("objective.escort_npc");
    }
}