package net.danh.sincedungeonpremium.listeners;

import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.api.events.DungeonRewardClaimEvent;
import net.danh.sinceDungeon.models.DungeonReward;
import net.danh.sinceDungeon.utils.ColorUtils;
import net.danh.sinceDungeon.utils.ItemBuilder;
import net.danh.sincedungeonpremium.SinceDungeonPremium;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * Premium-Exclusive Listener: Advanced Loot Drops
 * Responsibilities:
 * - Intercepts core reward claiming to implement Holographic Ground Drops.
 * - Parses display names into Adventure Components securely for custom nametags.
 * Note: Inventory Open interception has been safely migrated to RouletteRewardSystem via API.
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

        // Correctly handle standard items, MMOItems, and MythicItems now
        if (!reward.type().equalsIgnoreCase("ITEM") && !reward.type().equalsIgnoreCase("MMOITEM") && !reward.type().equalsIgnoreCase("MYTHIC_ITEM"))
            return;

        Player player = e.getPlayer();

        // Standardize dynamic string parsing for integration with the Custom Item Provider registry
        String parseVal = reward.value();
        if (reward.type().equalsIgnoreCase("MMOITEM") && !parseVal.toUpperCase().startsWith("MMOITEMS:")) {
            parseVal = "MMOITEMS:" + parseVal;
        } else if (reward.type().equalsIgnoreCase("MYTHIC_ITEM") && !parseVal.toUpperCase().startsWith("MYTHIC_ITEM:")) {
            parseVal = "MYTHIC_ITEM:" + parseVal;
        }

        ItemStack itemStack = ItemBuilder.parseDynamicItem(parseVal);

        if (itemStack != null) {
            String defaultName = SinceDungeon.getPlugin().getLanguageManager().getString("editor.words.reward_default_name", "&7Default");

            if (reward.displayName() != null && !reward.displayName().isEmpty() && !reward.displayName().equals(defaultName)) {
                ItemMeta meta = itemStack.getItemMeta();
                if (meta != null) {
                    meta.displayName(ColorUtils.parse("<!i>" + reward.displayName()));

                    if (reward.lore() != null && !reward.lore().isEmpty()) {
                        List<Component> lore = new ArrayList<>();
                        for (String line : reward.lore()) {
                            lore.add(ColorUtils.parse("<!i>" + line));
                        }
                        meta.lore(lore);
                    }
                    itemStack.setItemMeta(meta);
                }
            }

            Location dropLoc = player.getLocation().add(0, 1, 0);
            Item droppedItem = player.getWorld().dropItem(dropLoc, itemStack);

            droppedItem.setVelocity(new Vector((Math.random() - 0.5) * 0.3, 0.5, (Math.random() - 0.5) * 0.3));
            droppedItem.setPickupDelay(40);
            droppedItem.setGlowing(true);
            droppedItem.setOwner(player.getUniqueId());
            droppedItem.setCanMobPickup(false);

            String holoName = reward.displayName() != null && !reward.displayName().isEmpty() && !reward.displayName().equals(defaultName) ? reward.displayName() : ColorUtils.formatEnumName(itemStack.getType().name());
            Component nameComp = ColorUtils.parse(holoName);
            droppedItem.customName(nameComp);
            droppedItem.setCustomNameVisible(true);

            // Successfully injected - Prevent core loop from giving it straight into inventory
            e.setCancelled(true);
        }
    }
}