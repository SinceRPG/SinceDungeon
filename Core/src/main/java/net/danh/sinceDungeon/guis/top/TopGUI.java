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
import java.util.*;

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

    private String formatTime(long seconds) {
        long m = seconds / 60;
        long s = seconds % 60;
        return String.format("%02d:%02d", m, s);
    }

    public void openTopGUI(Player p, String dungeonId, TopManager.TopCategory category, int page) {
        String loadingMsg = plugin.getLanguageManager().getString("top.loading", "&eLoading leaderboard data...");
        p.sendMessage(ColorUtils.parseWithPrefix(loadingMsg));

        final int limit = plugin.getConfigFile().getInt("leaderboard.fetch-limit", 50);
        int rawGuiSize = plugin.getConfigFile().getInt("leaderboard.gui-size", 54);
        final int guiSize = Math.max(18, rawGuiSize);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<TopManager.TopEntry> records = plugin.getTopManager().getTop(dungeonId, category, limit);

            Bukkit.getScheduler().runTask(plugin, () -> {
                String titleRaw = plugin.getLanguageManager().getString("top.gui_title", "&6&lLeaderboard: &e<map>");
                Inventory inv = Bukkit.createInventory(new TopHolder(dungeonId, category, page), guiSize, ColorUtils.parse(titleRaw.replace("<map>", dungeonId)));

                int maxPage = Math.max(0, (records.size() - 1) / 36);
                final int currentPage = Math.max(0, Math.min(maxPage, page));

                for (int i = 0; i < 36; i++) {
                    int index = i + (currentPage * 36);
                    if (index >= records.size()) break;

                    TopManager.TopEntry entry = records.get(index);
                    int rank = index + 1;

                    Material mat;
                    if (rank == 1)
                        mat = Material.matchMaterial(plugin.getConfigFile().getString("leaderboard.items.rank_1", "GOLD_BLOCK"));
                    else if (rank == 2)
                        mat = Material.matchMaterial(plugin.getConfigFile().getString("leaderboard.items.rank_2", "IRON_BLOCK"));
                    else if (rank == 3)
                        mat = Material.matchMaterial(plugin.getConfigFile().getString("leaderboard.items.rank_3", "COPPER_BLOCK"));
                    else
                        mat = Material.matchMaterial(plugin.getConfigFile().getString("leaderboard.items.rank_other", "COAL_BLOCK"));

                    if (mat == null) mat = Material.PLAYER_HEAD;

                    String nameRaw = plugin.getLanguageManager().getString("top.item_format", "&e#<rank> &f<player>")
                            .replace("<rank>", String.valueOf(rank))
                            .replace("<player>", category == TopManager.TopCategory.PARTY_FASTEST_TIME ? plugin.getLanguageManager().getString("top.party_run_name", "Party Run") : entry.playerName());

                    List<String> loreRaw = new ArrayList<>();
                    String dateStr = dateFormat.format(new Date(entry.recordedAt()));

                    switch (category) {
                        case FASTEST_TIME -> {
                            List<String> timeLore = plugin.getLanguageManager().getStringList("top.time_lore");
                            if (timeLore == null || timeLore.isEmpty())
                                timeLore = Arrays.asList("&7Time: &a<value>", "&7Date: &f<date>");
                            for (String s : timeLore) {
                                loreRaw.add(s.replace("<value>", formatTime(entry.value())).replace("<date>", dateStr));
                            }
                        }
                        case PARTY_FASTEST_TIME -> {
                            List<String> partyLore = plugin.getLanguageManager().getStringList("top.party_time_lore");
                            if (partyLore == null || partyLore.isEmpty())
                                partyLore = Arrays.asList("&7Time: &a<value>", "&7Date: &f<date>", "&7Members: &f<members>");
                            for (String s : partyLore) {
                                loreRaw.add(s.replace("<value>", formatTime(entry.value())).replace("<date>", dateStr).replace("<members>", entry.playerName()));
                            }
                        }
                        case MOST_KILLS -> {
                            List<String> killLore = plugin.getLanguageManager().getStringList("top.kills_lore");
                            if (killLore == null || killLore.isEmpty())
                                killLore = Arrays.asList("&7Total Kills: &c<value>", "&7Date: &f<date>");
                            for (String s : killLore) {
                                loreRaw.add(s.replace("<value>", String.valueOf(entry.value())).replace("<date>", dateStr));
                            }
                        }
                        case MOST_CLEARS -> {
                            List<String> clearLore = plugin.getLanguageManager().getStringList("top.clears_lore");
                            if (clearLore == null || clearLore.isEmpty())
                                clearLore = Arrays.asList("&7Total Clears: &a<value>", "&7Date: &f<date>");
                            for (String s : clearLore) {
                                loreRaw.add(s.replace("<value>", String.valueOf(entry.value())).replace("<date>", dateStr));
                            }
                        }
                    }

                    inv.setItem(i, makeItem(mat, nameRaw, loreRaw));
                }

                Material timeMat = Material.matchMaterial(plugin.getConfigFile().getString("leaderboard.items.category_time", "CLOCK"));
                Material partyTimeMat = Material.matchMaterial(plugin.getConfigFile().getString("leaderboard.items.category_party_time", "GOLDEN_APPLE"));
                Material killsMat = Material.matchMaterial(plugin.getConfigFile().getString("leaderboard.items.category_kills", "DIAMOND_SWORD"));
                Material clearsMat = Material.matchMaterial(plugin.getConfigFile().getString("leaderboard.items.category_clears", "NETHER_STAR"));

                if (timeMat == null) timeMat = Material.CLOCK;
                if (partyTimeMat == null) partyTimeMat = Material.GOLDEN_APPLE;
                if (killsMat == null) killsMat = Material.DIAMOND_SWORD;
                if (clearsMat == null) clearsMat = Material.NETHER_STAR;

                List<String> switchLore = plugin.getLanguageManager().getStringList("top.click_to_switch");
                if (switchLore == null || switchLore.isEmpty())
                    switchLore = Collections.singletonList("&eLeft Click to view this category!");

                inv.setItem(guiSize - 8, makeItem(timeMat, plugin.getLanguageManager().getString("top.category_time", "&b&lSolo Fastest Clears"), switchLore));
                inv.setItem(guiSize - 6, makeItem(partyTimeMat, plugin.getLanguageManager().getString("top.category_party_time", "&d&lParty Fastest Clears"), switchLore));
                inv.setItem(guiSize - 4, makeItem(killsMat, plugin.getLanguageManager().getString("top.category_kills", "&c&lMost Kills"), switchLore));
                inv.setItem(guiSize - 2, makeItem(clearsMat, plugin.getLanguageManager().getString("top.category_clears", "&a&lMost Clears"), switchLore));

                String navItemStr = plugin.getConfigFile().getString("editor.nav-item", "ARROW");
                Material navMat = Material.matchMaterial(navItemStr);
                if (navMat == null) navMat = Material.ARROW;

                if (currentPage > 0) {
                    inv.setItem(guiSize - 9, makeItem(navMat, plugin.getLanguageManager().getString("editor.items.prev_page", "&e⬅ Previous Page"), null));
                }
                if (currentPage < maxPage) {
                    inv.setItem(guiSize - 1, makeItem(navMat, plugin.getLanguageManager().getString("editor.items.next_page", "&eNext Page ➡"), null));
                }

                p.openInventory(inv);
            });
        });
    }
}