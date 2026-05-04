package net.danh.sinceDungeon.actions.impl;

import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.actions.DungeonAction;
import net.danh.sinceDungeon.actions.Tickable;
import net.danh.sinceDungeon.hooks.MMOItemsHook;
import net.danh.sinceDungeon.models.DungeonGame;
import net.danh.sinceDungeon.utils.ColorUtils;
import net.danh.sinceDungeon.utils.ItemBuilder;
import net.danh.sinceDungeon.utils.ServerVersion;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.*;

/**
 * Spawns a customizable Boss Entity.
 * Features: Health Scaling, BossBar, Multi-Phases, Enrage Timer, Custom Equipment, and Custom Drops.
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
    private final List<String> baseEquipment;
    private final Map<Integer, PhaseData> phases;

    private final int enrageTime;
    private final String enrageMessage;
    private final List<String> enrageAttributes;
    private final List<String> customDrops;
    private final Set<Integer> executedPhases = new HashSet<>();
    private UUID bossId = null;
    private BossBar bossBar = null;
    private long spawnTimeMillis = 0;
    private boolean isEnraged = false;

    public BossBattleAction(Vector spawnLoc, EntityType mobType, String customName, double baseHealth,
                            double scaleHealthPerPlayer, String barColor, String barStyle,
                            List<String> baseAttributes, List<String> baseEquipment,
                            Map<Integer, PhaseData> phases, int enrageTime, String enrageMessage,
                            List<String> enrageAttributes, List<String> customDrops) {
        this.spawnLoc = spawnLoc;
        this.mobType = mobType;
        this.customName = customName;
        this.baseHealth = baseHealth;
        this.scaleHealthPerPlayer = scaleHealthPerPlayer;
        this.barColor = barColor;
        this.barStyle = barStyle;
        this.baseAttributes = baseAttributes;
        this.baseEquipment = baseEquipment;
        this.phases = phases;
        this.enrageTime = enrageTime;
        this.enrageMessage = enrageMessage;
        this.enrageAttributes = enrageAttributes;
        this.customDrops = customDrops;
    }

    @Override
    public String getObjectiveText() {
        return SinceDungeon.getPlugin().getLanguageManager().getString("objective.boss_battle", "<red>Defeat the Boss!");
    }

    @Override
    public void start(DungeonGame game) {
        Location loc = new Location(game.getWorld(), spawnLoc.getX() + 0.5, spawnLoc.getY(), spawnLoc.getZ() + 0.5);
        loc.getChunk().load(true);

        Entity entity = game.getWorld().spawnEntity(loc, mobType);
        if (!(entity instanceof LivingEntity)) {
            entity.remove();
            this.completed = true;
            return;
        }

        LivingEntity boss = (LivingEntity) entity;

        boss.setRemoveWhenFarAway(false);
        boss.setPersistent(true);
        if (customName != null && !customName.isEmpty()) {
            boss.customName(ColorUtils.parse(customName));
            boss.setCustomNameVisible(true);
        }

        this.bossId = boss.getUniqueId();
        this.spawnedEntities.add(this.bossId);
        this.spawnTimeMillis = System.currentTimeMillis();

        double totalHealth = baseHealth + (scaleHealthPerPlayer * Math.max(0, game.getParticipants().size() - 1));
        AttributeInstance healthAttr = boss.getAttribute(Attribute.MAX_HEALTH);
        if (healthAttr != null) {
            healthAttr.setBaseValue(totalHealth);
            boss.setHealth(totalHealth);
        }

        applyAttributes(boss, baseAttributes);
        applyEquipment(boss, baseEquipment);

        BarColor color = BarColor.RED;
        try {
            color = BarColor.valueOf(barColor.toUpperCase());
        } catch (Exception ignored) {
        }

        BarStyle style = BarStyle.SOLID;
        try {
            style = BarStyle.valueOf(barStyle.toUpperCase());
        } catch (Exception ignored) {
        }

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
        if (!(entity instanceof LivingEntity) || entity.isDead()) {
            handleCustomDrops(entity != null ? entity.getLocation() : null);
            completeBoss(game);
            return;
        }
        LivingEntity boss = (LivingEntity) entity;

        if (enrageTime > 0 && !isEnraged) {
            long elapsedSeconds = (System.currentTimeMillis() - spawnTimeMillis) / 1000;
            if (elapsedSeconds >= enrageTime) {
                isEnraged = true;
                if (enrageMessage != null && !enrageMessage.isEmpty()) {
                    for (Player p : game.getParticipants()) {
                        if (p.isOnline()) p.sendMessage(ColorUtils.parse(enrageMessage));
                    }
                }
                applyAttributes(boss, enrageAttributes);
            }
        }

        AttributeInstance healthAttr = boss.getAttribute(Attribute.MAX_HEALTH);
        double maxHealth = healthAttr != null ? healthAttr.getValue() : 100.0;
        double progress = Math.max(0.0, Math.min(1.0, boss.getHealth() / maxHealth));
        bossBar.setProgress(progress);
    }

    @Override
    public void onEvent(DungeonGame game, Event event) {
        if (completed || bossId == null) return;

        if (event instanceof EntityDamageEvent e) {
            if (e.getEntity().getUniqueId().equals(bossId)) {
                LivingEntity boss = (LivingEntity) e.getEntity();
                AttributeInstance healthAttr = boss.getAttribute(Attribute.MAX_HEALTH);
                double maxHealth = healthAttr != null ? healthAttr.getValue() : 100.0;
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
                handleCustomDrops(e.getEntity().getLocation());
                cleanup(game); // Clean up all reinforcements on boss death
                completeBoss(game);
            }
        }
    }

    @Override
    public void cleanup(DungeonGame game) {
        super.cleanup(game);
        // Clears the BossBar from players' screens if the action is forcefully terminated
        if (this.bossBar != null) {
            this.bossBar.removeAll();
            this.bossBar = null;
        }
    }

    private void executePhase(DungeonGame game, LivingEntity boss, PhaseData data) {
        if (data.message != null && !data.message.isEmpty()) {
            for (Player p : game.getParticipants()) {
                if (p.isOnline()) p.sendMessage(ColorUtils.parse(data.message));
            }
        }

        applyAttributes(boss, data.attributes);

        if (data.reinforcementMob != null) {
            for (int i = 0; i < data.reinforcementAmount; i++) {
                double offsetX = (Math.random() - 0.5) * 4.0;
                double offsetZ = (Math.random() - 0.5) * 4.0;
                Location rLoc = boss.getLocation().clone().add(offsetX, 0, offsetZ);

                Entity rEnt = game.getWorld().spawnEntity(rLoc, data.reinforcementMob);
                if (rEnt instanceof LivingEntity rLive) {
                    this.spawnedEntities.add(rLive.getUniqueId()); // Track reinforcement for cleanup
                    rLive.setRemoveWhenFarAway(false);
                    if (data.reinforcementName != null && !data.reinforcementName.isEmpty()) {
                        rLive.customName(ColorUtils.parse(data.reinforcementName));
                        rLive.setCustomNameVisible(true);
                    }
                    applyAttributes(rLive, data.reinforcementAttributes);
                    applyEquipment(rLive, data.reinforcementEquipment);
                    game.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, rLoc.add(0, 1, 0), 10, 0.2, 0.2, 0.2, 0.05);
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

    private void handleCustomDrops(Location loc) {
        if (loc == null || customDrops == null || customDrops.isEmpty()) return;
        for (String dropStr : customDrops) {
            try {
                String[] split = dropStr.split(";");
                if (split.length < 2) continue;
                String itemData = split[0].trim();
                double chance = Double.parseDouble(split[1].trim());

                if (Math.random() * 100.0 <= chance) {
                    ItemStack item = ItemBuilder.parseDynamicItem(itemData);
                    if (item != null) loc.getWorld().dropItemNaturally(loc, item);
                }
            } catch (Exception ignored) {
            }
        }
    }

    private void applyAttributes(LivingEntity living, List<String> attributesList) {
        if (attributesList == null || attributesList.isEmpty()) return;
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
                try {
                    attribute = Attribute.valueOf(attrName.toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ignored) {
                }
                if (attribute == null) {
                    try {
                        attribute = Attribute.valueOf("GENERIC_" + attrName.toUpperCase(Locale.ROOT));
                    } catch (IllegalArgumentException ignored) {
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

    private void applyEquipment(LivingEntity living, List<String> equipmentList) {
        if (equipmentList == null || equipmentList.isEmpty() || living.getEquipment() == null) return;

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
            ItemStack item = parseItem(itemData);

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

    private ItemStack parseItem(String data) {
        try {
            String cleanData = data.replace(" ", "");
            String[] parts = cleanData.split(":");
            if (parts.length >= 3 && parts[0].equalsIgnoreCase("MMOITEMS")) {
                if (Bukkit.getPluginManager().isPluginEnabled("MMOItems")) {
                    int amount = parts.length > 3 ? Integer.parseInt(parts[3]) : 1;
                    return MMOItemsHook.getMMOItem(parts[1], parts[2], amount);
                }
            } else {
                Material mat = Material.matchMaterial(parts[0]);
                if (mat != null) {
                    int amount = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;
                    return new ItemStack(mat, amount);
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    public static class PhaseData {
        public String message = "";
        public List<String> attributes = new ArrayList<>();
        public EntityType reinforcementMob = null;
        public int reinforcementAmount = 0;
        public String reinforcementName = "";
        public List<String> reinforcementAttributes = new ArrayList<>();
        public List<String> reinforcementEquipment = new ArrayList<>();
    }
}