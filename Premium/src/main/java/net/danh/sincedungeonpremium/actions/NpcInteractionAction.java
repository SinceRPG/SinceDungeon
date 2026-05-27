package net.danh.sincedungeonpremium.actions;

import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.actions.DungeonAction;
import net.danh.sinceDungeon.actions.Tickable;
import net.danh.sinceDungeon.api.SinceDungeonAPI;
import net.danh.sinceDungeon.api.events.DungeonEndEvent;
import net.danh.sinceDungeon.managers.DungeonLoader;
import net.danh.sinceDungeon.models.DungeonGame;
import net.danh.sinceDungeon.utils.AttributeUtils;
import net.danh.sinceDungeon.utils.ColorUtils;
import net.danh.sinceDungeon.utils.ItemBuilder;
import net.danh.sinceDungeon.utils.SoundUtils;
import net.danh.sincedungeonpremium.SinceDungeonPremium;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.*;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.*;

/**
 * Premium Action: NPC Interaction
 * Provides one editor-friendly NPC action for dialogue, guide movement, hand-in items,
 * reward delivery, and click teleport without requiring admins to edit files manually.
 */
public class NpcInteractionAction extends DungeonAction implements Tickable {

    private final String entityTypeStr;
    private final String customName;
    private final String npcLocationStr;
    private final String targetLocationStr;
    private final String interactionModeStr;
    private final String messageScopeStr;
    private final String teleportScopeStr;
    private final double maxHealth;
    private final double moveSpeed;
    private final double successRadius;
    private final double interactionRadius;
    private final boolean startOnClick;
    private final boolean npcIsBaby;
    private final boolean consumeRequiredItem;
    private final boolean failOnNpcDeath;
    private final int clickCooldownTicks;
    private final List<String> dialogueLines;
    private final String requiredItemData;
    private final String rewardItemData;
    private final String rewardDisplayName;
    private final List<String> npcAttributes;
    private final List<String> npcEquipment;
    private final Map<UUID, Long> clickCooldowns = new HashMap<>();
    private LivingEntity cachedNpc;
    private Mob cachedMob;
    private Location targetLocation;
    private Location targetParticleLoc;
    private InteractionMode mode = InteractionMode.TALK;
    private TargetScope messageScope = TargetScope.PLAYER;
    private TargetScope teleportScope = TargetScope.PLAYER;
    private boolean movementStarted = false;
    private int ticks = 0;
    private Particle spawnParticle;
    private Particle targetParticle;
    private Sound interactSound;
    private Sound completeSound;
    private Sound denySound;

    public NpcInteractionAction(String entityTypeStr, String customName, String npcLocationStr, String targetLocationStr,
                                String interactionModeStr, String messageScopeStr, String teleportScopeStr,
                                double maxHealth, double moveSpeed, double successRadius, double interactionRadius,
                                boolean startOnClick, boolean npcIsBaby, boolean consumeRequiredItem,
                                boolean failOnNpcDeath, int clickCooldownTicks, List<String> dialogueLines,
                                String requiredItemData, String rewardItemData, String rewardDisplayName,
                                List<String> npcAttributes, List<String> npcEquipment) {
        this.entityTypeStr = entityTypeStr;
        this.customName = customName;
        this.npcLocationStr = npcLocationStr;
        this.targetLocationStr = targetLocationStr;
        this.interactionModeStr = interactionModeStr;
        this.messageScopeStr = messageScopeStr;
        this.teleportScopeStr = teleportScopeStr;
        this.maxHealth = maxHealth;
        this.moveSpeed = moveSpeed;
        this.successRadius = successRadius;
        this.interactionRadius = interactionRadius;
        this.startOnClick = startOnClick;
        this.npcIsBaby = npcIsBaby;
        this.consumeRequiredItem = consumeRequiredItem;
        this.failOnNpcDeath = failOnNpcDeath;
        this.clickCooldownTicks = clickCooldownTicks;
        this.dialogueLines = dialogueLines;
        this.requiredItemData = requiredItemData;
        this.rewardItemData = rewardItemData;
        this.rewardDisplayName = rewardDisplayName;
        this.npcAttributes = npcAttributes;
        this.npcEquipment = npcEquipment;
    }

