package net.danh.sinceDungeon.api.interfaces;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Functional interface for parsing custom string formats into ItemStacks.
 * Used for expanding the Loot Chest and Reward Drop systems.
 */
@FunctionalInterface
public interface CustomItemProvider {
    /**
     * Parses the given string data (e.g. "MY_PLUGIN:ITEM_ID:AMOUNT") into an ItemStack.
     *
     * @param data The full string data from the config.
     * @return The parsed ItemStack, or null if parsing fails.
     */
    @Nullable
    ItemStack parseItem(String data);
}