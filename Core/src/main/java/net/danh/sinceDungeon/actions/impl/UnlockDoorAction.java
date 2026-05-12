package net.danh.sinceDungeon.actions.impl;

import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.actions.DungeonAction;
import net.danh.sinceDungeon.actions.Tickable;
import net.danh.sinceDungeon.models.DungeonGame;
import net.danh.sinceDungeon.utils.ColorUtils;
import net.danh.sinceDungeon.utils.ItemBuilder;
import net.danh.sinceDungeon.utils.SoundUtils;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Locale;

/**
 * Requires players to find a specific generated Dungeon Key and use it
 * to unlock a defined trigger block. Provides tracking compasses dynamically.
 */
public class UnlockDoorAction extends DungeonAction implements Tickable {
    private final Vector trigger;
    private final Vector c1;
    private final Vector c2;
    private final String keyId;
    private final Particle lockedParticle;

    private Location triggerLoc;
    private Location triggerBlockLoc;
    private BukkitTask breakTask = null;
    private boolean isUnlocking = false;

    public UnlockDoorAction(Vector trigger, Vector c1, Vector c2, String keyId, String particleName) {
        this.trigger = trigger;
        this.c1 = c1;
        this.c2 = c2;
        this.keyId = keyId;

        Particle p = Particle.ENCHANT;
        try {
            if (particleName != null && !particleName.equalsIgnoreCase("NONE")) {
                p = Particle.valueOf(particleName.toUpperCase(Locale.ROOT));
            }
        } catch (IllegalArgumentException ignored) {
        }
        this.lockedParticle = p;
    }

    @Override
    public String getObjectiveText() {
        return SinceDungeon.getPlugin().getLanguageManager().getString("objective.unlock_door", "<gold>Find the Key and Unlock the Door");
    }

    @Override
    public void start(DungeonGame game) {
        if (game.getWorld() == null) return;
        this.triggerBlockLoc = game.resolveBlockLocation(trigger);
        this.triggerLoc = triggerBlockLoc.clone().add(0.5, 0.5, 0.5);

        NamespacedKey compassTag = new NamespacedKey(SinceDungeon.getPlugin(), "dungeon_compass");
        ConfigurationSection cfg = SinceDungeon.getPlugin().getConfigFile().getSection("items.compass");

        ItemStack compass = ItemBuilder.fromConfig(SinceDungeon.getPlugin(), "items.compass", "COMPASS")
                .applyConfig(cfg, "&b&lTracking Compass")
                .setTag(compassTag, PersistentDataType.BYTE, (byte) 1)
                .build();

        String compassMsg = SinceDungeon.getPlugin().getLanguageManager().getString("action.compass_received", "&bYou received a Tracking Compass!");
        String soundPickup = SinceDungeon.getPlugin().getConfigFile().getString("sounds.compass_receive", "entity.item.pickup");

        for (Player p : game.getParticipants()) {
            if (p.isOnline() && !p.isDead()) {
                HashMap<Integer, ItemStack> leftover = p.getInventory().addItem(compass.clone());
                if (!leftover.isEmpty()) {
                    for (ItemStack drop : leftover.values()) {
                        p.getWorld().dropItem(p.getLocation(), drop);
                    }
                }

                p.setCompassTarget(triggerLoc);
                p.sendMessage(ColorUtils.parseWithPrefix(compassMsg));
                if (soundPickup != null) {
                    p.playSound(p.getLocation(), SoundUtils.getSound(soundPickup), 1f, 1f);
                }
            }
        }

        game.sendActionMessage(this, "init", "action.door_start");
    }

    @Override
    public void cleanup(DungeonGame game) {
        super.cleanup(game);
        if (breakTask != null && !breakTask.isCancelled()) {
            breakTask.cancel();
        }
        triggerLoc = null;
        triggerBlockLoc = null;
        removeCompasses(game);
    }

    @Override
    public void onTick(DungeonGame game) {
        if (completed || triggerLoc == null) return;

        if (lockedParticle != null) {
            game.getWorld().spawnParticle(lockedParticle, triggerLoc, 5, 0.2, 0.2, 0.2, 0.1);
        }

        for (Player p : game.getParticipants()) {
            if (p.isOnline() && p.getWorld().equals(game.getWorld())) {
                p.setCompassTarget(triggerLoc);
            }
        }
    }

