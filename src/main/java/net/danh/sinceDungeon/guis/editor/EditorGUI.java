package net.danh.sinceDungeon.guis.editor;

import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.hooks.MMOItemsHook;
import net.danh.sinceDungeon.managers.DungeonManager;
import net.danh.sinceDungeon.utils.ColorUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jspecify.annotations.NonNull;

import java.io.File;
import java.util.*;

public class EditorGUI {

    private final SinceDungeon plugin;

    public EditorGUI(SinceDungeon plugin) {
        this.plugin = plugin;
    }

    public String getMsg(String path) {
        return plugin.getMessagesFile().getString("editor." + path);
    }

    public String getMsg(String path, String def) {
        String res = plugin.getMessagesFile().getString("editor." + path);
        return (res == null || res.isEmpty()) ? def : res;
    }

    public String getWord(String key) {
        return getMsg("words." + key);
    }

    public void sendMessage(Player p, String key, String... placeholders) {
        String msg = getMsg("chat." + key);
        if (msg == null || msg.isEmpty()) {
            if (key.equals("val_cleared")) msg = "<yellow>Data cleared.";
            else if (key.equals("line_removed")) msg = "<yellow>Last line removed from the list.";
            else if (key.equals("list_empty")) msg = "<red>The list is currently empty.";
            else return;
        }

        String prefix = plugin.getMessagesFile().getString("prefix", "");
        msg = prefix + msg;

        for (int i = 0; i < placeholders.length; i += 2) {
            msg = msg.replace(placeholders[i], (i + 1 < placeholders.length) ? placeholders[i + 1] : "");
        }
        p.sendMessage(ColorUtils.parseWithPrefix(msg));
    }

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

    private String getPlainText(Component c) {
        return PlainTextComponentSerializer.plainText().serialize(c);
    }

    private String locToString(Location l) {
        return String.format(Locale.US, "%.1f,%.1f,%.1f", l.getX(), l.getY(), l.getZ());
    }

    public Material getNavItem() {
        String navItemStr = plugin.getConfigFile().getString("editor.nav-item", "ARROW");
        Material mat = Material.matchMaterial(navItemStr);
        return mat != null ? mat : Material.ARROW;
    }