    @Override
    public void start(DungeonGame game) {
        if (game.getWorld() == null) {
            forceComplete();
            return;
        }

        this.mode = parseEnum(InteractionMode.class, interactionModeStr, InteractionMode.TALK);
        this.messageScope = parseEnum(TargetScope.class, messageScopeStr, TargetScope.PLAYER);
        this.teleportScope = parseEnum(TargetScope.class, teleportScopeStr, TargetScope.PLAYER);
        this.spawnParticle = readParticle("particles.npc_spawn", Particle.HAPPY_VILLAGER);
        this.targetParticle = readParticle("particles.npc_target", Particle.END_ROD);
        this.interactSound = SoundUtils.getSound(SinceDungeonPremium.getInstance().getFileManager().getConfig().getString("sounds.npc_interact", "entity.villager.yes"));
        this.completeSound = SoundUtils.getSound(SinceDungeonPremium.getInstance().getFileManager().getConfig().getString("sounds.npc_complete", "entity.player.levelup"));
        this.denySound = SoundUtils.getSound(SinceDungeonPremium.getInstance().getFileManager().getConfig().getString("sounds.npc_deny", "entity.villager.no"));

        Vector npcVec = DungeonLoader.parseVector(npcLocationStr);
        Vector targetVec = DungeonLoader.parseVector(targetLocationStr);
        Location npcLocation = game.resolveLocation(npcVec, 0.5, 0, 0.5);
        this.targetLocation = game.resolveLocation(targetVec, 0.5, 0, 0.5);
        this.targetParticleLoc = targetLocation.clone().add(0, 1, 0);

        npcLocation.getChunk().load(true);
        targetLocation.getChunk().load(true);

        Entity entity = game.getWorld().spawnEntity(npcLocation, parseEntityType(entityTypeStr));
        if (!(entity instanceof LivingEntity living)) {
            entity.remove();
            forceComplete();
            return;
        }

        applyNpcProperties(living);
        cachedNpc = living;
        if (living instanceof Mob mob) {
            cachedMob = mob;
            cachedMob.setTarget(null);
            applyMovementSpeed(cachedMob);
        } else if (mode == InteractionMode.GUIDE) {
            entity.remove();
            log("log.npc_guide_requires_mob", "<type>", entityTypeStr);
            forceComplete();
            return;
        }

        spawnedEntities.add(living.getUniqueId());
        activeEntities.add(living);
        game.getWorld().spawnParticle(spawnParticle, npcLocation.clone().add(0, 1, 0), 20, 0.4, 0.7, 0.4, 0.02);
        game.sendActionMessage(this, "init", "action.npc_spawned");

        if (mode == InteractionMode.GUIDE && !startOnClick) {
            startMovement(game);
        }
    }

    @Override
    public void onTick(DungeonGame game) {
        if (completed || cachedNpc == null) return;
        ticks++;

        if (cachedNpc.isDead()) {
            handleNpcLost(game);
            return;
        }

        if (!cachedNpc.isValid()) {
            Location npcLocation = cachedNpc.getLocation();
            if (npcLocation.getWorld() != null && !npcLocation.isChunkLoaded()) {
                return;
            }
            handleNpcLost(game);
            return;
        }

        if (mode != InteractionMode.GUIDE || cachedMob == null || targetLocation == null) return;

        if (movementStarted) {
            if (ticks % 20 == 0) {
                cachedMob.getPathfinder().moveTo(targetLocation);
            }

            if (ticks % 10 == 0 && targetParticleLoc != null && targetParticleLoc.getWorld() != null) {
                targetParticleLoc.getWorld().spawnParticle(targetParticle, targetParticleLoc, 4, 0.2, 0.3, 0.2, 0.01);
            }

            if (cachedMob.getLocation().distanceSquared(targetLocation) <= successRadius * successRadius) {
                completeAction(game, "action.npc_guide_complete");
            }
        }
    }