    @Override
    public void onEvent(DungeonGame game, Event event) {
        if (event instanceof PlayerInteractEvent e) {
            if (e.getItem() != null) {
                NamespacedKey compassTag = new NamespacedKey(SinceDungeon.getPlugin(), "dungeon_compass");
                if (ItemBuilder.hasTag(e.getItem(), compassTag, PersistentDataType.BYTE)) {
                    e.setCancelled(true);
                }
            }

            if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;

            Player p = e.getPlayer();
            if (p.getGameMode() == GameMode.SPECTATOR) return;
            if (!game.getParticipants().contains(p)) return;
            if (!e.hasBlock() || isUnlocking) return;

            Block b = e.getClickedBlock();
            if (b != null && triggerBlockLoc != null && b.getWorld().equals(game.getWorld()) &&
                    b.getX() == triggerBlockLoc.getBlockX() &&
                    b.getY() == triggerBlockLoc.getBlockY() &&
                    b.getZ() == triggerBlockLoc.getBlockZ()) {

                e.setCancelled(true);
                ItemStack handItem = e.getItem();

                if (handItem == null || handItem.getType() == Material.AIR) {
                    String msg = SinceDungeon.getPlugin().getLanguageManager().getString("error.key_not_found", "&cYou need a Key!");
                    p.sendMessage(ColorUtils.parseWithPrefix(msg));
                    playDenySound(p);
                    return;
                }

                NamespacedKey keyTag = new NamespacedKey(SinceDungeon.getPlugin(), "dungeon_key_id");

                if (ItemBuilder.hasTag(handItem, keyTag, PersistentDataType.STRING) &&
                        this.keyId.equals(ItemBuilder.getTag(handItem, keyTag, PersistentDataType.STRING))) {

                    handItem.setAmount(handItem.getAmount() - 1);

                    isUnlocking = true;
                    b.setType(Material.AIR);
                    removeWall(game);
                    removeCompasses(game);

                    this.completed = true;
                    game.sendActionMessage(this, "complete", "action.door_unlocked", "<player>", p.getName());

                    Location spawnLoc = game.getRespawnLocation();
                    game.getParticipants().forEach(player -> {
                        if (player.isOnline()) player.setCompassTarget(spawnLoc);
                    });
                } else {
                    String msg = SinceDungeon.getPlugin().getLanguageManager().getString("error.wrong_key", "&cWrong key!");
                    p.sendMessage(ColorUtils.parseWithPrefix(msg));
                    playDenySound(p);
                }
            }
        }
    }

    private void playDenySound(Player p) {
        String soundLocked = SinceDungeon.getPlugin().getConfigFile().getString("sounds.door_locked", "block.chest.locked");
        if (soundLocked != null) {
            p.playSound(p.getLocation(), SoundUtils.getSound(soundLocked), 1f, 1f);
        }
    }

    private void removeCompasses(DungeonGame game) {
        NamespacedKey compassTag = new NamespacedKey(SinceDungeon.getPlugin(), "dungeon_compass");
        String msg = SinceDungeon.getPlugin().getLanguageManager().getString("action.compass_removed", "&7The Tracking Compass faded away.");

        for (Player p : game.getParticipants()) {
            if (!p.isOnline()) continue;
            boolean removed = false;
            for (ItemStack item : p.getInventory().getContents()) {
                if (ItemBuilder.hasTag(item, compassTag, PersistentDataType.BYTE)) {
                    item.setAmount(0);
                    removed = true;
                }
            }
            if (removed) {
                p.sendMessage(ColorUtils.parseWithPrefix(msg));
            }
        }
    }

    private void removeWall(DungeonGame game) {
        Location cornerA = game.resolveBlockLocation(c1);
        Location cornerB = game.resolveBlockLocation(c2);
        int minX = Math.min(cornerA.getBlockX(), cornerB.getBlockX());
        int maxX = Math.max(cornerA.getBlockX(), cornerB.getBlockX());
        int minY = Math.min(cornerA.getBlockY(), cornerB.getBlockY());
        int maxY = Math.max(cornerA.getBlockY(), cornerB.getBlockY());
        int minZ = Math.min(cornerA.getBlockZ(), cornerB.getBlockZ());
        int maxZ = Math.max(cornerA.getBlockZ(), cornerB.getBlockZ());

        String soundUnlock = SinceDungeon.getPlugin().getConfigFile().getString("sounds.door_unlock", "block.iron_door.open");
        if (soundUnlock != null) {
            game.getWorld().playSound(triggerLoc, SoundUtils.getSound(soundUnlock), 1f, 0.5f);
        }

        breakTask = new BukkitRunnable() {
            // JIT Optimization: Reusing the same Location object pointer to prevent massive GC allocations per tick
            final Location particleLoc = new Location(game.getWorld(), 0, 0, 0);
            int currentX = minX;
            int currentY = minY;
            int currentZ = minZ;

            @Override
            public void run() {
                if (game.getWorld() == null || !game.isRunning()) {
                    cancel();
                    return;
                }

                int blocksProcessed = 0;
                while (blocksProcessed < 50) {
                    Block block = game.getWorld().getBlockAt(currentX, currentY, currentZ);
                    if (block.getType() != Material.AIR) {
                        particleLoc.set(currentX + 0.5, currentY + 0.5, currentZ + 0.5);
                        game.getWorld().spawnParticle(Particle.BLOCK_CRUMBLE, particleLoc, 5, 0.2, 0.2, 0.2, 0.05, block.getBlockData());
                        block.setType(Material.AIR, false);
                    }
                    blocksProcessed++;

                    currentX++;
                    if (currentX > maxX) {
                        currentX = minX;
                        currentY++;
                        if (currentY > maxY) {
                            currentY = minY;
                            currentZ++;
                            if (currentZ > maxZ) {
                                cancel();
                                return;
                            }
                        }
                    }
                }
            }
        }.runTaskTimer(SinceDungeon.getPlugin(), 0L, 1L);
    }
}
