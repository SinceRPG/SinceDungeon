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

import java.util.Locale;

/**
 * Premium Action: Projectile Trap
 * A configurable dungeon hazard that periodically shoots Projectiles (Arrows, Fireballs)
 * from a specific location in a designated direction and speed.
 * Optimized: Caches the Enum EntityType to avoid Reflection matching during rapid ticks.
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
    private EntityType cachedType;

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
        loc = game.resolveLocation(vec, 0.5, 0.5, 0.5);
        loc.getChunk().load(true); // Ensured Chunk is actively generated and simulated prior to physics handling

        dir = DungeonLoader.parseVector(directionStr).normalize().multiply(speed);

        try {
            cachedType = EntityType.valueOf(projectileType.toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            String logMsg = SinceDungeonPremium.getInstance().getFileManager().getMessageRaw("log.invalid_projectile");
            SinceDungeonPremium.getInstance().getLogger().warning(logMsg.replace("<type>", projectileType));
            forceComplete();
        }
    }

    @Override
    public void onTick(DungeonGame game) {
        if (completed || loc == null || cachedType == null) return;
        ticksElapsed++;

        if (ticksElapsed >= duration) {
            forceComplete();
            return;
        }

        if (ticksElapsed % interval == 0) {
            try {
                Entity ent = loc.getWorld().spawnEntity(loc, cachedType);
                if (ent instanceof Projectile proj) {
                    proj.setVelocity(dir);
                    spawnedEntities.add(proj.getUniqueId());
                    activeEntities.add(proj); // OPTIMIZATION: Cache physical entity
                } else {
                    ent.remove();
                }
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public String getObjectiveText() {
        return SinceDungeon.getPlugin().getLanguageManager().getString("objective.projectile_trap");
    }
}
