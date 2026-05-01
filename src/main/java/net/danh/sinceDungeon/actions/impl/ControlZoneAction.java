package net.danh.sinceDungeon.actions.impl;

import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.actions.DungeonAction;
import net.danh.sinceDungeon.actions.Tickable;
import net.danh.sinceDungeon.models.DungeonGame;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public class ControlZoneAction extends DungeonAction implements Tickable {
    private final Vector center;
    private final double startRadius;
    private final double endRadius;
    private final int requiredTicks;
    private final String mobType;
    private final int mobInterval;

    private int currentTicks = 0;
    private int tickCounter = 0;
    private Location centerLoc;

    public ControlZoneAction(Vector center, double startRadius, double endRadius, int requiredSeconds, String mobType, int mobInterval) {
        this.center = center;
        this.startRadius = startRadius;
        this.endRadius = endRadius;
        this.requiredTicks = requiredSeconds * 20;
        this.mobType = mobType;
        this.mobInterval = mobInterval;
    }

    @Override
    public String getObjectiveText() {
        int percent = (int) (((double) currentTicks / requiredTicks) * 100);
        String base = SinceDungeon.getPlugin().getMessagesFile().getString("objective.control_zone", "<aqua>Control the Zone: <percent>%");
        return base.replace("<percent>", String.valueOf(percent));
    }

    @Override
    public void start(DungeonGame game) {
        if (game.getWorld() == null) return;
        this.centerLoc = new Location(game.getWorld(), center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5);
        game.sendActionMessage(this, "init", "action.zone_start");
    }

    @Override
    public void onTick(DungeonGame game) {
        if (completed || centerLoc == null) return;
        tickCounter++;

        double progress = (double) currentTicks / requiredTicks;
        double currentRadius = startRadius - ((startRadius - endRadius) * progress);
        if (currentRadius <= 0) currentRadius = 1.0;

        /**
         * NO HARDCODE: Fetch particle type from config.
         */
        if (tickCounter % 5 == 0) {
            String pName = SinceDungeon.getPlugin().getConfigFile().getString("particles.zone_border", "FLAME");
            Particle pType = Particle.FLAME;
            try {
                pType = Particle.valueOf(pName.toUpperCase());
            } catch (Exception ignored) {
            }

            for (int i = 0; i < 360; i += 15) {
                double angle = i * Math.PI / 180;
                double x = currentRadius * Math.cos(angle);
                double z = currentRadius * Math.sin(angle);
                centerLoc.getWorld().spawnParticle(pType, centerLoc.clone().add(x, 0.2, z), 1, 0, 0, 0, 0);
            }
        }

        int insideCount = 0;
        int activePlayers = 0;

        /**
         * LOGIC FIX: Dynamically calculate active players so dead/spectating players
         * do not permanently lock the zone capture requirements.
         */
        for (Player p : game.getParticipants()) {
            if (p.isOnline() && !p.isDead() && p.getGameMode() != GameMode.SPECTATOR) {
                activePlayers++;
                if (p.getLocation().getWorld().equals(game.getWorld())) {
                    if (p.getLocation().distanceSquared(centerLoc) <= currentRadius * currentRadius) {
                        insideCount++;
                    }
                }
            }
        }

        int requiredPlayers = Math.max(1, (int) Math.ceil(activePlayers * 0.75));

        if (insideCount >= requiredPlayers) {
            currentTicks++;

            if (!mobType.equalsIgnoreCase("NONE") && tickCounter % mobInterval == 0) {
                spawnInterferenceMob(game, currentRadius);
            }

            if (currentTicks >= requiredTicks) {
                this.completed = true;
                game.sendActionMessage(this, "complete", "action.zone_complete");

                String pName = SinceDungeon.getPlugin().getConfigFile().getString("particles.zone_complete", "TOTEM_OF_UNDYING");
                try {
                    centerLoc.getWorld().spawnParticle(Particle.valueOf(pName.toUpperCase()), centerLoc.clone().add(0, 1, 0), 50, 2, 2, 2, 0.1);
                } catch (Exception ignored) {
                }
            }
        } else {
            if (tickCounter % 40 == 0) {
                game.sendActionMessage(this, "warning", "action.zone_warning", "<required>", String.valueOf(requiredPlayers));
            }
        }
    }

    private void spawnInterferenceMob(DungeonGame game, double currentRadius) {
        try {
            EntityType type = EntityType.valueOf(mobType.toUpperCase());
            double angle = Math.random() * Math.PI * 2;
            double spawnRadius = currentRadius + 1.0;
            double x = spawnRadius * Math.cos(angle);
            double z = spawnRadius * Math.sin(angle);
            Location spawnLoc = centerLoc.clone().add(x, 0, z);

            game.getWorld().spawnEntity(spawnLoc, type);
            game.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, spawnLoc.add(0, 1, 0), 10, 0.2, 0.2, 0.2, 0.05);
        } catch (Exception ignored) {
        }
    }
}