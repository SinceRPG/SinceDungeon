package net.danh.sinceDungeon.system;

import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.api.Type;
import org.bukkit.inventory.ItemStack;

/**
 * Handles interactions and logic parsing for the MMOItems plugin.
 */
public class MMOItemsHook {
    /**
     * Generates a specific MMOItem item stack.
     *
     * @param typeStr The string type representation.
     * @param id      The specific item ID.
     * @param amount  The quantity requested.
     * @return The built ItemStack, or null if invalid.
     */
    public static ItemStack getMMOItem(String typeStr, String id, int amount) {
        try {
            Type type = Type.get(typeStr);
            if (type == null) return null;
            ItemStack item = MMOItems.plugin.getItem(type, id);
            if (item != null) item.setAmount(amount);
            return item;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}