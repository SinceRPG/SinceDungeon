package net.danh.sincedungeonpremium.hooks;

import io.lumine.mythic.api.MythicProvider;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.items.MythicItem;
import net.danh.sinceDungeon.api.SinceDungeonAPI;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;

/**
 * Encapsulated hook class that bridges MythicMobs with the extensible CustomItemProvider API.
 * Ensures NoClassDefFoundErrors are avoided when MythicMobs is uninstalled.
 */
public class PremiumMythicMobsHook {

    public static void register() {
        // Register as a Universal Item Provider so it works in Loot Chests, Drops, and GiveItemActions
        SinceDungeonAPI.get().registerItemProvider("MYTHIC_ITEM", data -> {
            try {
                String[] parts = data.split(":");
                if (parts.length < 2) return null;
                String internalName = parts[1];
                int amount = parts.length > 2 ? Integer.parseInt(parts[2]) : 1;

                Optional<MythicItem> optItem = MythicProvider.get().getItemManager().getItem(internalName);
                if (optItem.isPresent()) {
                    ItemStack itemStack = MythicBukkit.inst().getItemManager().getItemStack(internalName);
                    if (itemStack != null) {
                        itemStack.setAmount(amount);
                        return itemStack;
                    }
                }
            } catch (Exception ignored) {
            }
            return null;
        });

        // Register as a Reward Processor to hook cleanly into the Database and Event workflows
        SinceDungeonAPI.get().registerRewardProcessor("MYTHIC_ITEM", (player, value, displayName) -> {
            ItemStack item = SinceDungeonAPI.get().getManager().getItemProvider("MYTHIC_ITEM").parseItem("MYTHIC_ITEM:" + value);
            if (item != null) {
                SinceDungeonAPI.get().giveItemSafely(player, item, displayName != null ? displayName : value);
            }
        });
    }
}