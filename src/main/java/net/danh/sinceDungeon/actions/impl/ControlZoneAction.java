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
        this.requiredTicks = requiredSeconds * 20; // Chuyển giây thành tick (20 ticks = 1s)
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

        // Tính toán bán kính hiện tại dựa trên tiến độ (Vòng bo thu hẹp hoặc mở rộng)
        double progress = (double) currentTicks / requiredTicks;
        double currentRadius = startRadius - ((startRadius - endRadius) * progress);
        if (currentRadius <= 0) currentRadius = 1.0;

        // Vẽ vòng bo (Particle Ring) mỗi 5 tick
        if (tickCounter % 5 == 0) {
            for (int i = 0; i < 360; i += 15) {
                double angle = i * Math.PI / 180;
                double x = currentRadius * Math.cos(angle);
                double z = currentRadius * Math.sin(angle);
                centerLoc.getWorld().spawnParticle(Particle.FLAME, centerLoc.clone().add(x, 0.2, z), 1, 0, 0, 0, 0);
            }
        }

        // Đếm số người chơi đang đứng trong vòng bo
        int insideCount = 0;
        int requiredPlayers = Math.max(1, (int) Math.ceil(game.getParticipants().size() * 0.75)); // Yêu cầu 75% thành viên

        for (Player p : game.getParticipants()) {
            if (p.isOnline() && !p.isDead() && p.getGameMode() != GameMode.SPECTATOR) {
                if (p.getLocation().getWorld().equals(game.getWorld())) {
                    if (p.getLocation().distanceSquared(centerLoc) <= currentRadius * currentRadius) {
                        insideCount++;
                    }
                }
            }
        }

        // Nếu đủ số người yêu cầu, tăng tiến độ
        if (insideCount >= requiredPlayers) {
            currentTicks++;

            // Sinh quái vật cản đường (nếu có cấu hình)
            if (!mobType.equalsIgnoreCase("NONE") && tickCounter % mobInterval == 0) {
                spawnInterferenceMob(game, currentRadius);
            }

            if (currentTicks >= requiredTicks) {
                this.completed = true;
                game.sendActionMessage(this, "complete", "action.zone_complete");
                centerLoc.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, centerLoc.clone().add(0, 1, 0), 50, 2, 2, 2, 0.1);
            }
        } else {
            // Cảnh báo nếu không đủ người trong vòng
            if (tickCounter % 40 == 0) {
                game.sendActionMessage(this, "warning", "action.zone_warning", "<required>", String.valueOf(requiredPlayers));
            }
        }
    }

    private void spawnInterferenceMob(DungeonGame game, double currentRadius) {
        try {
            EntityType type = EntityType.valueOf(mobType.toUpperCase());
            double angle = Math.random() * Math.PI * 2;
            // Cho quái sinh ra ở mép vòng bo
            double spawnRadius = currentRadius + 1.0;
            double x = spawnRadius * Math.cos(angle);
            double z = spawnRadius * Math.sin(angle);
            Location spawnLoc = centerLoc.clone().add(x, 0, z);

            game.getWorld().spawnEntity(spawnLoc, type);
            game.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, spawnLoc.add(0, 1, 0), 10, 0.2, 0.2, 0.2, 0.05);
        } catch (Exception ignored) {
            // Invalid mob type, safely ignore
        }
    }
}