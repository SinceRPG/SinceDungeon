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

    /**
     * Tự động trích xuất chuỗi thông tin của một MMOItem để lưu vào Config.
     *
     * @param item Vật phẩm cần kiểm tra.
     * @return Chuỗi định dạng MMOITEMS:TYPE:ID:AMOUNT, hoặc null nếu không phải MMOItem.
     */
    public static String getMMOItemString(ItemStack item) {
        try {
            if (item == null || !item.hasItemMeta()) return null;
            io.lumine.mythic.lib.api.item.NBTItem nbtItem = io.lumine.mythic.lib.api.item.NBTItem.get(item);
            if (nbtItem.hasType() && nbtItem.getString("MMOITEMS_ITEM_ID") != null && !nbtItem.getString("MMOITEMS_ITEM_ID").isEmpty()) {
                return "MMOITEMS:" + nbtItem.getType() + ":" + nbtItem.getString("MMOITEMS_ITEM_ID") + ":" + item.getAmount();
            }
        } catch (Exception | NoClassDefFoundError ignored) {
        }
        return null;
    }
}