    private void setPagination(Inventory inv, int page, int maxPage) {
        if (page > 0) {
            inv.setItem(48, makeItem(getNavItem(), getMsg("items.prev_page", "<yellow>⬅ Previous"), null));
        }
        if (page < maxPage) {
            inv.setItem(50, makeItem(getNavItem(), getMsg("items.next_page", "<yellow>Next ➡"), null));
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
        page = Math.clamp(page, 0, maxPage);

        Inventory inv = Bukkit.createInventory(new EditorHolder(null, "MAIN", page), 54, ColorUtils.parse(getMsg("title.main")));

        for (int i = 0; i < 45; i++) {
            int idx = i + page * 45;
            if (idx >= total) break;
            File f = files.get(idx);
            inv.setItem(i, makeItem(Material.PAPER, "<yellow>" + f.getName().replace(".yml", ""), Collections.singletonList(getMsg("items.click_edit"))));
        }

        inv.setItem(49, makeItem(Material.EMERALD_BLOCK, getMsg("items.create_new"), null));
        setPagination(inv, page, maxPage);
        p.openInventory(inv);
    }

    public void openDungeonMenu(Player p, EditorSession session) {
        session.setLastMenuOpener(player -> openDungeonMenu(player, session));
        String title = getMsg("title.dungeon").replace("<name>", session.getFile().getName());
        Inventory inv = Bukkit.createInventory(new EditorHolder(session, "DUNGEON", 0), 27, ColorUtils.parse(title));

        String world = session.getConfig().getString("template-world", getWord("not_set"));
        boolean isPublic = session.getConfig().getBoolean("public", false);
        String publicStatus = isPublic ? getWord("true_word") : getWord("false_word");

        List<String> wLore = new ArrayList<>();
        for (String s : plugin.getMessagesFile().getStringList("editor.items.world_template_lore"))
            wLore.add(s.replace("<world>", world));
        inv.setItem(10, makeItem(Material.GRASS_BLOCK, getMsg("items.world_template"), wLore));

        List<String> pLore = new ArrayList<>();
        for (String s : plugin.getMessagesFile().getStringList("editor.items.public_status_lore"))
            pLore.add(s.replace("<status>", publicStatus));
        inv.setItem(20, makeItem(isPublic ? Material.ENDER_EYE : Material.ENDER_PEARL, getMsg("items.public_status"), pLore));

        inv.setItem(12, makeItem(Material.PAPER, getMsg("items.conditions"), plugin.getMessagesFile().getStringList("editor.items.conditions_lore")));
        inv.setItem(14, makeItem(Material.CHEST, getMsg("items.rewards"), plugin.getMessagesFile().getStringList("editor.items.rewards_lore")));
        inv.setItem(16, makeItem(Material.DIAMOND_SWORD, getMsg("items.stages"), plugin.getMessagesFile().getStringList("editor.items.stages_lore")));

        inv.setItem(13, makeItem(Material.COMPARATOR, getMsg("items.settings"), plugin.getMessagesFile().getStringList("editor.items.settings_lore")));

        inv.setItem(22, makeItem(Material.WRITABLE_BOOK, getMsg("items.save"), null));
        inv.setItem(18, makeItem(getNavItem(), getMsg("items.back"), null));

        p.openInventory(inv);
    }

    // Add this helper method to EditorGUI
    private List<String> getDynamicLore(String path, String replacement) {
        List<String> lore = new ArrayList<>();
        for (String s : plugin.getMessagesFile().getStringList("editor.items." + path)) {
            lore.add(s.replace("<val>", replacement));
        }
        return lore;
    }

    // Inside openSettingsMenu(Player p, EditorSession session)
    public void openSettingsMenu(Player p, EditorSession session) {
        session.setLastMenuOpener(player -> openSettingsMenu(player, session));
        Inventory inv = Bukkit.createInventory(new EditorHolder(session, "SETTINGS", 0), 27, ColorUtils.parse(getMsg("title.settings")));

        boolean keepInv = session.getConfig().contains("settings.keep-inventory-on-death") ? session.getConfig().getBoolean("settings.keep-inventory-on-death") : plugin.getConfigFile().getBoolean("dungeon.gameplay.keep-inventory-on-death", true);
        boolean preventDrop = session.getConfig().contains("settings.prevent-item-dropping") ? session.getConfig().getBoolean("settings.prevent-item-dropping") : plugin.getConfigFile().getBoolean("dungeon.gameplay.prevent-item-dropping", true);
        boolean blockPearls = session.getConfig().contains("settings.block-ender-pearls") ? session.getConfig().getBoolean("settings.block-ender-pearls") : plugin.getConfigFile().getBoolean("dungeon.gameplay.block-ender-pearls", true);
        int kickDelay = session.getConfig().contains("settings.kick-delay-after-finish") ? session.getConfig().getInt("settings.kick-delay-after-finish") : plugin.getConfigFile().getInt("dungeon.gameplay.kick-delay-after-finish", 10);
        boolean forceWeather = session.getConfig().contains("settings.force-daylight-and-clear-weather") ? session.getConfig().getBoolean("settings.force-daylight-and-clear-weather") : plugin.getConfigFile().getBoolean("dungeon.gameplay.force-daylight-and-clear-weather", true);
        boolean saveStats = session.getConfig().contains("settings.save-and-restore-stats") ? session.getConfig().getBoolean("settings.save-and-restore-stats") : plugin.getConfigFile().getBoolean("dungeon.save-and-restore-stats", false);
        String deathAction = session.getConfig().contains("settings.death-action") ? session.getConfig().getString("settings.death-action") : plugin.getConfigFile().getString("dungeon.death-action", "RESPAWN");
        boolean clearMobDrops = session.getConfig().contains("settings.clear-mob-drops") ? session.getConfig().getBoolean("settings.clear-mob-drops") : plugin.getConfigFile().getBoolean("dungeon.clear-mob-drops", true);
        int reqLives = session.getConfig().contains("settings.required-lives-to-join") ? session.getConfig().getInt("settings.required-lives-to-join") : 1;
        int deductLives = session.getConfig().contains("settings.lives-deducted-per-death") ? session.getConfig().getInt("settings.lives-deducted-per-death") : 1;
        boolean randomizeStages = session.getConfig().contains("settings.randomize-stages") ? session.getConfig().getBoolean("settings.randomize-stages") : plugin.getConfigFile().getBoolean("dungeon.gameplay.randomize-stages", false);
        inv.setItem(21, makeItem(Material.ENDER_PEARL, getMsg("items.setting_randomize_stages"), getDynamicLore("setting_randomize_stages_lore", randomizeStages ? getWord("true_word") : getWord("false_word"))));

        inv.setItem(10, makeItem(Material.TOTEM_OF_UNDYING, getMsg("items.setting_keep_inv"), getDynamicLore("setting_keep_inv_lore", keepInv ? getWord("true_word") : getWord("false_word"))));
        inv.setItem(11, makeItem(Material.BARRIER, getMsg("items.setting_prevent_drop"), getDynamicLore("setting_prevent_drop_lore", preventDrop ? getWord("true_word") : getWord("false_word"))));
        inv.setItem(12, makeItem(Material.ENDER_PEARL, getMsg("items.setting_block_pearls"), getDynamicLore("setting_block_pearls_lore", blockPearls ? getWord("true_word") : getWord("false_word"))));
        inv.setItem(13, makeItem(Material.CLOCK, getMsg("items.setting_kick_delay"), getDynamicLore("setting_kick_delay_lore", String.valueOf(kickDelay))));
        inv.setItem(14, makeItem(Material.SUNFLOWER, getMsg("items.setting_force_weather"), getDynamicLore("setting_force_weather_lore", forceWeather ? getWord("true_word") : getWord("false_word"))));
        inv.setItem(15, makeItem(Material.GOLDEN_APPLE, getMsg("items.setting_save_stats"), getDynamicLore("setting_save_stats_lore", saveStats ? getWord("true_word") : getWord("false_word"))));
        inv.setItem(16, makeItem(Material.SKELETON_SKULL, getMsg("items.setting_death_action"), getDynamicLore("setting_death_action_lore", deathAction.toUpperCase())));
        inv.setItem(17, makeItem(Material.ROTTEN_FLESH, getMsg("items.setting_clear_drops"), getDynamicLore("setting_clear_drops_lore", clearMobDrops ? getWord("true_word") : getWord("false_word"))));

        // Lives Settings
        inv.setItem(19, makeItem(Material.RED_BED, getMsg("items.setting_req_lives"), getDynamicLore("setting_req_lives_lore", String.valueOf(reqLives))));
        inv.setItem(20, makeItem(Material.WITHER_ROSE, getMsg("items.setting_deduct_lives"), getDynamicLore("setting_deduct_lives_lore", String.valueOf(deductLives))));

        inv.setItem(18, makeItem(getNavItem(), getMsg("items.back"), null));
        p.openInventory(inv);
    }

    private List<String> getLoreToggle(boolean state) {
        return Arrays.asList("<gray>Current: " + (state ? getWord("true_word") : getWord("false_word")), "<yellow>Left Click to toggle");
    }

    public void openConditionList(Player p, EditorSession session, int page) {
        session.setPage("CONDITIONS", page);
        session.setLastMenuOpener(player -> openConditionList(player, session, session.getPage("CONDITIONS")));

        ConfigurationSection sec = session.getConfig().getConfigurationSection("conditions");
        List<String> keys = sec != null ? new ArrayList<>(sec.getKeys(false)) : new ArrayList<>();

        int total = keys.size();
        int maxPage = Math.max(0, (total - 1) / 45);
        page = Math.clamp(page, 0, maxPage);

        Inventory inv = Bukkit.createInventory(new EditorHolder(session, "CONDITIONS", page), 54, ColorUtils.parse(getMsg("title.conditions")));

        for (int i = 0; i < 45; i++) {
            int idx = i + page * 45;
            if (idx >= total) break;
            String key = keys.get(idx);

            String nameStr = session.getConfig().getString("conditions." + key + ".name", key);
            String check = session.getConfig().getString("conditions." + key + ".check", getWord("unknown"));
            String msg = session.getConfig().getString("conditions." + key + ".msg", getWord("default_word"));

            String displayName = getMsg("items.condition_item").replace("<index>", nameStr);
            List<String> lore = new ArrayList<>();
            for (String s : plugin.getMessagesFile().getStringList("editor.items.condition_lore"))
                lore.add(s.replace("<check>", check).replace("<msg>", msg));

            inv.setItem(i, makeItem(Material.NAME_TAG, displayName, lore));
        }

        inv.setItem(49, makeItem(Material.EMERALD, getMsg("items.add_condition"), null));
        inv.setItem(45, makeItem(getNavItem(), getMsg("items.back"), null));
        setPagination(inv, page, maxPage);
        p.openInventory(inv);
    }

    public void openRewardMenu(Player p, EditorSession session) {
        session.setLastMenuOpener(player -> openRewardMenu(player, session));
        Inventory inv = Bukkit.createInventory(new EditorHolder(session, "REWARDS_MAIN", 0), 27, ColorUtils.parse(getMsg("title.rewards_main")));
        inv.setItem(11, makeItem(Material.CLOCK, getMsg("items.reward_tiers_item"), null));
        inv.setItem(15, makeItem(Material.CHEST, getMsg("items.reward_pool_item"), null));
        inv.setItem(18, makeItem(getNavItem(), getMsg("items.back"), null));
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
        page = Math.clamp(page, 0, maxPage);

        Inventory inv = Bukkit.createInventory(new EditorHolder(session, "REWARD_TIERS", page), 54, ColorUtils.parse(getMsg("title.reward_tiers")));

        for (int i = 0; i < 45; i++) {
            int idx = i + page * 45;
            if (idx >= total) break;
            String key = keys.get(idx);

            int amount = session.getConfig().getInt("rewards.tiers." + key);
            String name = getMsg("items.tier_item").replace("<time>", key);
            List<String> lore = new ArrayList<>();
            for (String s : plugin.getMessagesFile().getStringList("editor.items.tier_lore"))
                lore.add(s.replace("<amount>", String.valueOf(amount)));
            inv.setItem(i, makeItem(Material.CLOCK, name, lore));
        }

        inv.setItem(49, makeItem(Material.EMERALD, getMsg("items.add_tier"), null));
        inv.setItem(45, makeItem(getNavItem(), getMsg("items.back"), null));
        setPagination(inv, page, maxPage);
        p.openInventory(inv);
    }

    public void openRewardPool(Player p, EditorSession session, int page) {
        session.setPage("REWARD_POOL", page);
        session.setLastMenuOpener(player -> openRewardPool(player, session, session.getPage("REWARD_POOL")));

        ConfigurationSection pool = session.getConfig().getConfigurationSection("rewards.pool");
        List<String> keys = pool != null ? new ArrayList<>(pool.getKeys(false)) : new ArrayList<>();

        int total = keys.size();
        int maxPage = Math.max(0, (total - 1) / 45);
        page = Math.clamp(page, 0, maxPage);

        Inventory inv = Bukkit.createInventory(new EditorHolder(session, "REWARD_POOL", page), 54, ColorUtils.parse(getMsg("title.reward_pool")));

        for (int i = 0; i < 45; i++) {
            int idx = i + page * 45;
            if (idx >= total) break;
            String key = keys.get(idx);

            String type = session.getConfig().getString("rewards.pool." + key + ".type", getWord("unknown"));
            String val = session.getConfig().getString("rewards.pool." + key + ".value", getWord("unknown"));
            String name = getMsg("items.pool_item").replace("<index>", key);
            List<String> lore = new ArrayList<>();
            for (String s : plugin.getMessagesFile().getStringList("editor.items.pool_lore"))
                lore.add(s.replace("<type>", type).replace("<val>", val));
            inv.setItem(i, makeItem(Material.BUNDLE, name, lore));
        }

        inv.setItem(49, makeItem(Material.EMERALD, getMsg("items.add_pool_item"), null));
        inv.setItem(45, makeItem(getNavItem(), getMsg("items.back"), null));
        setPagination(inv, page, maxPage);
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
        String displayName = session.getConfig().getString(path + ".name", getWord("reward_default_name"));

        String title = getMsg("title.edit_reward").replace("<index>", key);
        Inventory inv = Bukkit.createInventory(new EditorHolder(session, "EDIT_REWARD", 0), 27, ColorUtils.parse(title));

        List<String> tLore = new ArrayList<>();
        for (String s : plugin.getMessagesFile().getStringList("editor.items.reward_type_lore"))
            tLore.add(s.replace("<type>", type));
        inv.setItem(10, makeItem(Material.COMPARATOR, getMsg("items.reward_type"), tLore));

        List<String> vLore = new ArrayList<>();
        for (String s : plugin.getMessagesFile().getStringList("editor.items.reward_value_lore"))
            vLore.add(s.replace("<val>", value));
        inv.setItem(12, makeItem(Material.PAPER, getMsg("items.reward_value"), vLore));

        List<String> cLore = new ArrayList<>();
        for (String s : plugin.getMessagesFile().getStringList("editor.items.reward_chance_lore"))
            cLore.add(s.replace("<val>", String.valueOf(chance)));
        inv.setItem(14, makeItem(Material.GOLD_NUGGET, getMsg("items.reward_chance"), cLore));

        List<String> nLore = new ArrayList<>();
        for (String s : plugin.getMessagesFile().getStringList("editor.items.reward_name_lore"))
            nLore.add(s.replace("<val>", displayName));
        inv.setItem(16, makeItem(Material.NAME_TAG, getMsg("items.reward_name"), nLore));

        inv.setItem(18, makeItem(getNavItem(), getMsg("items.back"), null));
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
        page = Math.clamp(page, 0, maxPage);

        Inventory inv = Bukkit.createInventory(new EditorHolder(session, "STAGES", page), 54, ColorUtils.parse(getMsg("title.stages")));

        for (int i = 0; i < 45; i++) {
            int idx = i + page * 45;
            if (idx >= total) break;
            String key = keys.get(idx);
            String name = getMsg("items.stage_item").replace("<stage>", key);
            inv.setItem(i, makeItem(Material.FILLED_MAP, name, plugin.getMessagesFile().getStringList("editor.items.stage_lore")));

            double chance = session.getConfig().getDouble("stages." + key + ".chance", 100.0);
            List<String> lore = new ArrayList<>();
            lore.add("&7Chance to spawn: &a" + chance + "%");
            lore.add("&cRight Click: Edit Chance");
            lore.addAll(plugin.getMessagesFile().getStringList("editor.items.stage_lore"));

            inv.setItem(i, makeItem(Material.FILLED_MAP, name, lore));
        }

        inv.setItem(49, makeItem(Material.EMERALD, getMsg("items.add_stage"), null));
        inv.setItem(45, makeItem(getNavItem(), getMsg("items.back"), null));
        setPagination(inv, page, maxPage);

        p.openInventory(inv);
    }

    public void openActionList(Player p, EditorSession session, int page) {
        session.setPage("ACTIONS", page);
        session.setLastMenuOpener(player -> openActionList(player, session, session.getPage("ACTIONS")));

        ConfigurationSection sec = session.getConfig().getConfigurationSection("stages." + session.getCurrentStage() + ".actions");
        List<String> keys = sec != null ? new ArrayList<>(sec.getKeys(false)) : new ArrayList<>();

        int total = keys.size();
        int maxPage = Math.max(0, (total - 1) / 45);
        page = Math.clamp(page, 0, maxPage);

        String title = getMsg("title.actions").replace("<stage>", session.getCurrentStage());
        Inventory inv = Bukkit.createInventory(new EditorHolder(session, "ACTIONS", page), 54, ColorUtils.parse(title));

        for (int i = 0; i < 45; i++) {
            int idx = i + page * 45;
            if (idx >= total) break;
            String key = keys.get(idx);

            String type = session.getConfig().getString("stages." + session.getCurrentStage() + ".actions." + key + ".type", getWord("unknown"));
            DungeonManager.ActionMeta meta = plugin.getDungeonManager().getActionMeta(type);

            String displayTypeName = (meta != null && meta.displayName() != null) ? meta.displayName() : type;
            Material icon = (meta != null && meta.icon() != null) ? meta.icon() : Material.PAPER;

            String name = getMsg("items.action_item").replace("<index>", key);
            List<String> lore = new ArrayList<>();
            for (String s : plugin.getMessagesFile().getStringList("editor.items.action_lore"))
                lore.add(s.replace("<type>", displayTypeName));

            inv.setItem(i, makeItem(icon, name, lore));
        }

        inv.setItem(49, makeItem(Material.EMERALD, getMsg("items.add_action"), null));
        inv.setItem(45, makeItem(getNavItem(), getMsg("items.back"), null));
        setPagination(inv, page, maxPage);
        p.openInventory(inv);
    }

    private ItemStack parseItemString(String data) {
        if (data == null || data.isEmpty()) return null;
        try {
            String cleanData = data.replace(" ", "");
            String[] parts = cleanData.split(":");
            if (parts.length >= 3 && parts[0].equalsIgnoreCase("MMOITEMS")) {
                if (Bukkit.getPluginManager().isPluginEnabled("MMOItems")) {
                    int amount = parts.length > 3 ? Integer.parseInt(parts[3]) : 1;
                    return MMOItemsHook.getMMOItem(parts[1], parts[2], amount);
                }
            } else {
                Material mat = Material.matchMaterial(parts[0]);
                if (mat != null) {
                    int amount = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;
                    return new ItemStack(mat, amount);
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    public void openActionChestEditor(Player p, EditorSession session) {
        session.setLastMenuOpener(player -> openActionChestEditor(player, session));
        String title = getMsg("title.edit_action_items").replace("<index>", session.getCurrentActionKey());
        Inventory inv = Bukkit.createInventory(new EditorHolder(session, "EDIT_ACTION_ITEMS", 0), 27, ColorUtils.parse(title));

        String path = "stages." + session.getCurrentStage() + ".actions." + session.getCurrentActionKey() + ".items";
        ConfigurationSection sec = session.getConfig().getConfigurationSection(path);

        if (sec != null) {
            for (String slotKey : sec.getKeys(false)) {
                try {
                    int slot = Integer.parseInt(slotKey);
                    if (slot >= 0 && slot < 54) {
                        String itemStr = sec.getString(slotKey);
                        ItemStack is = parseItemString(itemStr);
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

    public void openActionEditor(Player p, EditorSession session) {
        session.setLastMenuOpener(player -> openActionEditor(player, session));
        String title = getMsg("title.edit_action").replace("<index>", session.getCurrentActionKey());
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

        int slot = 0;
        for (String key : sec.getKeys(false)) {
            if (slot >= 45) break;
            String val = String.valueOf(sec.get(key));
            Material icon = Material.BOOK;
            String hint = getMsg("items.action_val_hint_edit", "<yellow>Trái: Sửa | <red>Shift-Phải: Xóa");
            /**
             * Checks if the configuration key represents a location coordinate.
             * Added "center" to support the ControlZoneAction properties.
             */
            boolean isLocation = key.toLowerCase().contains("location") || key.equals("target") || key.equals("trigger") || key.equals("corner1") || key.equals("corner2") || key.equals("pos") || key.equals("center");
            boolean isList = sec.isList(key);
            boolean isItems = key.equalsIgnoreCase("items");

            boolean isRandomMobs = key.equalsIgnoreCase("random_mobs");
            boolean isNotifications = key.equalsIgnoreCase("notifications");

            switch (key) {
                case "type":
                    icon = Material.BARRIER;
                    hint = getMsg("items.action_type_cant_edit");
                    break;
                case "amount":
                    icon = Material.GOLD_NUGGET;
                    break;
                case "mob":
                    icon = Material.CREEPER_HEAD;
                    break;
                case "start_message":
                    icon = Material.PAPER;
                    hint = getMsg("items.action_val_hint_list", "<yellow>Left: Add Line | Right: Delete Last | <red>Shift-Right: Clear All");
                    break;
                case "random_mobs":
                    icon = Material.TRIAL_SPAWNER;
                    hint = getMsg("items.action_val_hint_list", "<yellow>Left: Add Entry | Right: Remove Last | <red>Shift-Right: Clear");
                    isList = true;
                    break;
                case "notifications":
                    icon = Material.BELL;
                    hint = "<gray>Per-action notification overrides";
                    break;
                case "items":
                    icon = Material.CHEST;
                    hint = getMsg("items.action_val_hint_items", "<yellow>Left Click: Open chest editor");
                    val = "<aqua>[Click to arrange items]";
                    isList = false;
                    break;
                default:
                    if (isLocation) {
                        icon = Material.COMPASS;
                        hint = isList ? getMsg("items.action_val_hint_loc_list") : getMsg("items.action_val_hint_loc_single");
                    } else if (isList) {
                        hint = getMsg("items.action_val_hint_list", "<yellow>Left: Add Line | Right: Delete Last | <red>Shift-Right: Clear All");
                    }
                    break;
            }

            if (isList) {
                val = sec.getStringList(key).toString();
            }

            inv.setItem(slot++, makeItem(icon, "<gold>" + key, Arrays.asList("<gray>" + getWord("value") + ": <white>" + val, hint)));
        }

        inv.setItem(45, makeItem(getNavItem(), getMsg("items.back"), null));
        p.openInventory(inv);
    }

    public void openActionTypeSelector(Player p, EditorSession session, int page) {
        session.setPage("SELECT_TYPE", page);

        List<String> types = new ArrayList<>(plugin.getDungeonManager().getRegisteredActions());
        Collections.sort(types);

        int total = types.size();
        int maxPage = Math.max(0, (total - 1) / 45);
        page = Math.clamp(page, 0, maxPage);

        Inventory inv = Bukkit.createInventory(new EditorHolder(session, "SELECT_TYPE", page), 54, ColorUtils.parse(getMsg("title.select_type")));

        for (int i = 0; i < 45; i++) {
            int idx = i + page * 45;
            if (idx >= total) break;

            String type = types.get(idx);
            DungeonManager.ActionMeta meta = plugin.getDungeonManager().getActionMeta(type);
            String displayName = (meta.displayName() != null) ? "<green><bold>" + meta.displayName() : "<green>" + type;

            ItemStack item = makeItem(meta.icon(), displayName, Arrays.asList("<gray>" + meta.description(), "", "<yellow>Internal ID: <white>" + type));
            ItemMeta im = item.getItemMeta();
            NamespacedKey key = new NamespacedKey(plugin, "action_id");
            im.getPersistentDataContainer().set(key, PersistentDataType.STRING, type);
            item.setItemMeta(im);

            inv.setItem(i, item);
        }

        inv.setItem(45, makeItem(getNavItem(), getMsg("items.cancel"), null));
        setPagination(inv, page, maxPage);
        p.openInventory(inv);
    }

    public @NonNull Object getFinalVal(String val, String key) {
        int maxAmount = plugin.getConfigFile().getInt("editor.limits.max-mob-amount", 200);
        double maxRadius = plugin.getConfigFile().getDouble("editor.limits.max-radius", 100.0);

        Object finalVal = val;
        if (val.equalsIgnoreCase("true")) finalVal = true;
        else if (val.equalsIgnoreCase("false")) finalVal = false;
        else {
            try {
                int parsed = Integer.parseInt(val);
                if (key.equalsIgnoreCase("amount")) finalVal = Math.min(maxAmount, Math.max(0, parsed));
                else if (key.equalsIgnoreCase("level")) finalVal = Math.max(1, parsed);
                else if (key.equalsIgnoreCase("radius") || key.equalsIgnoreCase("chance"))
                    finalVal = Math.max(0, parsed);
                else finalVal = parsed;
            } catch (Exception e1) {
                try {
                    double parsed = Double.parseDouble(val);
                    if (key.equalsIgnoreCase("radius")) finalVal = Math.min(maxRadius, Math.max(0.0, parsed));
                    else if (key.equalsIgnoreCase("chance")) finalVal = Math.min(100.0, Math.max(0.0, parsed));
                    else if (key.equalsIgnoreCase("amount")) finalVal = Math.max(0.0, parsed);
                    else finalVal = parsed;
                } catch (Exception ignored) {
                }
            }
        }
        return finalVal;
    }
}