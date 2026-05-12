package net.danh.sinceDungeon.guis.editor;

import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.managers.DungeonManager;
import net.danh.sinceDungeon.utils.ColorUtils;
import net.danh.sinceDungeon.utils.ItemBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.util.*;

/**
 * Editor Graphical User Interface Manager
 * <p>
 * Responsibilities:
 * - Constructs all Inventory menus dynamically based on YAML configurations.
 * - Centralizes item generation and lore injection from language files.
 * - Utilizes Dynamic Field Resolution to eliminate hardcoded configuration types.
 */
public class EditorGUI {

    private final SinceDungeon plugin;

    public EditorGUI(SinceDungeon plugin) {
        this.plugin = plugin;
    }

    public String getMsg(String path, String def) {
        String res = plugin.getLanguageManager().getString("editor." + path);
        return (res == null || res.isEmpty()) ? def : res;
    }

    public String getWord(String key, String def) {
        String res = plugin.getLanguageManager().getString("editor.words." + key);
        return (res == null || res.isEmpty()) ? def : res;
    }

    public List<String> getLoreList(String path, List<String> defaultLore) {
        List<String> list = plugin.getLanguageManager().getStringList("editor.items." + path);
        return (list == null || list.isEmpty()) ? defaultLore : list;
    }

    public void sendMessage(Player p, String key, String... placeholders) {
        String msg = plugin.getLanguageManager().getString("editor.chat." + key);
        if (msg == null || msg.isEmpty()) {
            switch (key) {
                case "val_cleared" -> msg = "&eData cleared.";
                case "line_removed" -> msg = "&eLast line removed from the list.";
                case "list_empty" -> msg = "&cThe list is currently empty.";
                case "number_error" -> msg = "&cValue must be a valid number!";
                case "dungeon_deleted" -> msg = "&aSuccessfully deleted dungeon: &e<dungeon>";
                case "stage_inserted" -> msg = "&aSuccessfully shifted configuration and inserted Stage <pos>!";
                case "notification_toggled" -> msg = "&eNotification <key> set to <state>.";
                default -> {
                    return;
                }
            }
        }

        String prefix = plugin.getLanguageManager().getString("prefix", "");
        msg = prefix + msg;

        for (int i = 0; i < placeholders.length; i += 2) {
            msg = msg.replace(placeholders[i], (i + 1 < placeholders.length) ? placeholders[i + 1] : "");
        }
        p.sendMessage(ColorUtils.parseWithPrefix(msg));
    }

