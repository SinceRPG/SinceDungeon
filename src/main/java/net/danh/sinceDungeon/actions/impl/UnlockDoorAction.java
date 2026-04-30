package net.danh.sinceDungeon.actions.impl;

import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.actions.DungeonAction;
import net.danh.sinceDungeon.actions.Tickable;
import net.danh.sinceDungeon.hooks.MMOItemsHook;
import net.danh.sinceDungeon.models.DungeonGame;
import net.danh.sinceDungeon.utils.ColorUtils;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

public class UnlockDoorAction extends DungeonAction implements Tickable {
    private final Vector trigger;
    private final Vector c1;
    private final Vector c2;
    private final String keyItemData;

    private Location triggerLoc;
    private BukkitTask breakTask = null;
    private boolean isUnlocking = false;

    public UnlockDoorAction(Vector trigger, Vector c1, Vector c2, String keyItemData) {
        this.trigger = trigger;
        this.c1 = c1;
        this.c2 = c2;
        this.keyItemData = keyItemData;
    }

    @Override
    public String getObjectiveText() {
        return SinceDungeon.getPlugin().getMessagesFile().getString("objective.unlock_door", "<gold>Find the Key and Unlock the Door");
    }

    @Override
    public void start(DungeonGame game) {
        if (game.getWorld() == null) return;
        this.triggerLoc = new Location(game.getWorld(), trigger.getBlockX() + 0.5, trigger.getBlockY() + 0.5, trigger.getBlockZ() + 0.5);
        game.sendActionMessage(this, "init", "action.door_start");
    }

    @Override
    public void cleanup(DungeonGame game) {
        if (breakTask != null && !breakTask.isCancelled()) {
            breakTask.cancel();
        }
    }

    @Override
    public void onTick(DungeonGame game) {
        if (completed || triggerLoc == null) return;

        // Tạo hạt cho người chơi dễ nhận biết vị trí ổ khóa
        game.getWorld().spawnParticle(Particle.ENCHANT, triggerLoc, 5, 0.2, 0.2, 0.2, 0.1);

        // La bàn tự động chỉ đường tới cánh cửa cho toàn bộ người chơi
        for (Player p : game.getParticipants()) {
            if (p.isOnline() && p.getWorld().equals(game.getWorld())) {
                p.setCompassTarget(triggerLoc);
            }
        }
    }

    @Override
    public void onEvent(DungeonGame game, Event event) {
        if (event instanceof PlayerInteractEvent e) {
            if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;

            Player p = e.getPlayer();
            if (p.getGameMode() == GameMode.SPECTATOR) return;
            if (!game.getParticipants().contains(p)) return;
            if (!e.hasBlock() || isUnlocking) return;

            Block b = e.getClickedBlock();
            if (b != null && b.getWorld().equals(game.getWorld()) &&
                    b.getX() == trigger.getBlockX() &&
                    b.getY() == trigger.getBlockY() &&
                    b.getZ() == trigger.getBlockZ()) {

                ItemStack handItem = e.getItem();

                if (isMatchingKey(handItem)) {
                    // Trừ 1 chìa khóa
                    handItem.setAmount(handItem.getAmount() - 1);

                    isUnlocking = true;
                    b.setType(Material.AIR);
                    removeWall(game);

                    this.completed = true;
                    game.sendActionMessage(this, "complete", "action.door_unlocked", "<player>", p.getName());

                    // Reset lại la bàn của người chơi
                    Location spawnLoc = game.getWorld().getSpawnLocation();
                    game.getParticipants().forEach(player -> {
                        if (player.isOnline()) player.setCompassTarget(spawnLoc);
                    });
                } else {
                    String msg = SinceDungeon.getPlugin().getMessagesFile().getString("error.wrong_key", "<red>You don't have the correct key!");
                    p.sendMessage(ColorUtils.parseWithPrefix(msg));
                }
            }
        }
    }

    private boolean isMatchingKey(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;

        try {
            String cleanData = keyItemData.replace(" ", "");
            String[] parts = cleanData.split(":");

            if (parts.length >= 3 && parts[0].equalsIgnoreCase("MMOITEMS")) {
                String mType = parts[1];
                String mId = parts[2];
                String itemString = MMOItemsHook.getMMOItemString(item);
                return itemString != null && itemString.startsWith("MMOITEMS:" + mType + ":" + mId);
            } else {
                Material requiredMat = Material.matchMaterial(parts[0]);
                return item.getType() == requiredMat;
            }
        } catch (Exception e) {
            return false;
        }
    }

    private void removeWall(DungeonGame game) {
        int minX = Math.min(c1.getBlockX(), c2.getBlockX());
        int maxX = Math.max(c1.getBlockX(), c2.getBlockX());
        int minY = Math.min(c1.getBlockY(), c2.getBlockY());
        int maxY = Math.max(c1.getBlockY(), c2.getBlockY());
        int minZ = Math.min(c1.getBlockZ(), c2.getBlockZ());
        int maxZ = Math.max(c1.getBlockZ(), c2.getBlockZ());

        game.getWorld().playSound(triggerLoc, Sound.BLOCK_IRON_DOOR_OPEN, 1f, 0.5f);

        breakTask = new BukkitRunnable() {
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
                while (blocksProcessed < 50) { // Phá cửa chậm gọn nhẹ hơn phá tường
                    Block block = game.getWorld().getBlockAt(currentX, currentY, currentZ);
                    if (block.getType() != Material.AIR) {
                        game.getWorld().spawnParticle(Particle.BLOCK_CRUMBLE, block.getLocation().add(0.5, 0.5, 0.5), 5, 0.2, 0.2, 0.2, 0.05, block.getBlockData());
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