    @Override
    public void onEvent(DungeonGame game, Event event) {
        if (completed || cachedNpc == null) return;

        if (event instanceof PlayerInteractEntityEvent e && isNpc(e.getRightClicked())) {
            e.setCancelled(true);
            handleClick(game, e.getPlayer());
            return;
        }

        if (event instanceof EntityDamageByEntityEvent e && isNpc(e.getEntity())) {
            Player attacker = getPlayerAttacker(e.getDamager());
            if (attacker != null) {
                e.setCancelled(true);
                handleClick(game, attacker);
            }
            return;
        }

        if (event instanceof EntityDeathEvent e && isNpc(e.getEntity())) {
            handleNpcLost(game);
        }
    }

    @Override
    public void cleanup(DungeonGame game) {
        super.cleanup(game);
        clickCooldowns.clear();
        cachedNpc = null;
        cachedMob = null;
        targetLocation = null;
        targetParticleLoc = null;
    }

    @Override
    public String getObjectiveText() {
        String base = SinceDungeon.getPlugin().getLanguageManager().getString("objective.npc_interaction", "<green>Interact with the NPC");
        return base.replace("<mode>", mode.name());
    }

    private void handleClick(DungeonGame game, Player player) {
        if (player == null || !player.isOnline() || player.isDead() || player.getGameMode() == GameMode.SPECTATOR)
            return;
        if (!game.getParticipants().contains(player) || !player.getWorld().equals(game.getWorld())) return;
        if (cachedNpc == null || player.getLocation().distanceSquared(cachedNpc.getLocation()) > interactionRadius * interactionRadius)
            return;
        if (isOnCooldown(player)) return;

        playSound(player, interactSound);
        sendDialogue(game, player);

        switch (mode) {
            case TALK -> completeAction(game, "action.npc_talk_complete");
            case GUIDE -> {
                if (!movementStarted) {
                    startMovement(game);
                } else {
                    game.sendActionMessage(this, "progress", "action.npc_already_moving");
                }
            }
            case GIVE_ITEM -> handleGiveItem(game, player);
            case TELEPORT -> handleTeleport(game, player);
        }
    }

    private boolean isOnCooldown(Player player) {
        long now = System.currentTimeMillis();
        long until = clickCooldowns.getOrDefault(player.getUniqueId(), 0L);
        if (now < until) return true;

        long cooldownMillis = Math.max(0, clickCooldownTicks) * 50L;
        if (cooldownMillis > 0) {
            clickCooldowns.put(player.getUniqueId(), now + cooldownMillis);
        }
        return false;
    }

    private void startMovement(DungeonGame game) {
        if (cachedMob == null || targetLocation == null) return;
        movementStarted = true;
        cachedMob.getPathfinder().moveTo(targetLocation);
        game.sendActionMessage(this, "progress", "action.npc_guide_start");
    }

    private void handleGiveItem(DungeonGame game, Player player) {
        ItemStack required = parseOptionalItem(requiredItemData);
        if (required != null) {
            if (!hasItem(player, required)) {
                String msg = SinceDungeon.getPlugin().getLanguageManager().getString("error.npc_missing_item", "&cYou do not have the required item.");
                player.sendMessage(ColorUtils.parseWithPrefix(msg));
                playSound(player, denySound);
                return;
            }
            if (consumeRequiredItem) {
                removeItem(player, required);
            }
        }

        ItemStack reward = parseOptionalItem(rewardItemData);
        if (reward != null) {
            SinceDungeonAPI.get().giveItemSafely(player, reward, rewardDisplayName);
        }
        completeAction(game, "action.npc_item_complete");
    }

