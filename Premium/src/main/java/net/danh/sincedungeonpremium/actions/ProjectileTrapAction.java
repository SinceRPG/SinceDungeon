package net.danh.sincedungeonpremium.actions;

import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.actions.DungeonAction;
import net.danh.sinceDungeon.actions.Tickable;
import net.danh.sinceDungeon.managers.DungeonLoader;
import net.danh.sinceDungeon.models.DungeonGame;
import net.danh.sincedungeonpremium.SinceDungeonPremium;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Projectile;
import org.bukkit.util.Vector;

/**
 * Premium Action: Projectile Trap
 * A configurable dungeon hazard that periodically shoots Projectiles (Arrows, Fireballs)
 * from a specific location in a designated direction and speed.
 */
public class ProjectileTrapAction extends DungeonAction implements Tickable {

    private final String locationStr;
    private final String directionStr;
    private final String projectileType;
    private final int interval;
    private final double speed;
    private final int duration;

    private Location loc;
    private Vector dir;
    private int ticksElapsed = 0;

    public ProjectileTrapAction(String locationStr, String directionStr, String projectileType, int interval, double speed, int duration) {
        this.locationStr = locationStr;
        this.directionStr = directionStr;
        this.projectileType = projectileType;
        this.interval = interval;
        this.speed = speed;
        this.duration = duration;
    }

    @Override
    public void start(DungeonGame game) {
        if (game.getWorld() == null) {
            forceComplete();
            return;
        }
        Vector vec = DungeonLoader.parseVector(locationStr);
        loc = new Location(game.getWorld(), vec.getX() + 0.5, vec.getY() + 0.5, vec.getZ() + 0.5);
        dir = DungeonLoader.parseVector(directionStr).normalize().multiply(speed);
    }

    @Override
    public void onTick(DungeonGame game) {
        if (completed || loc == null) return;
        ticksElapsed++;

        if (ticksElapsed >= duration) {
            forceComplete();
            return;
        }

        if (ticksElapsed % interval == 0) {
            try {
                EntityType type = EntityType.valueOf(projectileType.toUpperCase());
                Entity ent = loc.getWorld().spawnEntity(loc, type);
                if (ent instanceof Projectile proj) {
                    proj.setVelocity(dir);
                    spawnedEntities.add(proj.getUniqueId());
                } else {
                    ent.remove();
                }
            } catch (IllegalArgumentException e) {
                SinceDungeonPremium.getInstance().getLogger().warning("Invalid Projectile EntityType: " + projectileType);
                forceComplete();
            }
        }
    }

    @Override
    public String getObjectiveText() {
        return SinceDungeon.getPlugin().getLanguageManager().getString("objective.projectile_trap");
    }
}