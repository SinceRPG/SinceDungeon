package net.danh.sinceDungeon.guis.editor;

import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.managers.DungeonManager;
import net.danh.sinceDungeon.utils.ColorUtils;
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
 * Handles the construction and opening of all graphical editor interfaces.
 * Features robust fallbacks to prevent empty lore if the config is missing keys.
 */
public class EditorGUI {

    private final SinceDungeon plugin;

    public EditorGUI(SinceDungeon plugin) {
        this.plugin = plugin;
    }

    /**
     * Retrieves a message from the configuration with a robust fallback.
     */
    public String getMsg(String path, String def) {
        String res = plugin.getMessagesFile().getString("editor." + path);
        return (res == null || res.isEmpty()) ? def : res;
    }

    /**
     * Retrieves a single localized word from the configuration.
     */
    public String getWord(String key, String def) {
        String res = plugin.getMessagesFile().getString("editor.words." + key);
        return (res == null || res.isEmpty()) ? def : res;
    }

    /**
     * Retrieves a lore list from the configuration, substituting a default array if missing.
     * Prevents items from appearing blank if the language file fails to update.
     */
    private List<String> getLoreList(String path, List<String> defaultLore) {
        List<String> list = plugin.getMessagesFile().getStringList("editor.items." + path);
        return (list == null || list.isEmpty()) ? defaultLore : list;
    }

    /**
     * Sends a formatted message to the player, replacing any provided placeholders.
     */
    public void sendMessage(Player p, String key, String... placeholders) {
        String msg = plugin.getMessagesFile().getString("editor.chat." + key);
        if (msg == null || msg.isEmpty()) {
            if (key.equals("val_cleared")) msg = "&eData cleared.";
            else if (key.equals("line_removed")) msg = "&eLast line removed from the list.";
            else if (key.equals("list_empty")) msg = "&cThe list is currently empty.";
            else if (key.equals("number_error")) msg = "&cValue must be a valid number!";
            else if (key.equals("dungeon_deleted")) msg = "&aSuccessfully deleted dungeon: &e<dungeon>";
            else return;
        }

        String prefix = plugin.getMessagesFile().getString("prefix", "");
        msg = prefix + msg;

        for (int i = 0; i < placeholders.length; i += 2) {
            msg = msg.replace(placeholders[i], (i + 1 < placeholders.length) ? placeholders[i + 1] : "");
        }
        p.sendMessage(ColorUtils.parseWithPrefix(msg));
    }