    private void handleTeleport(DungeonGame game, Player clickedPlayer) {
        if (targetLocation == null) return;

        if (teleportScope == TargetScope.PARTY) {
            for (Player participant : game.getParticipants()) {
                if (participant.isOnline() && !participant.isDead()) {
                    participant.teleportAsync(targetLocation);
                }
            }
        } else {
            clickedPlayer.teleportAsync(targetLocation);
        }

        completeAction(game, "action.npc_teleport_complete");
    }

    private void sendDialogue(DungeonGame game, Player clickedPlayer) {
        if (dialogueLines == null || dialogueLines.isEmpty()) return;

        if (messageScope == TargetScope.PARTY) {
            for (Player participant : game.getParticipants()) {
                if (participant.isOnline() && participant.getWorld().equals(game.getWorld())) {
                    sendDialogueTo(participant, clickedPlayer);
                }
            }
        } else {
            sendDialogueTo(clickedPlayer, clickedPlayer);
        }
    }

    private void sendDialogueTo(Player receiver, Player clickedPlayer) {
        for (String line : dialogueLines) {
            receiver.sendMessage(ColorUtils.parseWithPrefix(line
                    .replace("<player>", clickedPlayer.getName())
                    .replace("<npc>", customName == null ? entityTypeStr : customName)));
        }
    }

    private void completeAction(DungeonGame game, String messageKey) {
        if (completed) return;
        completed = true;

        if (completeSound != null) {
            for (Player participant : game.getParticipants()) {
                if (participant.isOnline()) {
                    participant.playSound(participant.getLocation(), completeSound, 1.0f, 1.0f);
                }
            }
        }

        game.sendActionMessage(this, "complete", messageKey);
    }

    private void handleNpcLost(DungeonGame game) {
        if (completed) return;
        if (failOnNpcDeath) {
            game.sendActionMessage(this, "warning", "action.npc_failed");
            game.stop(true, DungeonEndEvent.EndReason.FAILED);
        } else {
            completeAction(game, "action.npc_talk_complete");
        }
    }

    private boolean isNpc(Entity entity) {
        return cachedNpc != null && entity != null && entity.getUniqueId().equals(cachedNpc.getUniqueId());
    }

