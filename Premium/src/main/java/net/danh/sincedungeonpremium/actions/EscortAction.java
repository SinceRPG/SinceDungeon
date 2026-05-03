package net.danh.sincedungeonpremium.actions;

import com.destroystokyo.paper.entity.Pathfinder;
import net.danh.sinceDungeon.actions.DungeonAction;
import net.danh.sinceDungeon.actions.Tickable;
import net.danh.sinceDungeon.api.events.DungeonEndEvent;
import net.danh.sinceDungeon.managers.DungeonLoader;
import net.danh.sinceDungeon.models.DungeonGame;
import net.danh.sinceDungeon.utils.ColorUtils;
import net.danh.sincedungeonpremium.SinceDungeonPremium;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.UUID;

/**
 * Premium-Exclusive Action: Escort NPC
 * Responsibilities:
 * - Spawns a custom-named Mob and utilizes Paper's Pathfinder API to navigate it to a destination.
 * - Monitors the NPC's health. If it dies, forcefully stops the dungeon resulting in a failure.
 * - Monitors distance to the target location and successfully completes when within the specified radius.
 * - Features an Attacker System that spawns enemy waves at configured intervals to attempt assassination.
 */
public class EscortAction extends DungeonAction implements Tickable {

    private final String entityTypeStr;
    private final String customName;
    private final double maxHealth;
    private final String startLocStr;
    private final String targetLocStr;
    private final double speed;
    private final double successRadius;
    private final String attackerMob;
    private final int attackerAmount;
    private final int attackerInterval;
    private final String objectiveText;

    private UUID npcId = null;
    private Location targetLocation = null;
    private int tickCounter = 0;

    public EscortAction(String entityTypeStr, String customName, double maxHealth, String startLocStr, String targetLocStr, double speed, double successRadius, String attackerMob, int attackerAmount, int attackerInterval, String objectiveText) {
        this.entityTypeStr = entityTypeStr;
        this.customName = customName;
        this.maxHealth = maxHealth;
        this.startLocStr = startLocStr;
        this.targetLocStr = targetLocStr;
        this.speed = speed;
        this.successRadius = successRadius;
        this.attackerMob = attackerMob;
        this.attackerAmount = attackerAmount;
        this.attackerInterval = attackerInterval;
        this.objectiveText = objectiveText;
    }

    /**
     * Initializes the escort mission by spawning the target entity.
     * Properly utilizes imported classes instead of inline full paths.
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

        mob.setRemoveWhenFarAway(false);
        mob.setPersistent(true);

        // Disable target AI to prevent wandering away from the path
        mob.setTarget(null);

        if (customName != null && !customName.isEmpty()) {
            mob.customName(ColorUtils.parse(customName));
            mob.setCustomNameVisible(true);
        }

        AttributeInstance healthAttr = mob.getAttribute(Attribute.MAX_HEALTH);
        if (healthAttr != null) {
            healthAttr.setBaseValue(maxHealth);
            mob.setHealth(maxHealth);
        }

        AttributeInstance speedAttr = mob.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speedAttr != null) {
            speedAttr.setBaseValue(speedAttr.getBaseValue() * speed);
        }

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
                attMob.setRemoveWhenFarAway(false);
                attMob.setPersistent(true);
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