    /**
     * Helper to construct ItemStacks with Adventure components.
     */
    private ItemStack makeItem(Material mat, String nameRaw, List<String> loreRaw) {
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
            item.setItemMeta(meta);
        }
        return item;
    }

    public Material getNavItem() {
        String navItemStr = plugin.getConfigFile().getString("editor.nav-item", "ARROW");
        Material mat = Material.matchMaterial(navItemStr);
        return mat != null ? mat : Material.ARROW;
    }

    private void setPagination(Inventory inv, int page, int maxPage, int prevSlot, int nextSlot) {
        if (page > 0) {
            inv.setItem(prevSlot, makeItem(getNavItem(), getMsg("items.prev_page", "&e⬅ Previous Page"), null));
        }
        if (page < maxPage) {
            inv.setItem(nextSlot, makeItem(getNavItem(), getMsg("items.next_page", "&eNext Page ➡"), null));
        }
    }

    /**
     * Opens the root selection menu containing all Dungeon YAML files.
     */
    public void openMainMenu(Player p, int page) {
        File folder = new File(plugin.getDataFolder(), "dungeons");
        List<File> files = new ArrayList<>();
        if (folder.exists()) {
            File[] arr = folder.listFiles((d, n) -> n.endsWith(".yml"));
            if (arr != null) files.addAll(Arrays.asList(arr));
        }

        int total = files.size();
        int maxPage = Math.max(0, (total - 1) / 45);
        page = Math.max(0, Math.min(maxPage, page)); // Java 17 safe clamp

        Inventory inv = Bukkit.createInventory(new EditorHolder(null, "MAIN", page), 54, ColorUtils.parse(getMsg("title.main", "&lDungeon Editor")));

        for (int i = 0; i < 45; i++) {
            int idx = i + page * 45;
            if (idx >= total) break;
            File f = files.get(idx);

            String dungeonId = f.getName().replace(".yml", "");
            String nameFmt = getMsg("items.dungeon_file_name", "&e<name>");
            String displayName = nameFmt.replace("<name>", dungeonId);

            // Added Shift-Right hint to lore
            List<String> lore = getLoreList("dungeon_file_lore", Arrays.asList("&eLeft Click: Edit", "&cShift-Right: Delete Dungeon"));

            ItemStack item = makeItem(Material.PAPER, displayName, lore);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                // Use NBT to store the internal ID safely
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

    /**
     * Opens the specific settings interface for a selected Dungeon.
     */
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
        inv.setItem(18, makeItem(getNavItem(), getMsg("items.back", "&cGo Back"), null));

        // NEW: Delete Dungeon Button
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
                    boolean val = session.getConfig().contains(opt.getLocalPath()) ? session.getConfig().getBoolean(opt.getLocalPath()) : plugin.getConfigFile().getBoolean(opt.getGlobalFallbackPath(), (Boolean) opt.getDefaultValue());
                    valStr = val ? getWord("true_word", "&aON") : getWord("false_word", "&cOFF");
                }
                case "INT" -> {
                    int val = session.getConfig().contains(opt.getLocalPath()) ? session.getConfig().getInt(opt.getLocalPath()) : (Integer) opt.getDefaultValue();
                    valStr = val > 0 ? String.valueOf(val) : (opt.name().equals("MAX_PLAYERS") ? getWord("unlimited", "Unlimited") : String.valueOf(val));
                }
                case "DEATH_ENUM" -> {
                    valStr = session.getConfig().contains(opt.getLocalPath()) ? session.getConfig().getString(opt.getLocalPath()) : plugin.getConfigFile().getString(opt.getGlobalFallbackPath(), (String) opt.getDefaultValue());
                    if (valStr != null) valStr = valStr.toUpperCase();
                }
                case "LIST" -> {
                    valStr = String.valueOf(session.getConfig().getStringList(opt.getLocalPath()).size());
                }
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

        inv.setItem(18, makeItem(getNavItem(), getMsg("items.back", "&cGo Back"), null));
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
        inv.setItem(45, makeItem(getNavItem(), getMsg("items.back", "&cGo Back"), null));
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
        inv.setItem(45, makeItem(getNavItem(), getMsg("items.back", "&cGo Back"), null));
        setPagination(inv, page, maxPage, 48, 50);
        p.openInventory(inv);
    }

    public void openRewardMenu(Player p, EditorSession session) {
        session.setLastMenuOpener(player -> openRewardMenu(player, session));
        Inventory inv = Bukkit.createInventory(new EditorHolder(session, "REWARDS_MAIN", 0), 27, ColorUtils.parse(getMsg("title.rewards_main", "&lRewards Menu")));
        inv.setItem(11, makeItem(Material.CLOCK, getMsg("items.reward_tiers_item", "&6Time Tiers"), null));
        inv.setItem(15, makeItem(Material.CHEST, getMsg("items.reward_pool_item", "&6Item Pool"), null));
        inv.setItem(18, makeItem(getNavItem(), getMsg("items.back", "&cGo Back"), null));
        p.openInventory(inv);
    }

    public void openRewardTiers(Player p, EditorSession session, int page) {
        session.setPage("REWARD_TIERS", page);
        session.setLastMenuOpener(player -> openRewardTiers(player, session, session.getPage("REWARD_TIERS")));

        ConfigurationSection tiers = session.getConfig().getConfigurationSection("rewards.tiers");
        List<String> keys = tiers != null ? new ArrayList<>(tiers.getKeys(false)) : new ArrayList<>();
        keys.sort(Comparator.comparingInt(Integer::parseInt));

        int total = keys.size();
        int maxPage = Math.max(0, (total - 1) / 45);
        page = Math.max(0, Math.min(maxPage, page));

        Inventory inv = Bukkit.createInventory(new EditorHolder(session, "REWARD_TIERS", page), 54, ColorUtils.parse(getMsg("title.reward_tiers", "&lReward Tiers")));

        for (int i = 0; i < 45; i++) {
            int idx = i + page * 45;
            if (idx >= total) break;
            String key = keys.get(idx);

            int amount = session.getConfig().getInt("rewards.tiers." + key);
            String name = getMsg("items.tier_item", "&e<time>s").replace("<time>", key);
            List<String> lore = new ArrayList<>();
            for (String s : getLoreList("tier_lore", Arrays.asList("&7Chests: &f<amount>", "", "&eLeft Click: Edit amount", "&cRight Click: Delete tier")))
                lore.add(s.replace("<amount>", String.valueOf(amount)));
            inv.setItem(i, makeItem(Material.CLOCK, name, lore));
        }

        inv.setItem(49, makeItem(Material.EMERALD, getMsg("items.add_tier", "&aAdd Tier"), null));
        inv.setItem(45, makeItem(getNavItem(), getMsg("items.back", "&cGo Back"), null));
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
        inv.setItem(45, makeItem(getNavItem(), getMsg("items.back", "&cGo Back"), null));
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

        inv.setItem(18, makeItem(getNavItem(), getMsg("items.back", "&cGo Back"), null));
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
        inv.setItem(45, makeItem(getNavItem(), getMsg("items.back", "&cGo Back"), null));
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

            String name = getMsg("items.action_item", "&eAction #<index>").replace("<index>", key);
            List<String> lore = new ArrayList<>();
            for (String s : getLoreList("action_lore", defaultLore)) {
                lore.add(s.replace("<type>", displayTypeName));
            }

            inv.setItem(i, makeItem(icon, name, lore));
        }

        inv.setItem(49, makeItem(Material.EMERALD, getMsg("items.add_action", "&aAdd Action"), null));
        inv.setItem(45, makeItem(getNavItem(), getMsg("items.back", "&cGo Back"), null));
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
                        ItemStack is = new ItemStack(Material.STONE);
                        if (itemStr != null && itemStr.contains(":")) {
                            String[] parts = itemStr.split(":");
                            Material mat = Material.matchMaterial(parts[0]);
                            if (mat != null) is = new ItemStack(mat, Integer.parseInt(parts[1]));
                        }
                        inv.setItem(slot, is);
                    }
                } catch (Exception ignored) {
                }
            }
        }

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

        String hintEdit = getMsg("items.action_val_hint_edit", "&eLeft Click: Enter new value");
        String hintLocSingle = getMsg("items.action_val_hint_loc_single", "&eLeft: Type | Right: Current Pos | &cShift-Right: Delete");
        String hintLocList = getMsg("items.action_val_hint_loc_list", "&eLeft: Type | Right: Current Pos | &cShift-Right: Clear list");
        String hintList = getMsg("items.action_val_hint_list", "&eLeft: Add Line | Right: Delete Last | &cShift-Right: Clear All");
        String hintItems = getMsg("items.action_val_hint_items", "&eLeft Click: Open GUI | Read YAML config to add Keys");
        String hintNotif = getMsg("items.action_val_hint_notif", "&7Per-action notification overrides");
        String hintTypeBlocked = getMsg("items.action_type_cant_edit", "&cCannot change action type after creation");

        int slot = 0;
        for (String key : sec.getKeys(false)) {
            if (slot >= 45) break;
            String val = String.valueOf(sec.get(key));
            Material icon = Material.BOOK;
            String hint;

            boolean isLocation = key.toLowerCase().contains("location") || key.equals("target") || key.equals("trigger") || key.equals("corner1") || key.equals("corner2") || key.equals("pos") || key.equals("center");
            boolean isList = sec.isList(key);
            boolean isRandomMobs = key.equalsIgnoreCase("random_mobs");

            switch (key) {
                case "type":
                    icon = Material.BARRIER;
                    hint = hintTypeBlocked;
                    break;
                case "amount":
                    icon = Material.GOLD_NUGGET;
                    hint = hintEdit;
                    break;
                case "mob":
                    icon = Material.CREEPER_HEAD;
                    hint = hintEdit;
                    break;
                case "start_message":
                    icon = Material.PAPER;
                    hint = hintList;
                    break;
                case "random_mobs":
                    icon = Material.TRIAL_SPAWNER;
                    hint = hintList;
                    isList = true;
                    break;
                case "notifications":
                    icon = Material.BELL;
                    hint = hintNotif;
                    break;
                case "items":
                    icon = Material.CHEST;
                    hint = hintItems;
                    val = getMsg("items.action_val_click_arrange", "&a[Click to arrange items]");
                    isList = false;
                    break;
                default:
                    if (isLocation) {
                        icon = Material.COMPASS;
                        hint = isList ? hintLocList : hintLocSingle;
                    } else if (isList) {
                        hint = hintList;
                    } else {
                        hint = hintEdit;
                    }
                    break;
            }

            if (isList) {
                val = sec.getStringList(key).size() + " " + getWord("items", "items");
            }

            String keyFmt = getMsg("items.action_key_format", "&6<key>").replace("<key>", key);
            String valFmt = getMsg("items.action_val_format", "&7Value: &f<val>").replace("<val>", val);

            inv.setItem(slot++, makeItem(icon, keyFmt, Arrays.asList(valFmt, hint)));
        }

        inv.setItem(45, makeItem(getNavItem(), getMsg("items.back", "&cGo Back"), null));
        p.openInventory(inv);
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

        inv.setItem(45, makeItem(getNavItem(), getMsg("items.cancel", "&cCancel"), null));
        setPagination(inv, page, maxPage, 48, 50);
        p.openInventory(inv);
    }

    public Object getFinalVal(String val, String key) {
        int maxAmount = plugin.getConfigFile().getInt("editor.limits.max-mob-amount", 200);
        double maxRadius = plugin.getConfigFile().getDouble("editor.limits.max-radius", 100.0);

        Object finalVal = val;
        if (val.equalsIgnoreCase("true")) finalVal = true;
        else if (val.equalsIgnoreCase("false")) finalVal = false;
        else {
            try {
                int parsed = Integer.parseInt(val);
                if (key.equalsIgnoreCase("amount")) finalVal = Math.max(0, Math.min(maxAmount, parsed));
                else if (key.equalsIgnoreCase("level")) finalVal = Math.max(1, parsed);
                else if (key.equalsIgnoreCase("radius") || key.equalsIgnoreCase("chance"))
                    finalVal = Math.max(0, parsed);
                else finalVal = parsed;
            } catch (Exception e1) {
                try {
                    double parsed = Double.parseDouble(val);
                    if (key.equalsIgnoreCase("radius")) finalVal = Math.max(0.0, Math.min(maxRadius, parsed));
                    else if (key.equalsIgnoreCase("chance")) finalVal = Math.max(0.0, Math.min(100.0, parsed));
                    else if (key.equalsIgnoreCase("amount")) finalVal = Math.max(0.0, parsed);
                    else finalVal = parsed;
                } catch (Exception ignored) {
                }
            }
        }
        return finalVal;
    }
}