    private Player getPlayerAttacker(Entity damager) {
        if (damager instanceof Player player) return player;
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) return player;
        return null;
    }

    private EntityType parseEntityType(String value) {
        try {
            return EntityType.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            log("log.npc_invalid_entity", "<type>", String.valueOf(value));
            return EntityType.VILLAGER;
        }
    }

    private void applyNpcProperties(LivingEntity living) {
        living.setRemoveWhenFarAway(false);
        living.setPersistent(true);

        if (customName != null && !customName.trim().isEmpty()) {
            living.customName(ColorUtils.parse(customName));
            living.setCustomNameVisible(true);
        }

        if (npcIsBaby && living instanceof Ageable ageable) {
            ageable.setBaby();
        } else if (npcIsBaby && living instanceof Zombie zombie) {
            zombie.setBaby();
        }

        AttributeInstance healthAttr = living.getAttribute(Attribute.MAX_HEALTH);
        if (healthAttr != null && maxHealth > 0) {
            healthAttr.setBaseValue(maxHealth);
            living.setHealth(Math.min(maxHealth, healthAttr.getValue()));
        }

        applyAttributes(living);
        applyEquipment(living);
    }

    private void applyMovementSpeed(Mob mob) {
        AttributeInstance speedAttr = mob.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speedAttr != null && moveSpeed > 0) {
            speedAttr.setBaseValue(speedAttr.getBaseValue() * moveSpeed);
        }
    }

    private void applyAttributes(LivingEntity living) {
        if (npcAttributes == null || npcAttributes.isEmpty()) return;

        for (String attrStr : npcAttributes) {
            String[] parts = attrStr.split(":", 2);
            if (parts.length < 2) continue;

            String attrName = parts[0].trim().toLowerCase(Locale.ROOT).replace("generic.", "");
            double value;
            try {
                value = Double.parseDouble(parts[1].trim());
            } catch (NumberFormatException e) {
                continue;
            }

            Attribute attribute = resolveAttribute(attrName);
            if (attribute == null) continue;

            AttributeInstance instance = living.getAttribute(attribute);
            if (instance != null) {
                instance.setBaseValue(value);
                if (attrName.equals("max_health")) {
                    living.setHealth(Math.min(value, instance.getValue()));
                }
            }
        }
    }

    private Attribute resolveAttribute(String attrName) {
        return AttributeUtils.resolve(attrName);
    }

    private void applyEquipment(LivingEntity living) {
        if (npcEquipment == null || npcEquipment.isEmpty() || living.getEquipment() == null) return;

        living.getEquipment().setHelmetDropChance(0f);
        living.getEquipment().setChestplateDropChance(0f);
        living.getEquipment().setLeggingsDropChance(0f);
        living.getEquipment().setBootsDropChance(0f);
        living.getEquipment().setItemInMainHandDropChance(0f);
        living.getEquipment().setItemInOffHandDropChance(0f);

        for (String equipStr : npcEquipment) {
            String[] parts = equipStr.split(":", 2);
            if (parts.length < 2) continue;

            ItemStack item = ItemBuilder.parseDynamicItem(parts[1].trim());
            if (item == null) continue;

            switch (parts[0].trim().toLowerCase(Locale.ROOT)) {
                case "helmet", "head" -> living.getEquipment().setHelmet(item);
                case "chestplate", "chest" -> living.getEquipment().setChestplate(item);
                case "leggings", "legs" -> living.getEquipment().setLeggings(item);
                case "boots", "feet" -> living.getEquipment().setBoots(item);
                case "mainhand", "hand" -> living.getEquipment().setItemInMainHand(item);
                case "offhand", "shield" -> living.getEquipment().setItemInOffHand(item);
            }
        }
    }

    private ItemStack parseOptionalItem(String data) {
        if (data == null || data.trim().isEmpty() || data.equalsIgnoreCase("NONE")) return null;
        return ItemBuilder.parseDynamicItem(data);
    }

    private boolean hasItem(Player player, ItemStack required) {
        int remaining = Math.max(1, required.getAmount());
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.isSimilar(required)) {
                remaining -= item.getAmount();
                if (remaining <= 0) return true;
            }
        }
        return false;
    }

    private void removeItem(Player player, ItemStack required) {
        int remaining = Math.max(1, required.getAmount());
        ItemStack[] contents = player.getInventory().getContents();

        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack item = contents[i];
            if (item == null || !item.isSimilar(required)) continue;

            int take = Math.min(remaining, item.getAmount());
            item.setAmount(item.getAmount() - take);
            remaining -= take;
            if (item.getAmount() <= 0) {
                player.getInventory().setItem(i, null);
            }
        }
    }

    private Particle readParticle(String path, Particle fallback) {
        String value = SinceDungeonPremium.getInstance().getFileManager().getConfig().getString(path, fallback.name());
        try {
            return Particle.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private void playSound(Player player, Sound sound) {
        if (sound != null) {
            player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
        }
    }

    private void log(String path, String... placeholders) {
        String message = SinceDungeonPremium.getInstance().getFileManager().getMessageRaw(path);
        for (int i = 0; i < placeholders.length; i += 2) {
            message = message.replace(placeholders[i], i + 1 < placeholders.length ? placeholders[i + 1] : "");
        }
        SinceDungeonPremium.getInstance().getLogger().warning(message);
    }

    private <T extends Enum<T>> T parseEnum(Class<T> enumClass, String value, T fallback) {
        try {
            return Enum.valueOf(enumClass, value.toUpperCase(Locale.ROOT));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private enum InteractionMode {
        TALK,
        GUIDE,
        GIVE_ITEM,
        TELEPORT
    }

    private enum TargetScope {
        PLAYER,
        PARTY
    }
}
