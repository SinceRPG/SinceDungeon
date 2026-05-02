package net.danh.sinceDungeon.actions.impl;

import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.actions.DungeonAction;
import net.danh.sinceDungeon.actions.Tickable;
import net.danh.sinceDungeon.models.DungeonGame;
import net.danh.sinceDungeon.utils.ColorUtils;
import net.danh.sinceDungeon.utils.ServerVersion;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.*;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.*;

/**
 * Spawns a customizable Boss Entity.
 * Features: Health Scaling, BossBar, Multi-Phases, and Enrage Timer.
 */
public class BossBattleAction extends DungeonAction implements Tickable {

    private final Vector spawnLoc;
    private final EntityType mobType;
    private final String customName;
    private final double baseHealth;
    private final double scaleHealthPerPlayer;
    private final String barColor;
    private final String barStyle;
    private final List<String> baseAttributes;
    private final Map<Integer, PhaseData> phases;

    // Enrage Settings
    private final int enrageTime;
    private final String enrageMessage;
    private final List<String> enrageAttributes;

    private UUID bossId = null;
    private BossBar bossBar = null;
    private final Set<Integer> executedPhases = new HashSet<>();

    private long spawnTimeMillis = 0;
    private boolean isEnraged = false;

    public BossBattleAction(Vector spawnLoc, EntityType mobType, String customName, double baseHealth, double scaleHealthPerPlayer, String barColor, String barStyle, List<String> baseAttributes, Map<Integer, PhaseData> phases, int enrageTime, String enrageMessage, List<String> enrageAttributes) {
        this.spawnLoc = spawnLoc;
        this.mobType = mobType;
        this.customName = customName;
        this.baseHealth = baseHealth;
        this.scaleHealthPerPlayer = scaleHealthPerPlayer;
        this.barColor = barColor;
        this.barStyle = barStyle;
        this.baseAttributes = baseAttributes;
        this.phases = phases;
        this.enrageTime = enrageTime;
        this.enrageMessage = enrageMessage;
        this.enrageAttributes = enrageAttributes;
    }

    @Override
    public String getObjectiveText() {
        return SinceDungeon.getPlugin().getMessagesFile().getString("objective.boss_battle", "<red>Defeat the Boss!");
    }

    @Override
    public void start(DungeonGame game) {
        Location loc = new Location(game.getWorld(), spawnLoc.getX() + 0.5, spawnLoc.getY(), spawnLoc.getZ() + 0.5);
        loc.getChunk().load(true);

        Entity entity = game.getWorld().spawnEntity(loc, mobType);
        if (!(entity instanceof LivingEntity boss)) {
            entity.remove();
            this.completed = true;
            return;
        }

        boss.setRemoveWhenFarAway(false);
        boss.setPersistent(true);
        if (customName != null && !customName.isEmpty()) {
            boss.customName(ColorUtils.parse(customName));
            boss.setCustomNameVisible(true);
        }

        this.bossId = boss.getUniqueId();
        this.spawnTimeMillis = System.currentTimeMillis();

        // Scale Health
        double totalHealth = baseHealth + (scaleHealthPerPlayer * Math.max(0, game.getParticipants().size() - 1));
        AttributeInstance healthAttr = boss.getAttribute(Attribute.MAX_HEALTH);
        if (healthAttr != null) {
            healthAttr.setBaseValue(totalHealth);
            boss.setHealth(totalHealth);
        }

        applyAttributes(boss, baseAttributes);

        // Setup BossBar
        BarColor color = BarColor.RED;
        try { color = BarColor.valueOf(barColor.toUpperCase()); } catch (Exception ignored) {}

        BarStyle style = BarStyle.SOLID;
        try { style = BarStyle.valueOf(barStyle.toUpperCase()); } catch (Exception ignored) {}

        String title = customName != null && !customName.isEmpty() ? ColorUtils.toPlainText(ColorUtils.parse(customName)) : mobType.name();
        bossBar = Bukkit.createBossBar(title, color, style);

        for (Player p : game.getParticipants()) {
            if (p.isOnline()) bossBar.addPlayer(p);
        }

        game.sendActionMessage(this, "init", "action.boss_spawn", "<boss>", title);
    }

    @Override
    public void onTick(DungeonGame game) {
        if (completed || bossId == null || bossBar == null) return;

        Entity entity = Bukkit.getEntity(bossId);
        if (!(entity instanceof LivingEntity boss) || boss.isDead()) {
            completeBoss(game);
            return;
        }

        // Check Enrage Timer
        if (enrageTime > 0 && !isEnraged) {
            long elapsedSeconds = (System.currentTimeMillis() - spawnTimeMillis) / 1000;
            if (elapsedSeconds >= enrageTime) {
                isEnraged = true;

                // Broadcast Enrage Message
                if (enrageMessage != null && !enrageMessage.isEmpty()) {
                    for (Player p : game.getParticipants()) {
                        if (p.isOnline()) p.sendMessage(ColorUtils.parse(enrageMessage));
                    }
                }

                // Apply Enrage Stats
                applyAttributes(boss, enrageAttributes);
            }
        }

        AttributeInstance healthAttr = boss.getAttribute(Attribute.MAX_HEALTH);
        double maxHealth = healthAttr != null ? healthAttr.getValue() : 100.0;
        double progress = Math.max(0.0, Math.min(1.0, boss.getHealth() / maxHealth));
        bossBar.setProgress(progress);

        for (Player p : game.getParticipants()) {
            if (p.isOnline() && !bossBar.getPlayers().contains(p)) {
                bossBar.addPlayer(p);
            }
        }
    }