    /**
     * Builds an item applying optional Custom Model Data logic.
     */
    public ItemStack makeItem(Material mat, int cmd, String nameRaw, List<String> loreRaw) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (nameRaw != null) meta.displayName(ColorUtils.parse("<!i>" + nameRaw));
            List<Component> lore = new ArrayList<>();
            if (loreRaw != null) {
                for (String s : loreRaw) {
                    lore.add(ColorUtils.parse("<!i>" + s));
                }
            }
            meta.lore(lore);
            if (cmd != -1) meta.setCustomModelData(cmd);
            item.setItemMeta(meta);
        }
        return item;
    }

    public ItemStack makeItem(Material mat, String nameRaw, List<String> loreRaw) {
        return makeItem(mat, -1, nameRaw, loreRaw);
    }

    public Material getNavMaterial() {
        String navItemStr = plugin.getConfigFile().getString("editor.nav-item", "ARROW");
        String[] parts = navItemStr.split(":");
        Material mat = Material.matchMaterial(parts[0]);
        return mat != null ? mat : Material.ARROW;
    }

    public int getNavCmd() {
        String navItemStr = plugin.getConfigFile().getString("editor.nav-item", "ARROW");
        String[] parts = navItemStr.split(":");
        if (parts.length > 1) {
            try {
                return Integer.parseInt(parts[1]);
            } catch (Exception ignored) {
            }
        }
        return -1;
    }

    public ItemStack getNavItemStack(String nameRaw) {
        return makeItem(getNavMaterial(), getNavCmd(), nameRaw, null);
    }

    private void setPagination(Inventory inv, int page, int maxPage, int prevSlot, int nextSlot) {
        if (page > 0) {
            inv.setItem(prevSlot, getNavItemStack(getMsg("items.prev_page", "&e⬅ Previous Page")));
        }
        if (page < maxPage) {
            inv.setItem(nextSlot, getNavItemStack(getMsg("items.next_page", "&eNext Page ➡")));
        }
    }

    public void openMainMenu(Player p, int page) {
        File folder = new File(plugin.getDataFolder(), "dungeons");
        List<File> files = new ArrayList<>();
        if (folder.exists()) {
            File[] arr = folder.listFiles((d, n) -> n.endsWith(".yml"));
            if (arr != null) files.addAll(Arrays.asList(arr));
        }

        int total = files.size();
        int maxPage = Math.max(0, (total - 1) / 45);
        page = Math.max(0, Math.min(maxPage, page));

        Inventory inv = Bukkit.createInventory(new EditorHolder(null, "MAIN", page), 54, ColorUtils.parse(getMsg("title.main", "&lDungeon Editor")));

        for (int i = 0; i < 45; i++) {
            int idx = i + page * 45;
            if (idx >= total) break;
            File f = files.get(idx);

            String dungeonId = f.getName().replace(".yml", "");
            String nameFmt = getMsg("items.dungeon_file_name", "&e<name>");
            String displayName = nameFmt.replace("<name>", dungeonId);

            List<String> lore = getLoreList("dungeon_file_lore", Arrays.asList("&eLeft Click: Edit", "&cShift-Right: Delete Dungeon"));

            ItemStack item = makeItem(Material.PAPER, displayName, lore);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                NamespacedKey key = new NamespacedKey(plugin, "dungeon_id");
                meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, dungeonId);
                item.setItemMeta(meta);
            }

            inv.setItem(i, item);
        }

        inv.setItem(49, makeItem(Material.EMERALD_BLOCK, getMsg("items.create_new", "&a&lCreate New Dungeon"), null));
        setPagination(inv, page, maxPage, 48, 50);
        p.openInventory(inv);
    }

    public void openDungeonMenu(Player p, EditorSession session) {
        session.setLastMenuOpener(player -> openDungeonMenu(player, session));
        String title = getMsg("title.dungeon", "&lEditing: <name>").replace("<name>", session.getFile().getName());
        Inventory inv = Bukkit.createInventory(new EditorHolder(session, "DUNGEON", 0), 27, ColorUtils.parse(title));

        String world = session.getConfig().getString("template-world", getWord("not_set", "Not Set"));
        boolean isPublic = session.getConfig().getBoolean("public", false);
        String publicStatus = isPublic ? getWord("true_word", "&aON") : getWord("false_word", "&cOFF");

        List<String> wLore = new ArrayList<>();
        for (String s : getLoreList("world_template_lore", Arrays.asList("&7Current: &f<world>", "&eLeft Click: Enter new map name")))
            wLore.add(s.replace("<world>", world));
        inv.setItem(10, makeItem(Material.GRASS_BLOCK, getMsg("items.world_template", "&eWorld Template"), wLore));

        List<String> pLore = new ArrayList<>();
        for (String s : getLoreList("public_status_lore", Arrays.asList("&7Current: <status>", "&eLeft Click: Toggle ON/OFF")))
            pLore.add(s.replace("<status>", publicStatus));
        inv.setItem(20, makeItem(isPublic ? Material.ENDER_EYE : Material.ENDER_PEARL, getMsg("items.public_status", "&ePublic Status"), pLore));

        inv.setItem(12, makeItem(Material.PAPER, getMsg("items.conditions", "&eEntry Conditions"), getLoreList("conditions_lore", Collections.singletonList("&7Edit entry requirements"))));
        inv.setItem(14, makeItem(Material.CHEST, getMsg("items.rewards", "&6Rewards"), getLoreList("rewards_lore", Collections.singletonList("&7Edit dungeon rewards"))));
        inv.setItem(16, makeItem(Material.DIAMOND_SWORD, getMsg("items.stages", "&cStages"), getLoreList("stages_lore", Collections.singletonList("&7Edit dungeon content"))));

        inv.setItem(13, makeItem(Material.COMPARATOR, getMsg("items.settings", "&bGameplay Settings"), getLoreList("settings_lore", Arrays.asList("&7Edit custom game rules", "&7for this specific map."))));

        inv.setItem(22, makeItem(Material.WRITABLE_BOOK, getMsg("items.save", "&a&lSave Changes"), null));
        inv.setItem(18, getNavItemStack(getMsg("items.back", "&cGo Back")));

        List<String> deleteLore = getLoreList("delete_dungeon_lore", Arrays.asList("&7Permanently delete this", "&7dungeon and its leaderboard.", "", "&cShift-Right Click to confirm"));
        inv.setItem(26, makeItem(Material.BARRIER, getMsg("items.delete_dungeon", "&c&lDelete Dungeon"), deleteLore));

        p.openInventory(inv);
    }

    public void openSettingsMenu(Player p, EditorSession session, int page) {
        session.setPage("SETTINGS", page);
        session.setLastMenuOpener(player -> openSettingsMenu(player, session, session.getPage("SETTINGS")));

        EditorSession.SettingOption[] options = EditorSession.SettingOption.values();
        int total = options.length;
        int maxPage = Math.max(0, (total - 1) / 18);
        page = Math.max(0, Math.min(maxPage, page));

        Inventory inv = Bukkit.createInventory(new EditorHolder(session, "SETTINGS", page), 27, ColorUtils.parse(getMsg("title.settings", "&lAdvanced Settings")));

        for (int i = 0; i < 18; i++) {
            int idx = i + page * 18;
            if (idx >= total) break;

            EditorSession.SettingOption opt = options[idx];
            String valStr;

            switch (opt.getDataType()) {
                case "BOOL" -> {
                    boolean val = session.getConfig().contains(opt.getLocalPath())
                            ? session.getConfig().getBoolean(opt.getLocalPath())
                            : (opt.getGlobalFallbackPath() != null ? plugin.getConfigFile().getBoolean(opt.getGlobalFallbackPath(), (Boolean) opt.getDefaultValue()) : (Boolean) opt.getDefaultValue());
                    valStr = val ? getWord("true_word", "&aON") : getWord("false_word", "&cOFF");
                }
                case "STRING" ->
                        valStr = session.getConfig().getString(opt.getLocalPath(), (String) opt.getDefaultValue());
                case "INT" -> {
                    int val = session.getConfig().contains(opt.getLocalPath()) ? session.getConfig().getInt(opt.getLocalPath()) : (Integer) opt.getDefaultValue();
                    valStr = val > 0 ? String.valueOf(val) : (opt.name().equals("MAX_PLAYERS") ? getWord("unlimited", "Unlimited") : String.valueOf(val));
                }
                case "DEATH_ENUM" -> {
                    valStr = session.getConfig().contains(opt.getLocalPath())
                            ? session.getConfig().getString(opt.getLocalPath())
                            : (opt.getGlobalFallbackPath() != null ? plugin.getConfigFile().getString(opt.getGlobalFallbackPath(), (String) opt.getDefaultValue()) : (String) opt.getDefaultValue());
                    if (valStr != null) valStr = valStr.toUpperCase();
                }
                case "LIST" -> valStr = String.valueOf(session.getConfig().getStringList(opt.getLocalPath()).size());
                default -> valStr = getWord("unknown", "Unknown");
            }

            List<String> lore = new ArrayList<>();
            for (String s : getLoreList(opt.getLangKey() + "_lore", Arrays.asList("&7Current: &f<val>", "&eLeft Click to edit"))) {
                lore.add(s.replace("<val>", valStr));
            }

            ItemStack item = makeItem(opt.getIcon(), getMsg("items." + opt.getLangKey(), "&e" + opt.name()), lore);
            ItemMeta meta = item.getItemMeta();
            NamespacedKey key = new NamespacedKey(plugin, "setting_id");
            meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, opt.name());
            item.setItemMeta(meta);

            inv.setItem(i, item);
        }

        inv.setItem(18, getNavItemStack(getMsg("items.back", "&cGo Back")));
        setPagination(inv, page, maxPage, 21, 23);
        p.openInventory(inv);
    }

    public void openStringListEditor(Player p, EditorSession session, String path, String returnMenu, int page) {
        session.setCurrentListPath(path);
        session.setCurrentListReturnMenu(returnMenu);
        session.setPage("STRING_LIST", page);
        session.setLastMenuOpener(player -> openStringListEditor(player, session, path, returnMenu, session.getPage("STRING_LIST")));

        List<String> list = session.getConfig().getStringList(path);
        int total = list.size();
        int maxPage = Math.max(0, (total - 1) / 45);
        page = Math.max(0, Math.min(maxPage, page));

        Inventory inv = Bukkit.createInventory(new EditorHolder(session, "EDIT_STRING_LIST", page), 54, ColorUtils.parse(getMsg("title.string_list", "&lEditing List")));

        for (int i = 0; i < 45; i++) {
            int idx = i + page * 45;
            if (idx >= total) break;

            String val = list.get(idx);
            String name = getMsg("items.list_line_item", "&eLine #<index>").replace("<index>", String.valueOf(idx + 1));

            List<String> lore = new ArrayList<>();
            for (String s : getLoreList("list_line_lore", Arrays.asList("&f<val>", "", "&cShift-Right to delete"))) {
                lore.add(s.replace("<val>", val));
            }

            inv.setItem(i, makeItem(Material.PAPER, name, lore));
        }

        inv.setItem(49, makeItem(Material.EMERALD, getMsg("items.add_line", "&aAdd New Line"), null));
        inv.setItem(51, makeItem(Material.TNT, getMsg("items.clear_list", "&cClear Entire List"), null));
        inv.setItem(45, getNavItemStack(getMsg("items.back", "&cGo Back")));
        setPagination(inv, page, maxPage, 48, 50);
        p.openInventory(inv);
    }

    public void openConditionList(Player p, EditorSession session, int page) {
        session.setPage("CONDITIONS", page);
        session.setLastMenuOpener(player -> openConditionList(player, session, session.getPage("CONDITIONS")));

        ConfigurationSection sec = session.getConfig().getConfigurationSection("conditions");
        List<String> keys = sec != null ? new ArrayList<>(sec.getKeys(false)) : new ArrayList<>();

        int total = keys.size();
        int maxPage = Math.max(0, (total - 1) / 45);
        page = Math.max(0, Math.min(maxPage, page));

        Inventory inv = Bukkit.createInventory(new EditorHolder(session, "CONDITIONS", page), 54, ColorUtils.parse(getMsg("title.conditions", "&lConditions")));

        for (int i = 0; i < 45; i++) {
            int idx = i + page * 45;
            if (idx >= total) break;
            String key = keys.get(idx);

            String nameStr = session.getConfig().getString("conditions." + key + ".name", key);
            String check = session.getConfig().getString("conditions." + key + ".check", getWord("unknown", "Unknown"));
            String msg = session.getConfig().getString("conditions." + key + ".msg", getWord("default_word", "Default"));

            String displayName = getMsg("items.condition_item", "&eCondition #<index>").replace("<index>", nameStr);
            List<String> lore = new ArrayList<>();
            for (String s : getLoreList("condition_lore", Arrays.asList("&7Check: &f<check>", "&7Error Msg: &f<msg>", "", "&eLeft Click: Edit check", "&eRight Click: Edit message", "&cShift-Right: Delete")))
                lore.add(s.replace("<check>", check).replace("<msg>", msg));

            inv.setItem(i, makeItem(Material.NAME_TAG, displayName, lore));
        }

        inv.setItem(49, makeItem(Material.EMERALD, getMsg("items.add_condition", "&aAdd Condition"), null));
        inv.setItem(45, getNavItemStack(getMsg("items.back", "&cGo Back")));
        setPagination(inv, page, maxPage, 48, 50);
        p.openInventory(inv);
    }

    public void openRewardMenu(Player p, EditorSession session) {
        session.setLastMenuOpener(player -> openRewardMenu(player, session));
        Inventory inv = Bukkit.createInventory(new EditorHolder(session, "REWARDS_MAIN", 0), 27, ColorUtils.parse(getMsg("title.rewards_main", "&lRewards Menu")));

        inv.setItem(11, makeItem(Material.CLOCK, getMsg("items.reward_solo_tiers", "&6Solo Time Tiers"), null));
        inv.setItem(12, makeItem(Material.GOLD_BLOCK, getMsg("items.reward_party_tiers", "&6Party Time Tiers"), null));
        inv.setItem(15, makeItem(Material.CHEST, getMsg("items.reward_pool_item", "&6Item Pool"), null));

        inv.setItem(18, getNavItemStack(getMsg("items.back", "&cGo Back")));
        p.openInventory(inv);
    }

    public void openRewardTiers(Player p, EditorSession session, int page) {
        session.setPage("REWARD_TIERS", page);
        session.setLastMenuOpener(player -> openRewardTiers(player, session, session.getPage("REWARD_TIERS")));

        String pathPrefix = session.getCurrentTierType().equalsIgnoreCase("PARTY") ? "rewards.party-tiers" : "rewards.solo-tiers";
        ConfigurationSection tiers = session.getConfig().getConfigurationSection(pathPrefix);

        List<String> keys = tiers != null ? new ArrayList<>(tiers.getKeys(false)) : new ArrayList<>();
        keys.sort(Comparator.comparingInt(Integer::parseInt));

        int total = keys.size();
        int maxPage = Math.max(0, (total - 1) / 45);
        page = Math.max(0, Math.min(maxPage, page));

        String title = session.getCurrentTierType().equalsIgnoreCase("PARTY")
                ? getMsg("title.reward_party_tiers", "&lParty Tiers")
                : getMsg("title.reward_solo_tiers", "&lSolo Tiers");

        Inventory inv = Bukkit.createInventory(new EditorHolder(session, "REWARD_TIERS", page), 54, ColorUtils.parse(title));

        for (int i = 0; i < 45; i++) {
            int idx = i + page * 45;
            if (idx >= total) break;
            String key = keys.get(idx);

            int amount = session.getConfig().getInt(pathPrefix + "." + key);
            String name = getMsg("items.tier_item", "&e<time>s").replace("<time>", key);
            List<String> lore = new ArrayList<>();
            for (String s : getLoreList("tier_lore", Arrays.asList("&7Chests: &f<amount>", "", "&eLeft Click: Edit amount", "&cRight Click: Delete tier")))
                lore.add(s.replace("<amount>", String.valueOf(amount)));
            inv.setItem(i, makeItem(Material.CLOCK, name, lore));
        }

        inv.setItem(49, makeItem(Material.EMERALD, getMsg("items.add_tier", "&aAdd Tier"), null));
        inv.setItem(45, getNavItemStack(getMsg("items.back", "&cGo Back")));
        setPagination(inv, page, maxPage, 48, 50);
        p.openInventory(inv);
    }

    public void openRewardPool(Player p, EditorSession session, int page) {
        session.setPage("REWARD_POOL", page);
        session.setLastMenuOpener(player -> openRewardPool(player, session, session.getPage("REWARD_POOL")));

        ConfigurationSection pool = session.getConfig().getConfigurationSection("rewards.pool");
        List<String> keys = pool != null ? new ArrayList<>(pool.getKeys(false)) : new ArrayList<>();

        int total = keys.size();
        int maxPage = Math.max(0, (total - 1) / 45);
        page = Math.max(0, Math.min(maxPage, page));

        Inventory inv = Bukkit.createInventory(new EditorHolder(session, "REWARD_POOL", page), 54, ColorUtils.parse(getMsg("title.reward_pool", "&lReward Pool")));

        for (int i = 0; i < 45; i++) {
            int idx = i + page * 45;
            if (idx >= total) break;
            String key = keys.get(idx);

            String type = session.getConfig().getString("rewards.pool." + key + ".type", getWord("unknown", "Unknown"));
            String val = session.getConfig().getString("rewards.pool." + key + ".value", getWord("unknown", "Unknown"));
            String name = getMsg("items.pool_item", "&eReward #<index>").replace("<index>", key);
            List<String> lore = new ArrayList<>();
            for (String s : getLoreList("pool_lore", Arrays.asList("&7Type: &f<type>", "&7Value: &f<val>", "", "&eLeft Click: Edit reward", "&cShift-Right: Delete")))
                lore.add(s.replace("<type>", type).replace("<val>", val));
            inv.setItem(i, makeItem(Material.BUNDLE, name, lore));
        }

        inv.setItem(49, makeItem(Material.EMERALD, getMsg("items.add_pool_item", "&aAdd Reward"), null));
        inv.setItem(45, getNavItemStack(getMsg("items.back", "&cGo Back")));
        setPagination(inv, page, maxPage, 48, 50);
        p.openInventory(inv);
    }

    public void openRewardEditor(Player p, EditorSession session) {
        session.setLastMenuOpener(player -> openRewardEditor(player, session));
        String key = session.getCurrentRewardKey();
        if (key == null) {
            openRewardPool(p, session, session.getPage("REWARD_POOL"));
            return;
        }

        String path = "rewards.pool." + key;
        String type = session.getConfig().getString(path + ".type", "ITEM");
        String value = session.getConfig().getString(path + ".value", "AIR:1");
        double chance = session.getConfig().getDouble(path + ".chance", 100.0);
        String displayName = session.getConfig().getString(path + ".name", getWord("reward_default_name", "&7Default"));

        String title = getMsg("title.edit_reward", "&lEdit Reward #<index>").replace("<index>", key);
        Inventory inv = Bukkit.createInventory(new EditorHolder(session, "EDIT_REWARD", 0), 27, ColorUtils.parse(title));

        List<String> tLore = new ArrayList<>();
        for (String s : getLoreList("reward_type_lore", Arrays.asList("&7Current: &f<type>", "&eLeft Click: Change (ITEM/COMMAND/MMOITEM)")))
            tLore.add(s.replace("<type>", type));
        inv.setItem(10, makeItem(Material.COMPARATOR, getMsg("items.reward_type", "&eType"), tLore));

        List<String> vLore = new ArrayList<>();
        for (String s : getLoreList("reward_value_lore", Arrays.asList("&7Current: &f<val>", "&eLeft Click: Type in Chat", "&eRight Click: Get held item")))
            vLore.add(s.replace("<val>", value));
        inv.setItem(12, makeItem(Material.PAPER, getMsg("items.reward_value", "&eValue"), vLore));

        List<String> cLore = new ArrayList<>();
        for (String s : getLoreList("reward_chance_lore", Arrays.asList("&7Current: &f<val>%", "&eLeft Click: Edit chance")))
            cLore.add(s.replace("<val>", String.valueOf(chance)));
        inv.setItem(14, makeItem(Material.GOLD_NUGGET, getMsg("items.reward_chance", "&eChance"), cLore));

        List<String> nLore = new ArrayList<>();
        for (String s : getLoreList("reward_name_lore", Arrays.asList("&7Current: &f<val>", "&eLeft Click: Edit name")))
            nLore.add(s.replace("<val>", displayName));
        inv.setItem(16, makeItem(Material.NAME_TAG, getMsg("items.reward_name", "&eDisplay Name"), nLore));

        inv.setItem(18, getNavItemStack(getMsg("items.back", "&cGo Back")));
        p.openInventory(inv);
    }

    public void openStageList(Player p, EditorSession session, int page) {
        session.setPage("STAGES", page);
        session.setLastMenuOpener(player -> openStageList(player, session, session.getPage("STAGES")));

        ConfigurationSection stages = session.getConfig().getConfigurationSection("stages");
        List<String> keys = stages != null ? new ArrayList<>(stages.getKeys(false)) : new ArrayList<>();
        keys.sort(Comparator.comparingInt(s -> {
            try {
                return Integer.parseInt(s);
            } catch (Exception e) {
                return 0;
            }
        }));

        int total = keys.size();
        int maxPage = Math.max(0, (total - 1) / 45);
        page = Math.max(0, Math.min(maxPage, page));

        Inventory inv = Bukkit.createInventory(new EditorHolder(session, "STAGES", page), 54, ColorUtils.parse(getMsg("title.stages", "&lStages")));

        List<String> defaultLore = Arrays.asList(
                "&7Chance to spawn: &a<chance>%",
                "&7Stage Commands: &f<cmds>",
                "",
                "&eLeft Click: Edit Actions",
                "&cRight Click: Edit Chance",
                "&bShift-Left: Edit Commands",
                "&cShift-Right: Delete Stage"
        );

        for (int i = 0; i < 45; i++) {
            int idx = i + page * 45;
            if (idx >= total) break;
            String key = keys.get(idx);
            String name = getMsg("items.stage_item", "&eStage <stage>").replace("<stage>", key);

            double chance = session.getConfig().getDouble("stages." + key + ".chance", 100.0);
            int cmdsCount = session.getConfig().getStringList("stages." + key + ".commands").size();

            List<String> lore = new ArrayList<>();
            for (String s : getLoreList("stage_lore", defaultLore)) {
                lore.add(s.replace("<chance>", String.valueOf(chance)).replace("<cmds>", String.valueOf(cmdsCount)));
            }

            inv.setItem(i, makeItem(Material.FILLED_MAP, name, lore));
        }

        inv.setItem(49, makeItem(Material.EMERALD, getMsg("items.add_stage", "&aAdd Stage"), null));
        inv.setItem(51, makeItem(Material.PISTON, getMsg("items.insert_stage", "&bInsert Stage Here"), getLoreList("insert_stage_lore", Arrays.asList("&7Shifts stages down to make room.", "&eLeft Click: Enter position"))));
        inv.setItem(45, getNavItemStack(getMsg("items.back", "&cGo Back")));
        setPagination(inv, page, maxPage, 48, 50);

        p.openInventory(inv);
    }

    public void openActionList(Player p, EditorSession session, int page) {
        session.setPage("ACTIONS", page);
        session.setLastMenuOpener(player -> openActionList(player, session, session.getPage("ACTIONS")));

        ConfigurationSection sec = session.getConfig().getConfigurationSection("stages." + session.getCurrentStage() + ".actions");
        List<String> keys = sec != null ? new ArrayList<>(sec.getKeys(false)) : new ArrayList<>();

        int total = keys.size();
        int maxPage = Math.max(0, (total - 1) / 45);
        page = Math.max(0, Math.min(maxPage, page));

        String title = getMsg("title.actions", "&lStage <stage>").replace("<stage>", session.getCurrentStage());
        Inventory inv = Bukkit.createInventory(new EditorHolder(session, "ACTIONS", page), 54, ColorUtils.parse(title));

        List<String> defaultLore = Arrays.asList(
                "&7Type: &f<type>",
                "&7Info: &f<desc>",
                "",
                "&eLeft Click: Edit action",
                "&cShift-Right: Delete action"
        );

        for (int i = 0; i < 45; i++) {
            int idx = i + page * 45;
            if (idx >= total) break;
            String key = keys.get(idx);

            String type = session.getConfig().getString("stages." + session.getCurrentStage() + ".actions." + key + ".type", getWord("unknown", "Unknown"));
            DungeonManager.ActionMeta meta = plugin.getDungeonManager().getActionMeta(type);

            String displayTypeName = (meta != null && meta.displayName() != null) ? meta.displayName() : type;
            Material icon = (meta != null && meta.icon() != null) ? meta.icon() : Material.PAPER;
            String desc = (meta != null && meta.description() != null) ? meta.description() : getWord("unknown", "Unknown");

            String name = getMsg("items.action_item", "&eAction #<index>").replace("<index>", key);
            List<String> lore = new ArrayList<>();
            for (String s : getLoreList("action_lore", defaultLore)) {
                lore.add(s.replace("<type>", displayTypeName).replace("<desc>", desc));
            }

            inv.setItem(i, makeItem(icon, name, lore));
        }

        inv.setItem(49, makeItem(Material.EMERALD, getMsg("items.add_action", "&aAdd Action"), null));
        inv.setItem(45, getNavItemStack(getMsg("items.back", "&cGo Back")));
        setPagination(inv, page, maxPage, 48, 50);
        p.openInventory(inv);
    }

    public void openActionChestEditor(Player p, EditorSession session) {
        session.setLastMenuOpener(player -> openActionChestEditor(player, session));
        String title = getMsg("title.edit_action_items", "&lChest Editor: <index>").replace("<index>", session.getCurrentActionKey());
        Inventory inv = Bukkit.createInventory(new EditorHolder(session, "EDIT_ACTION_ITEMS", 0), 27, ColorUtils.parse(title));

        String path = "stages." + session.getCurrentStage() + ".actions." + session.getCurrentActionKey() + ".items";
        ConfigurationSection sec = session.getConfig().getConfigurationSection(path);

        if (sec != null) {
            for (String slotKey : sec.getKeys(false)) {
                try {
                    int slot = Integer.parseInt(slotKey);
                    if (slot >= 0 && slot < 54) {
                        String itemStr = sec.getString(slotKey);
                        ItemStack is = ItemBuilder.parseDynamicItem(itemStr);
                        if (is != null) {
                            inv.setItem(slot, is);
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        }
        p.openInventory(inv);
    }

    public void openNotificationEditor(Player p, EditorSession session) {
        session.setLastMenuOpener(player -> openNotificationEditor(player, session));
        String title = getMsg("title.edit_notifications", "&lAction Notifications");
        Inventory inv = Bukkit.createInventory(new EditorHolder(session, "EDIT_NOTIFICATIONS", 0), 27, ColorUtils.parse(title));

        String path = "stages." + session.getCurrentStage() + ".actions." + session.getCurrentActionKey() + ".notifications";
        ensureNotificationDefaults(session, path);

        String enabled = getWord("true_word", "&aON");
        String disabled = getWord("false_word", "&cOFF");
        int[] slots = {10, 11, 12, 13, 14};
        String[] keys = {"custom_start", "init", "progress", "complete", "warning"};

        for (int i = 0; i < keys.length; i++) {
            boolean value = session.getConfig().getBoolean(path + "." + keys[i], true);
            String name = getMsg("items.notification_key_format", "&e<key>").replace("<key>", keys[i]);
            String status = getMsg("items.notification_status", "&7Status: &f<status>").replace("<status>", value ? enabled : disabled);
            String hint = getMsg("items.notification_hint", "&eLeft Click: Toggle");
            inv.setItem(slots[i], makeItem(value ? Material.LIME_DYE : Material.GRAY_DYE, name, Arrays.asList(status, hint)));
        }

        inv.setItem(18, getNavItemStack(getMsg("items.back", "&cGo Back")));
        p.openInventory(inv);
    }

    public void openActionEditor(Player p, EditorSession session) {
        session.setLastMenuOpener(player -> openActionEditor(player, session));
        String title = getMsg("title.edit_action", "&lEdit Action #<index>").replace("<index>", session.getCurrentActionKey());
        Inventory inv = Bukkit.createInventory(new EditorHolder(session, "EDIT_ACTION", 0), 54, ColorUtils.parse(title));

        String path = "stages." + session.getCurrentStage() + ".actions." + session.getCurrentActionKey();
        ConfigurationSection sec = session.getConfig().getConfigurationSection(path);

        if (sec == null) {
            openActionList(p, session, session.getPage("ACTIONS"));
            return;
        }

        String type = sec.getString("type");
        if (type != null) {
            DungeonManager.ActionMeta meta = plugin.getDungeonManager().getActionMeta(type);
            if (meta != null) {
                boolean changed = false;
                for (Map.Entry<String, Object> entry : meta.defaults().entrySet()) {
                    if (!sec.contains(entry.getKey())) {
                        session.getConfig().set(path + "." + entry.getKey(), entry.getValue());
                        changed = true;
                    }
                }
                if (changed) {
                    sec = session.getConfig().getConfigurationSection(path);
                }
            }
        }

        ensureNotificationDefaults(session, path + ".notifications");
        sec = session.getConfig().getConfigurationSection(path);

        String hintTypeBlocked = getMsg("items.action_type_cant_edit", "&cCannot change action type after creation");

        int slot = 0;
        for (String key : sec.getKeys(false)) {
            if (slot >= 45) break;

            Object rawValue = sec.get(key);
            String valStr = String.valueOf(rawValue);

            FieldProperties props = FieldProperties.resolve(key, rawValue, plugin);

            if (key.equalsIgnoreCase("type")) {
                props.icon = Material.BARRIER;
                props.hint = hintTypeBlocked;
            } else if (key.equalsIgnoreCase("items")) {
                props.icon = Material.CHEST;
                props.hint = getMsg("items.action_val_hint_items", "&eLeft Click: Open item GUI");
                valStr = getMsg("items.action_val_click_arrange", "&a[Click to arrange items]");
            } else if (key.equalsIgnoreCase("notifications")) {
                props.icon = Material.BELL;
                props.hint = getMsg("items.action_val_hint_notif", "&eLeft Click: Open notification toggles");
            } else if (key.equalsIgnoreCase("phases")) {
                props.icon = Material.COMMAND_BLOCK;
                props.hint = getMsg("items.action_val_hint_open_gui", "&eLeft Click: Open GUI");
                int size = sec.getConfigurationSection(key) != null ? sec.getConfigurationSection(key).getKeys(false).size() : 0;
                valStr = getMsg("items.action_val_phases_count", "&f<count> phases").replace("<count>", String.valueOf(size));
            }

            if (props.isList && !key.equalsIgnoreCase("items") && !key.equalsIgnoreCase("phases")) {
                valStr = sec.getStringList(key).size() + " " + getWord("items", "items");
            }

            String keyFmt = getMsg("items.action_key_format", "&6<key>").replace("<key>", key);
            String valFmt = getMsg("items.action_val_format", "&7Value: &f<val>").replace("<val>", valStr);

            inv.setItem(slot++, makeItem(props.icon, keyFmt, Arrays.asList(valFmt, props.hint)));
        }

        inv.setItem(45, getNavItemStack(getMsg("items.back", "&cGo Back")));
        p.openInventory(inv);
    }

    private void ensureNotificationDefaults(EditorSession session, String path) {
        if (!session.getConfig().contains(path + ".custom_start")) session.getConfig().set(path + ".custom_start", true);
        if (!session.getConfig().contains(path + ".init")) session.getConfig().set(path + ".init", true);
        if (!session.getConfig().contains(path + ".progress")) session.getConfig().set(path + ".progress", true);
        if (!session.getConfig().contains(path + ".complete")) session.getConfig().set(path + ".complete", true);
        if (!session.getConfig().contains(path + ".warning")) session.getConfig().set(path + ".warning", true);
    }

    public void openActionTypeSelector(Player p, EditorSession session, int page) {
        session.setPage("SELECT_TYPE", page);

        List<String> types = new ArrayList<>(plugin.getDungeonManager().getRegisteredActions());
        Collections.sort(types);

        int total = types.size();
        int maxPage = Math.max(0, (total - 1) / 45);
        page = Math.max(0, Math.min(maxPage, page));

        Inventory inv = Bukkit.createInventory(new EditorHolder(session, "SELECT_TYPE", page), 54, ColorUtils.parse(getMsg("title.select_type", "&lSelect Action Type")));

        for (int i = 0; i < 45; i++) {
            int idx = i + page * 45;
            if (idx >= total) break;

            String type = types.get(idx);
            DungeonManager.ActionMeta meta = plugin.getDungeonManager().getActionMeta(type);

            String displayName = getMsg("items.action_type_name", "&a&l<name>").replace("<name>", meta.displayName() != null ? meta.displayName() : type);

            List<String> lore = new ArrayList<>();
            lore.add(getMsg("items.action_type_desc", "&7<desc>").replace("<desc>", meta.description()));
            lore.add("");
            lore.add(getMsg("items.action_type_id", "&eInternal ID: &f<id>").replace("<id>", type));

            ItemStack item = makeItem(meta.icon(), displayName, lore);
            ItemMeta im = item.getItemMeta();
            NamespacedKey key = new NamespacedKey(plugin, "action_id");
            im.getPersistentDataContainer().set(key, PersistentDataType.STRING, type);
            item.setItemMeta(im);

            inv.setItem(i, item);
        }

        inv.setItem(45, getNavItemStack(getMsg("items.cancel", "&cCancel")));
        setPagination(inv, page, maxPage, 48, 50);
        p.openInventory(inv);
    }

    public void openPhaseList(Player p, EditorSession session, int page) {
        session.setPage("PHASE_LIST", page);
        session.setLastMenuOpener(player -> openPhaseList(player, session, session.getPage("PHASE_LIST")));

        String path = "stages." + session.getCurrentStage() + ".actions." + session.getCurrentActionKey() + ".phases";
        ConfigurationSection sec = session.getConfig().getConfigurationSection(path);
        List<String> keys = sec != null ? new ArrayList<>(sec.getKeys(false)) : new ArrayList<>();
        keys.sort((a, b) -> {
            try {
                return Integer.compare(Integer.parseInt(b), Integer.parseInt(a));
            } catch (Exception e) {
                return 0;
            }
        });

        int total = keys.size();
        int maxPage = Math.max(0, (total - 1) / 45);
        page = Math.max(0, Math.min(maxPage, page));

        Inventory inv = Bukkit.createInventory(new EditorHolder(session, "PHASE_LIST", page), 54, ColorUtils.parse(getMsg("title.phases", "&lBoss Phases")));

        for (int i = 0; i < 45; i++) {
            int idx = i + page * 45;
            if (idx >= total) break;
            String key = keys.get(idx);

            String name = getMsg("items.phase_item", "&ePhase at <hp>% HP").replace("<hp>", key);
            List<String> lore = getLoreList("phase_lore", Arrays.asList("&7Click to configure phase", "", "&cShift-Right to delete"));
            inv.setItem(i, makeItem(Material.COMMAND_BLOCK, name, lore));
        }

        inv.setItem(49, makeItem(Material.EMERALD, getMsg("items.add_phase", "&aAdd Phase"), null));
        inv.setItem(45, getNavItemStack(getMsg("items.back", "&cGo Back")));
        setPagination(inv, page, maxPage, 48, 50);
        p.openInventory(inv);
    }

    public void openPhaseEditor(Player p, EditorSession session) {
        session.setLastMenuOpener(player -> openPhaseEditor(player, session));
        String title = getMsg("title.edit_phase", "&lPhase: <hp>%").replace("<hp>", session.getCurrentPhaseThreshold());
        Inventory inv = Bukkit.createInventory(new EditorHolder(session, "EDIT_PHASE", 0), 27, ColorUtils.parse(title));

        String basePath = "stages." + session.getCurrentStage() + ".actions." + session.getCurrentActionKey() + ".phases." + session.getCurrentPhaseThreshold();
        String msg = session.getConfig().getString(basePath + ".message", "");
        int attrCount = session.getConfig().getStringList(basePath + ".attributes").size();

        String hintEdit = getMsg("items.action_val_hint_edit", "&eLeft Click: Enter new value");
        String hintList = getMsg("items.action_val_hint_list", "&eLeft: Add Line | Right: Delete Last | &cShift-Right: Clear All");
        String hintGui = getMsg("items.action_val_hint_open_gui", "&eLeft Click: Open GUI");

        inv.setItem(11, makeItem(Material.PAPER, getMsg("items.phase_message", "&ePhase Message"), Arrays.asList("&7Current: &f" + msg, hintEdit)));
        inv.setItem(13, makeItem(Material.POTION, getMsg("items.phase_attributes", "&ePhase Attributes"), Arrays.asList("&7Current: &f" + attrCount + " attributes", hintList)));
        inv.setItem(15, makeItem(Material.ZOMBIE_HEAD, getMsg("items.phase_reinforcements", "&eReinforcements"), Arrays.asList("&7Configure backup mobs", hintGui)));

        inv.setItem(18, getNavItemStack(getMsg("items.back", "&cGo Back")));
        p.openInventory(inv);
    }

    public void openReinforcementEditor(Player p, EditorSession session) {
        session.setLastMenuOpener(player -> openReinforcementEditor(player, session));
        String title = getMsg("title.edit_reinforcements", "&lReinforcements: <hp>%").replace("<hp>", session.getCurrentPhaseThreshold());
        Inventory inv = Bukkit.createInventory(new EditorHolder(session, "EDIT_REINFORCEMENTS", 0), 27, ColorUtils.parse(title));

        String basePath = "stages." + session.getCurrentStage() + ".actions." + session.getCurrentActionKey() + ".phases." + session.getCurrentPhaseThreshold() + ".reinforcements";
        String mob = session.getConfig().getString(basePath + ".mob", "NONE");
        int amount = session.getConfig().getInt(basePath + ".amount", 1);
        String name = session.getConfig().getString(basePath + ".custom_name", "");
        int attrCount = session.getConfig().getStringList(basePath + ".attributes").size();
        int equipCount = session.getConfig().getStringList(basePath + ".equipment").size();

        String hintEdit = getMsg("items.action_val_hint_edit", "&eLeft Click: Enter new value");
        String hintList = getMsg("items.action_val_hint_list", "&eLeft: Add Line | Right: Delete Last | &cShift-Right: Clear All");

        inv.setItem(10, makeItem(Material.CREEPER_HEAD, getMsg("items.reinf_mob", "&eMob Type"), Arrays.asList("&7Current: &f" + mob, hintEdit)));
        inv.setItem(12, makeItem(Material.GOLD_NUGGET, getMsg("items.reinf_amount", "&eAmount"), Arrays.asList("&7Current: &f" + amount, hintEdit)));
        inv.setItem(13, makeItem(Material.NAME_TAG, getMsg("items.reinf_name", "&eCustom Name"), Arrays.asList("&7Current: &f" + name, hintEdit)));
        inv.setItem(14, makeItem(Material.POTION, getMsg("items.reinf_attributes", "&eAttributes"), Arrays.asList("&7Current: &f" + attrCount + " attributes", hintList)));
        inv.setItem(16, makeItem(Material.IRON_CHESTPLATE, getMsg("items.reinf_equipment", "&eEquipment"), Arrays.asList("&7Current: &f" + equipCount + " items", hintList)));

        inv.setItem(18, getNavItemStack(getMsg("items.back", "&cGo Back")));
        p.openInventory(inv);
    }

    public Object getFinalVal(String val, String key) {
        if (val.equalsIgnoreCase("true")) return true;
        if (val.equalsIgnoreCase("false")) return false;
        try {
            return Integer.parseInt(val);
        } catch (Exception e1) {
            try {
                return Double.parseDouble(val);
            } catch (Exception ignored) {
            }
        }
        return val;
    }

    public static class FieldProperties {
        public EditorSession.InputType inputType;
        public Material icon;
        public boolean isList;
        public boolean isLocation;
        public String hint;

        public static FieldProperties resolve(String key, Object rawValue, SinceDungeon plugin) {
            FieldProperties p = new FieldProperties();
            p.isList = rawValue instanceof List;
            String lowerKey = key.toLowerCase(Locale.ROOT);
            p.isLocation = lowerKey.contains("loc") || lowerKey.contains("pos") || lowerKey.contains("center") || lowerKey.contains("target") || lowerKey.contains("corner") || lowerKey.contains("levers");

            boolean isNumber = rawValue instanceof Number || lowerKey.contains("time") || lowerKey.contains("amount") || lowerKey.contains("radius") || lowerKey.contains("chance") || lowerKey.contains("level") || lowerKey.contains("health") || lowerKey.contains("damage") || lowerKey.contains("speed") || lowerKey.contains("interval") || lowerKey.contains("stage");
            boolean isBoolean = rawValue instanceof Boolean || lowerKey.contains("is_baby") || lowerKey.contains("scale_with_party") || lowerKey.contains("per_player");

            EditorGUI gui = new EditorGUI(plugin);

            if (p.isLocation) {
                p.inputType = p.isList ? EditorSession.InputType.EDIT_LOCATION_LIST : EditorSession.InputType.EDIT_LOCATION;
                p.icon = Material.COMPASS;
                p.hint = p.isList ? gui.getMsg("items.action_val_hint_loc_list", "&eLeft: Type | Right: Current Pos | &cShift-Right: Clear list") : gui.getMsg("items.action_val_hint_loc_single", "&eLeft: Type | Right: Current Pos | &cShift-Right: Delete");
            } else if (p.isList) {
                p.inputType = EditorSession.InputType.EDIT_LIST;
                p.icon = Material.BOOK;
                p.hint = gui.getMsg("items.action_val_hint_list", "&eLeft: Add Line | Right: Delete Last | &cShift-Right: Clear All");
            } else if (isBoolean) {
                p.inputType = EditorSession.InputType.EDIT_BOOLEAN;
                p.icon = Material.LEVER;
                p.hint = gui.getMsg("items.action_val_hint_edit", "&eLeft Click: Enter new value");
            } else if (isNumber) {
                p.inputType = EditorSession.InputType.EDIT_NUMBER;
                p.icon = Material.GOLD_NUGGET;
                p.hint = gui.getMsg("items.action_val_hint_edit", "&eLeft Click: Enter new value");
            } else {
                p.inputType = EditorSession.InputType.EDIT_STRING;
                p.icon = Material.PAPER;
                p.hint = gui.getMsg("items.action_val_hint_edit", "&eLeft Click: Enter new value");
            }

            if (lowerKey.contains("mob") || lowerKey.contains("core_type")) p.icon = Material.CREEPER_HEAD;
            if (lowerKey.contains("color") || lowerKey.contains("style")) p.icon = Material.PAINTING;
            if (lowerKey.contains("message")) p.icon = Material.PAPER;
            if (lowerKey.contains("attributes")) p.icon = Material.POTION;
            if (lowerKey.contains("equipment")) p.icon = Material.IRON_CHESTPLATE;
            if (lowerKey.contains("drops")) p.icon = Material.DIAMOND;
            if (lowerKey.contains("random_mobs")) p.icon = Material.TRIAL_SPAWNER;

            return p;
        }
    }
}
