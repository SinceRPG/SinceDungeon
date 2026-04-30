package net.danh.sinceDungeon.guis.top;

import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.managers.TopManager;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/**
 * Handles inventory interactions for the Leaderboard GUI.
 * Prevents item stealing and handles pagination and category switching.
 */
public class TopMenuListener implements Listener {

    private final SinceDungeon plugin;

    public TopMenuListener(SinceDungeon plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof TopHolder) {
            for (int slot : e.getRawSlots()) {
                if (slot < e.getView().getTopInventory().getSize()) {
                    e.setCancelled(true);
                    return;
                }
            }
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!(e.getView().getTopInventory().getHolder() instanceof TopHolder holder)) return;

        // Cancel all clicks to prevent item stealing
        e.setCancelled(true);

        if (e.getCurrentItem() == null || e.getCurrentItem().getType() == Material.AIR) return;
        if (e.getClickedInventory() != e.getView().getTopInventory()) return;

        TopGUI gui = new TopGUI(plugin);
        int slot = e.getRawSlot();
        int guiSize = e.getView().getTopInventory().getSize();

        // Handle Pagination
        if (slot == guiSize - 9) {
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            gui.openTopGUI(p, holder.dungeonId(), holder.category(), holder.page() - 1);
            return;
        }
        if (slot == guiSize - 1) {
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            gui.openTopGUI(p, holder.dungeonId(), holder.category(), holder.page() + 1);
            return;
        }

        // Handle Category Switching
        if (slot == guiSize - 7) {
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            gui.openTopGUI(p, holder.dungeonId(), TopManager.TopCategory.FASTEST_TIME, 0);
            return;
        }
        if (slot == guiSize - 5) {
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            gui.openTopGUI(p, holder.dungeonId(), TopManager.TopCategory.MOST_KILLS, 0);
            return;
        }
        if (slot == guiSize - 3) {
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            gui.openTopGUI(p, holder.dungeonId(), TopManager.TopCategory.MOST_CLEARS, 0);
        }
    }
}