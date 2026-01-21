package net.danh.sinceDungeon.system;

import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.api.Type;
import org.bukkit.inventory.ItemStack;

public class MMOItemsHook {
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