package net.danh.sincedungeonpremium.listeners;

import net.danh.sinceDungeon.api.events.DungeonRewardClaimEvent;
import net.danh.sinceDungeon.guis.reward.RewardHolder;
import net.danh.sinceDungeon.models.DungeonReward;
import net.danh.sinceDungeon.utils.ItemBuilder;
import net.danh.sincedungeonpremium.SinceDungeonPremium;
import org.bukkit.Location;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

/**
 * Premium-Exclusive Listener: Advanced Loot & Roulette
 * Responsibilities:
 * - Intercepts core reward claiming to implement Holographic Ground Drops.
 * - Intercepts core Reward GUI opening to inject the Roulette Spin GUI system.
 */
public class PremiumRewardListener implements Listener {

    private final SinceDungeonPremium plugin;

    public PremiumRewardListener(SinceDungeonPremium plugin) {
        this.plugin = plugin;
    }

    /**
     * Replaces standard inventory injection with cinematic physical holographic drops.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onRewardClaim(DungeonRewardClaimEvent e) {
        boolean useHoloDrops = plugin.getFileManager().getConfig().getBoolean("hologram-drops.enabled", false);
        if (!useHoloDrops) return;

        DungeonReward reward = e.getReward();
        if (!reward.type().equalsIgnoreCase("ITEM") && !reward.type().equalsIgnoreCase("MMOITEM")) return;

        Player player = e.getPlayer();
        ItemStack itemStack = ItemBuilder.parseDynamicItem(reward.value());

        if (itemStack != null) {
            Location dropLoc = player.getLocation().add(0, 1, 0);
            Item droppedItem = player.getWorld().dropItem(dropLoc, itemStack);

            // Scatter velocity
            droppedItem.setVelocity(new Vector((Math.random() - 0.5) * 0.3, 0.5, (Math.random() - 0.5) * 0.3));
            droppedItem.setPickupDelay(40); // Prevent instant pickup
            droppedItem.setGlowing(true);

            // Display hologram-like text using vanilla custom names for ultra-stability
            String displayName = reward.displayName() != null ? reward.displayName() : itemStack.getType().name();
            droppedItem.setCustomName(net.danh.sinceDungeon.utils.ColorUtils.toPlainText(net.danh.sinceDungeon.utils.ColorUtils.parse(displayName)));
            droppedItem.setCustomNameVisible(true);

            // Cancel the event so the Core doesn't also give the item directly into the inventory
            e.setCancelled(true);
        }
    }

    /**
     * Intercepts the standard RewardGUI from the core to open the Roulette GUI.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent e) {
        if (!(e.getPlayer() instanceof Player player)) return;

        if (e.getInventory().getHolder() instanceof RewardHolder holder) {
            boolean useRoulette = plugin.getFileManager().getConfig().getBoolean("roulette.enabled", false);

            if (useRoulette) {
                e.setCancelled(true);
                // Delay execution by 1 tick to prevent inventory overlap conflicts
                org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                    plugin.getRouletteManager().openRoulette(player, holder.session());
                });
            }
        }
    }
}