    @Override
    public void onEvent(DungeonGame game, Event event) {
        if (completed || bossId == null) return;

        if (event instanceof EntityDamageEvent e) {
            if (e.getEntity().getUniqueId().equals(bossId)) {
                LivingEntity boss = (LivingEntity) e.getEntity();
                AttributeInstance healthAttr = boss.getAttribute(Attribute.MAX_HEALTH);
                double maxHealth = healthAttr != null ? healthAttr.getValue() : 100.0;

                // Calculate health percentage AFTER this damage event
                double healthPct = ((boss.getHealth() - e.getFinalDamage()) / maxHealth) * 100.0;

                for (Map.Entry<Integer, PhaseData> phase : phases.entrySet()) {
                    int threshold = phase.getKey();
                    if (healthPct <= threshold && !executedPhases.contains(threshold)) {
                        executePhase(game, boss, phase.getValue());
                        executedPhases.add(threshold);
                    }
                }
            }
        } else if (event instanceof EntityDeathEvent e) {
            if (e.getEntity().getUniqueId().equals(bossId)) {
                completeBoss(game);
            }
        }
    }

    private void executePhase(DungeonGame game, LivingEntity boss, PhaseData data) {
        if (data.message != null && !data.message.isEmpty()) {
            for (Player p : game.getParticipants()) {
                if (p.isOnline()) p.sendMessage(ColorUtils.parse(data.message));
            }
        }

        applyAttributes(boss, data.attributes);

        // Execute Skills
        for (String skill : data.skills) {
            String[] parts = skill.split(":");
            String skillType = parts[0].toUpperCase();

            switch (skillType) {
                case "POTION_CLOUD":
                    if (parts.length >= 4) {
                        try {
                            PotionEffectType type = PotionEffectType.getByName(parts[1].toUpperCase());
                            int duration = Integer.parseInt(parts[2]) * 20;
                            int amp = Integer.parseInt(parts[3]);

                            AreaEffectCloud cloud = (AreaEffectCloud) game.getWorld().spawnEntity(boss.getLocation(), EntityType.AREA_EFFECT_CLOUD);
                            cloud.setRadius(5.0f);
                            cloud.setDuration(duration);
                            if (type != null) cloud.addCustomEffect(new PotionEffect(type, duration, amp), true);
                        } catch (Exception ignored) {}
                    }
                    break;
                case "LIGHTNING":
                    for (Player p : game.getParticipants()) {
                        if (p.isOnline() && !p.isDead()) {
                            game.getWorld().strikeLightning(p.getLocation());
                        }
                    }
                    break;
            }
        }

        // Spawn Reinforcements
        if (data.reinforcementMob != null) {
            for (int i = 0; i < data.reinforcementAmount; i++) {
                double offsetX = (Math.random() - 0.5) * 4.0;
                double offsetZ = (Math.random() - 0.5) * 4.0;
                Location rLoc = boss.getLocation().clone().add(offsetX, 0, offsetZ);

                Entity rEnt = game.getWorld().spawnEntity(rLoc, data.reinforcementMob);
                if (rEnt instanceof LivingEntity rLive) {
                    rLive.setRemoveWhenFarAway(false);
                    if (data.reinforcementName != null && !data.reinforcementName.isEmpty()) {
                        rLive.customName(ColorUtils.parse(data.reinforcementName));
                        rLive.setCustomNameVisible(true);
                    }
                    applyAttributes(rLive, data.reinforcementAttributes);
                    game.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, rLoc.add(0,1,0), 10, 0.2, 0.2, 0.2, 0.05);
                }
            }
        }
    }

    private void completeBoss(DungeonGame game) {
        this.completed = true;
        if (bossBar != null) {
            bossBar.removeAll();
            bossBar = null;
        }
        game.sendActionMessage(this, "complete", "action.boss_defeated");
    }

    @Override
    public void cleanup(DungeonGame game) {
        if (bossBar != null) {
            bossBar.removeAll();
            bossBar = null;
        }
        if (bossId != null) {
            Entity ent = Bukkit.getEntity(bossId);
            if (ent != null && !ent.isDead()) ent.remove();
        }
    }

    @SuppressWarnings("deprecation")
    private void applyAttributes(LivingEntity living, List<String> attributesList) {
        if (attributesList == null || attributesList.isEmpty()) return;
        for (String attrStr : attributesList) {
            String[] parts = attrStr.split(":", 2);
            if (parts.length < 2) continue;

            String attrName = parts[0].trim().toLowerCase(Locale.ROOT).replace("generic.", "");
            double value;
            try {
                value = Double.parseDouble(parts[1].trim());
            } catch (NumberFormatException e) { continue; }

            Attribute attribute = null;
            if (ServerVersion.isAtLeast(1, 21, 3)) {
                try {
                    NamespacedKey key = NamespacedKey.minecraft(attrName);
                    attribute = Registry.ATTRIBUTE.get(key);
                } catch (Throwable ignored) {}
            } else {
                try { attribute = Attribute.valueOf(attrName.toUpperCase(Locale.ROOT)); } catch (IllegalArgumentException ignored) {}
                if (attribute == null) {
                    try { attribute = Attribute.valueOf("GENERIC_" + attrName.toUpperCase(Locale.ROOT)); } catch (IllegalArgumentException ignored) {}
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

    public static class PhaseData {
        public String message = "";
        public List<String> attributes = new ArrayList<>();
        public List<String> skills = new ArrayList<>();

        public EntityType reinforcementMob = null;
        public int reinforcementAmount = 0;
        public String reinforcementName = "";
        public List<String> reinforcementAttributes = new ArrayList<>();
    }
}