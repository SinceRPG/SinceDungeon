package net.danh.sincedungeonpremium.listeners;

import net.danh.sinceDungeon.api.events.DungeonRewardClaimEvent;
import net.danh.sinceDungeon.guis.reward.RewardHolder;
import net.danh.sinceDungeon.models.DungeonReward;
import net.danh.sinceDungeon.utils.ColorUtils;
import net.danh.sinceDungeon.utils.ItemBuilder;
import net.danh.sincedungeonpremium.SinceDungeonPremium;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * Premium-Exclusive Listener: Advanced Loot & Roulette
 * Responsibilities:
 * - Intercepts core reward claiming to implement Holographic Ground Drops.
 * - Parses display names into Adventure Components securely for custom nametags.
 * - Intercepts core Reward GUI opening to inject the Roulette Spin GUI system.
 */
public class PremiumRewardListener implements Listener {

    private final SinceDungeonPremium plugin;

    public PremiumRewardListener(SinceDungeonPremium plugin) {
        this.plugin = plugin;
    }

    /**
     * Replaces standard inventory injection with cinematic physical holographic drops.
     * Applies strict ownership mechanics to prevent other players from stealing the loot,
     * and securely modifies the ItemStack's internal meta so the display name and Lore
     * are retained when the item is picked up into the inventory.
     *
     * @param e The DungeonRewardClaimEvent containing the generated reward data.
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
            ItemMeta meta = itemStack.getItemMeta();
            String displayName = reward.displayName() != null && !reward.displayName().isEmpty() ? reward.displayName() : ColorUtils.formatEnumName(itemStack.getType().name());

            // Apply Lore and DisplayName to the ItemStack metadata directly
            if (meta != null) {
                meta.displayName(ColorUtils.parse("<!i>" + displayName));

                if (reward.lore() != null && !reward.lore().isEmpty()) {
                    List<Component> lore = new ArrayList<>();
                    for (String line : reward.lore()) {
                        lore.add(ColorUtils.parse("<!i>" + line));
                    }
                    meta.lore(lore);
                }
                itemStack.setItemMeta(meta);
            }

            Location dropLoc = player.getLocation().add(0, 1, 0);
            Item droppedItem = player.getWorld().dropItem(dropLoc, itemStack);

            droppedItem.setVelocity(new Vector((Math.random() - 0.5) * 0.3, 0.5, (Math.random() - 0.5) * 0.3));
            droppedItem.setPickupDelay(40);
            droppedItem.setGlowing(true);

            // Set the owner to strictly restrict pickup permissions to the winning player
            droppedItem.setOwner(player.getUniqueId());

            // Prevent entity mobs (zombies, foxes) from stealing the reward item
            droppedItem.setCanMobPickup(false);

            // Render the floating text nametag above the physical item entity
            Component nameComp = ColorUtils.parse(displayName);
            droppedItem.customName(nameComp);
            droppedItem.setCustomNameVisible(true);

            e.setCancelled(true);
        }
    }

    /**
     * Intercepts the standard RewardGUI from the core to open the Roulette GUI.
     * Replaced inline Bukkit invocation with proper imported class logic.
     *
     * @param e The InventoryOpenEvent.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent e) {
        if (!(e.getPlayer() instanceof Player player)) return;

        if (e.getInventory().getHolder() instanceof RewardHolder holder) {
            boolean useRoulette = plugin.getFileManager().getConfig().getBoolean("roulette.enabled", false);

            if (useRoulette) {
                e.setCancelled(true);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    plugin.getRouletteManager().openRoulette(player, holder.session());
                });
            }
        }
    }
}