package net.danh.sinceDungeon.guis.top;

import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.managers.TopManager;
import net.danh.sinceDungeon.utils.ColorUtils;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Handles the construction and rendering of the Leaderboard GUI.
 * Queries the database asynchronously to prevent main-thread lag spikes.
 */
public class TopGUI {

    private final SinceDungeon plugin;
    private final SimpleDateFormat dateFormat;

    public TopGUI(SinceDungeon plugin) {
        this.plugin = plugin;
        this.dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm");
    }

    /**
     * Helper to create items with custom names and lore.
     */
    private ItemStack makeItem(Material mat, String nameRaw, List<String> loreRaw) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (nameRaw != null) meta.displayName(ColorUtils.parse("<!i>" + nameRaw));
            if (loreRaw != null) {
                List<Component> lore = new ArrayList<>();
                for (String s : loreRaw) {
                    lore.add(ColorUtils.parse("<!i>" + s));
                }
                meta.lore(lore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Formats seconds into MM:SS format.
     */
    private String formatTime(long seconds) {
        long m = seconds / 60;
        long s = seconds % 60;
        return String.format("%02d:%02d", m, s);
    }

    /**
     * Opens the Leaderboard GUI for the specified dungeon map.
     * Fetches data async and then syncs back to the main thread to open the inventory.
     *
     * @param p         The player opening the GUI.
     * @param dungeonId The ID of the dungeon to display.
     * @param category  The specific leaderboard category (Time, Kills, Clears).
     * @param page      The current page number.
     */
    public void openTopGUI(Player p, String dungeonId, TopManager.TopCategory category, int page) {
        String loadingMsg = plugin.getMessagesFile().getString("top.loading", "&eLoading leaderboard data...");
        p.sendMessage(ColorUtils.parseWithPrefix(loadingMsg));

        int limit = plugin.getConfigFile().getInt("leaderboard.fetch-limit", 50);
        int guiSize = plugin.getConfigFile().getInt("leaderboard.gui-size", 54);

        // Fetch data asynchronously to prevent freezing the server
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<TopManager.TopEntry> records = plugin.getTopManager().getTop(dungeonId, category, limit);

            // Sync back to main thread to create and open the inventory
            Bukkit.getScheduler().runTask(plugin, () -> {
                String titleRaw = plugin.getMessagesFile().getString("top.gui_title", "&6&lLeaderboard: &e<map>");
                Inventory inv = Bukkit.createInventory(new TopHolder(dungeonId, category, page), guiSize, ColorUtils.parse(titleRaw.replace("<map>", dungeonId)));

                int maxPage = Math.max(0, (records.size() - 1) / 36);
                int currentPage = Math.clamp(page, 0, maxPage);

                // Render the records
                for (int i = 0; i < 36; i++) {
                    int index = i + (currentPage * 36);
                    if (index >= records.size()) break;

                    TopManager.TopEntry entry = records.get(index);
                    int rank = index + 1;

                    Material mat = Material.COAL_BLOCK;
                    if (rank == 1)
                        mat = Material.matchMaterial(plugin.getConfigFile().getString("leaderboard.items.rank_1", "GOLD_BLOCK"));
                    else if (rank == 2)
                        mat = Material.matchMaterial(plugin.getConfigFile().getString("leaderboard.items.rank_2", "IRON_BLOCK"));
                    else if (rank == 3)
                        mat = Material.matchMaterial(plugin.getConfigFile().getString("leaderboard.items.rank_3", "COPPER_BLOCK"));
                    else
                        mat = Material.matchMaterial(plugin.getConfigFile().getString("leaderboard.items.rank_other", "COAL_BLOCK"));

                    if (mat == null) mat = Material.PLAYER_HEAD;

                    String nameRaw = plugin.getMessagesFile().getString("top.item_format", "&e#<rank> &f<player>")
                            .replace("<rank>", String.valueOf(rank))
                            .replace("<player>", entry.playerName());

                    List<String> loreRaw = new ArrayList<>();
                    String dateStr = dateFormat.format(new Date(entry.recordedAt()));

                    switch (category) {
                        case FASTEST_TIME -> {
                            for (String s : plugin.getMessagesFile().getStringList("top.time_lore")) {
                                loreRaw.add(s.replace("<value>", formatTime(entry.value())).replace("<date>", dateStr));
                            }
                        }
                        case MOST_KILLS -> {
                            for (String s : plugin.getMessagesFile().getStringList("top.kills_lore")) {
                                loreRaw.add(s.replace("<value>", String.valueOf(entry.value())).replace("<date>", dateStr));
                            }
                        }
                        case MOST_CLEARS -> {
                            for (String s : plugin.getMessagesFile().getStringList("top.clears_lore")) {
                                loreRaw.add(s.replace("<value>", String.valueOf(entry.value())).replace("<date>", dateStr));
                            }
                        }
                    }

                    inv.setItem(i, makeItem(mat, nameRaw, loreRaw));
                }

                // Render Navigation & Category Switch Buttons
                Material timeMat = Material.matchMaterial(plugin.getConfigFile().getString("leaderboard.items.category_time", "CLOCK"));
                Material killsMat = Material.matchMaterial(plugin.getConfigFile().getString("leaderboard.items.category_kills", "DIAMOND_SWORD"));
                Material clearsMat = Material.matchMaterial(plugin.getConfigFile().getString("leaderboard.items.category_clears", "NETHER_STAR"));

                List<String> switchLore = plugin.getMessagesFile().getStringList("top.click_to_switch");

                inv.setItem(guiSize - 7, makeItem(timeMat, plugin.getMessagesFile().getString("top.category_time"), switchLore));
                inv.setItem(guiSize - 5, makeItem(killsMat, plugin.getMessagesFile().getString("top.category_kills"), switchLore));
                inv.setItem(guiSize - 3, makeItem(clearsMat, plugin.getMessagesFile().getString("top.category_clears"), switchLore));

                // Pagination Buttons
                String navItemStr = plugin.getConfigFile().getString("editor.nav-item", "ARROW");
                Material navMat = Material.matchMaterial(navItemStr);
                if (navMat == null) navMat = Material.ARROW;

                if (currentPage > 0) {
                    inv.setItem(guiSize - 9, makeItem(navMat, plugin.getMessagesFile().getString("editor.items.prev_page", "&e⬅ Previous Page"), null));
                }
                if (currentPage < maxPage) {
                    inv.setItem(guiSize - 1, makeItem(navMat, plugin.getMessagesFile().getString("editor.items.next_page", "&eNext Page ➡"), null));
                }

                p.openInventory(inv);
            });
        });
    }
}