package net.danh.sincedungeonpremium.actions;

import com.destroystokyo.paper.entity.Pathfinder;
import net.danh.sinceDungeon.actions.DungeonAction;
import net.danh.sinceDungeon.actions.Tickable;
import net.danh.sinceDungeon.api.events.DungeonEndEvent;
import net.danh.sinceDungeon.managers.DungeonLoader;
import net.danh.sinceDungeon.models.DungeonGame;
import net.danh.sinceDungeon.utils.ColorUtils;
import net.danh.sincedungeonpremium.SinceDungeonPremium;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
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
 */
public class EscortAction extends DungeonAction implements Tickable {

    private final String entityTypeStr;
    private final String customName;
    private final double maxHealth;
    private final String startLocStr;
    private final String targetLocStr;
    private final double speed;
    private final double successRadius;
    private final String objectiveText;

    private UUID npcId = null;
    private Location targetLocation = null;

    public EscortAction(String entityTypeStr, String customName, double maxHealth, String startLocStr, String targetLocStr, double speed, double successRadius, String objectiveText) {
        this.entityTypeStr = entityTypeStr;
        this.customName = customName;
        this.maxHealth = maxHealth;
        this.startLocStr = startLocStr;
        this.targetLocStr = targetLocStr;
        this.speed = speed;
        this.successRadius = successRadius;
        this.objectiveText = objectiveText;
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

        startLocation.getChunk().load(true);
        targetLocation.getChunk().load(true);

        EntityType type;
        try {
            type = EntityType.valueOf(entityTypeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            SinceDungeonPremium.getInstance().getLogger().warning("Invalid Escort EntityType: " + entityTypeStr + ". Defaulting to VILLAGER.");
            type = EntityType.VILLAGER;
        }

        org.bukkit.entity.Entity entity = game.getWorld().spawnEntity(startLocation, type);

        if (!(entity instanceof Mob mob)) {
            SinceDungeonPremium.getInstance().getLogger().warning("Escort entity must be a Mob! Provided: " + entityTypeStr);
            entity.remove();
            this.forceComplete();
            return;
        }

        mob.setRemoveWhenFarAway(false);
        mob.setPersistent(true);

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

    @Override
    public void onTick(DungeonGame game) {
        if (completed || npcId == null || targetLocation == null) return;

        org.bukkit.entity.Entity entity = org.bukkit.Bukkit.getEntity(npcId);

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

        if (game.getWorld().getTime() % 40 == 0) {
            forcePathfind(mob);
        }
    }

    private void forcePathfind(Mob mob) {
        if (targetLocation == null) return;
        Pathfinder pathfinder = mob.getPathfinder();
        if (!pathfinder.hasPath()) {
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