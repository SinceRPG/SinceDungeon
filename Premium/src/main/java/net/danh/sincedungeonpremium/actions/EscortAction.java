package net.danh.sincedungeonpremium.actions;

import com.destroystokyo.paper.entity.Pathfinder;
import net.danh.sinceDungeon.actions.DungeonAction;
import net.danh.sinceDungeon.actions.Tickable;
import net.danh.sinceDungeon.api.events.DungeonEndEvent;
import net.danh.sinceDungeon.managers.DungeonLoader;
import net.danh.sinceDungeon.models.DungeonGame;
import net.danh.sinceDungeon.utils.ColorUtils;
import net.danh.sinceDungeon.utils.ItemBuilder;
import net.danh.sinceDungeon.utils.ServerVersion;
import net.danh.sincedungeonpremium.SinceDungeonPremium;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Premium-Exclusive Action: Escort NPC
 * Responsibilities:
 * - Spawns a custom-named Mob and utilizes Paper's Pathfinder API to navigate it to a destination.
 * - Monitors the NPC's health. If it dies, forcefully stops the dungeon resulting in a failure.
 * - Monitors distance to the target location and successfully completes when within the specified radius.
 * - Features an Attacker System that spawns enemy waves at configured intervals to attempt assassination.
 * - Supports full Entity modifications (Armor, Attributes, Name, Age) for both VIP and Attackers.
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

    private final String objectiveText;

    private UUID npcId = null;
    private Location targetLocation = null;
    private int tickCounter = 0;

    public EscortAction(String entityTypeStr, String customName, double maxHealth, String startLocStr, String targetLocStr, double speed, double successRadius, boolean vipIsBaby, List<String> vipAttributes, List<String> vipEquipment, String attackerMob, int attackerAmount, int attackerInterval, String attackerName, boolean attackerIsBaby, List<String> attackerAttributes, List<String> attackerEquipment, String objectiveText) {
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
        this.objectiveText = objectiveText;
    }

    /**
     * Applies custom parameters globally to either the VIP or the Attacker entity.
     * Evaluates Names, Age, Attributes, and Equipment mappings securely.
     */
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

    /**
     * Initializes the escort mission by spawning the target entity.
     * Applies general custom properties, then overrides specific ones (like MaxHealth/Speed).
     *
     * @param game The active dungeon instance.
     */
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

        startLocation.getChunk().load(true);
        targetLocation.getChunk().load(true);

        EntityType type;
        try {
            type = EntityType.valueOf(entityTypeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            SinceDungeonPremium.getInstance().getLogger().warning("Invalid Escort EntityType: " + entityTypeStr + ". Defaulting to VILLAGER.");
            type = EntityType.VILLAGER;
        }

        Entity entity = game.getWorld().spawnEntity(startLocation, type);

        if (!(entity instanceof Mob mob)) {
            SinceDungeonPremium.getInstance().getLogger().warning("Escort entity must be a Mob! Provided: " + entityTypeStr);
            entity.remove();
            this.forceComplete();
            return;
        }

        // Apply GUI Configuration (Attributes, Name, Equipment)
        applyCustomProperties(mob, customName, vipIsBaby, vipAttributes, vipEquipment);

        // Override Explicit Configuration (MaxHealth, Speed)
        AttributeInstance healthAttr = mob.getAttribute(Attribute.MAX_HEALTH);
        if (healthAttr != null) {
            healthAttr.setBaseValue(maxHealth);
            mob.setHealth(maxHealth);
        }

        AttributeInstance speedAttr = mob.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speedAttr != null) {
            speedAttr.setBaseValue(speedAttr.getBaseValue() * speed);
        }

        // Disable target AI to prevent wandering away from the path
        mob.setTarget(null);

        this.npcId = mob.getUniqueId();
        this.spawnedEntities.add(npcId);

        forcePathfind(mob);
    }

    /**
     * Monitors the NPC continuously. Forces failure if the NPC is killed.
     * Manages attacker spawning intervals.
     *
     * @param game The active dungeon instance.
     */
    @Override
    public void onTick(DungeonGame game) {
        if (completed || npcId == null || targetLocation == null) return;

        tickCounter++;
        Entity entity = Bukkit.getEntity(npcId);

        if (!(entity instanceof Mob mob) || mob.isDead()) {
            sendFailureMessage(game);
            game.stop(true, DungeonEndEvent.EndReason.FAILED);
            this.forceComplete();
            return;
        }

        if (mob.getLocation().distanceSquared(targetLocation) <= (successRadius * successRadius)) {
            this.forceComplete();
            return;
        }

        if (game.getWorld().getTime() % 10 == 0) {
            forcePathfind(mob);
            // Draw a marker at the target destination
            game.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, targetLocation.clone().add(0, 1, 0), 5, 0.3, 0.3, 0.3, 0);
        }

        // Handle attacker wave spawning
        if (attackerMob != null && !attackerMob.equalsIgnoreCase("NONE") && attackerInterval > 0) {
            if (tickCounter % attackerInterval == 0) {
                spawnAttackers(game, mob.getLocation());
            }
        }
    }

    /**
     * Spawns attackers around the NPC to force players to protect it.
     */
    private void spawnAttackers(DungeonGame game, Location npcLoc) {
        EntityType type;
        try {
            type = EntityType.valueOf(attackerMob.toUpperCase());
        } catch (IllegalArgumentException e) {
            return;
        }

        for (int i = 0; i < attackerAmount; i++) {
            double offsetX = (Math.random() - 0.5) * 6.0;
            double offsetZ = (Math.random() - 0.5) * 6.0;
            Location spawnLoc = npcLoc.clone().add(offsetX, 0, offsetZ);

            Entity attacker = game.getWorld().spawnEntity(spawnLoc, type);
            if (attacker instanceof Mob attMob) {
                applyCustomProperties(attMob, attackerName, attackerIsBaby, attackerAttributes, attackerEquipment);
                attMob.setTarget((Mob) Bukkit.getEntity(npcId)); // Target the VIP explicitly
                this.spawnedEntities.add(attMob.getUniqueId());
                game.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, spawnLoc.add(0, 1, 0), 10, 0.2, 0.2, 0.2, 0.05);
            }
        }
    }

    private void forcePathfind(Mob mob) {
        if (targetLocation == null) return;
        Pathfinder pathfinder = mob.getPathfinder();
        if (!pathfinder.hasPath()) {
            mob.setTarget(null); // Prevent distraction
            pathfinder.moveTo(targetLocation);
        }
    }

    private void sendFailureMessage(DungeonGame game) {
        String msg = SinceDungeonPremium.getInstance().getFileManager().getMessageRaw("escort.failed");
        for (Player p : game.getParticipants()) {
            if (p.isOnline()) {
                p.sendMessage(ColorUtils.parseWithPrefix(msg));
            }
        }
    }

    @Override
    public String getObjectiveText() {
        return objectiveText;
    }
}