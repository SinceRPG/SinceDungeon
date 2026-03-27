package net.danh.sinceDungeon.editor;

import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.manager.DungeonManager;
import net.danh.sinceDungeon.utils.ColorUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jspecify.annotations.NonNull;

import java.io.File;
import java.util.*;

public class EditorGUI implements Listener {

    private final SinceDungeon plugin;

    public EditorGUI(SinceDungeon plugin) {
        this.plugin = plugin;
    }

    private String getMsg(String path) {
        return plugin.getMessagesFile().getString("editor." + path);
    }

    private String getMsg(String path, String def) {
        String res = plugin.getMessagesFile().getString("editor." + path);
        return (res == null || res.isEmpty()) ? def : res;
    }

    private String getWord(String key) {
        return getMsg("words." + key);
    }

    private void sendMessage(Player p, String key, String... placeholders) {
        String msg = getMsg("chat." + key);
        if (msg == null || msg.isEmpty()) return;

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

    private Material getNavItem() {
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

        // BUTTON SETTINGS
        inv.setItem(13, makeItem(Material.COMPARATOR, getMsg("items.settings"), plugin.getMessagesFile().getStringList("editor.items.settings_lore")));

        inv.setItem(22, makeItem(Material.WRITABLE_BOOK, getMsg("items.save"), null));
        inv.setItem(18, makeItem(getNavItem(), getMsg("items.back"), null));

        p.openInventory(inv);
    }

    // GIAO DIỆN SETTINGS PER-DUNGEON
    public void openSettingsMenu(Player p, EditorSession session) {
        session.setLastMenuOpener(player -> openSettingsMenu(player, session));
        Inventory inv = Bukkit.createInventory(new EditorHolder(session, "SETTINGS", 0), 27, ColorUtils.parse(getMsg("title.settings")));

        boolean keepInv = session.getConfig().contains("settings.keep-inventory-on-death") ? session.getConfig().getBoolean("settings.keep-inventory-on-death") : plugin.getConfigFile().getConfig().getBoolean("dungeon.gameplay.keep-inventory-on-death", true);
        boolean preventDrop = session.getConfig().contains("settings.prevent-item-dropping") ? session.getConfig().getBoolean("settings.prevent-item-dropping") : plugin.getConfigFile().getConfig().getBoolean("dungeon.gameplay.prevent-item-dropping", true);
        boolean blockPearls = session.getConfig().contains("settings.block-ender-pearls") ? session.getConfig().getBoolean("settings.block-ender-pearls") : plugin.getConfigFile().getConfig().getBoolean("dungeon.gameplay.block-ender-pearls", true);
        int kickDelay = session.getConfig().contains("settings.kick-delay-after-finish") ? session.getConfig().getInt("settings.kick-delay-after-finish") : plugin.getConfigFile().getInt("dungeon.gameplay.kick-delay-after-finish", 10);
        boolean forceWeather = session.getConfig().contains("settings.force-daylight-and-clear-weather") ? session.getConfig().getBoolean("settings.force-daylight-and-clear-weather") : plugin.getConfigFile().getConfig().getBoolean("dungeon.gameplay.force-daylight-and-clear-weather", true);
        boolean saveStats = session.getConfig().contains("settings.save-and-restore-stats") ? session.getConfig().getBoolean("settings.save-and-restore-stats") : plugin.getConfigFile().getConfig().getBoolean("dungeon.save-and-restore-stats", false);
        String deathAction = session.getConfig().contains("settings.death-action") ? session.getConfig().getString("settings.death-action") : plugin.getConfigFile().getString("dungeon.death-action", "RESPAWN");
        boolean clearMobDrops = session.getConfig().contains("settings.clear-mob-drops") ? session.getConfig().getBoolean("settings.clear-mob-drops") : plugin.getConfigFile().getConfig().getBoolean("dungeon.clear-mob-drops", true);

        inv.setItem(10, makeItem(Material.TOTEM_OF_UNDYING, getMsg("items.setting_keep_inv"), getLoreToggle(keepInv)));
        inv.setItem(11, makeItem(Material.BARRIER, getMsg("items.setting_prevent_drop"), getLoreToggle(preventDrop)));
        inv.setItem(12, makeItem(Material.ENDER_PEARL, getMsg("items.setting_block_pearls"), getLoreToggle(blockPearls)));

        List<String> delayLore = new ArrayList<>();
        delayLore.add("<gray>Current: <white>" + kickDelay + "s");
        delayLore.add("<yellow>Left Click to edit");
        inv.setItem(13, makeItem(Material.CLOCK, getMsg("items.setting_kick_delay"), delayLore));

        inv.setItem(14, makeItem(Material.SUNFLOWER, getMsg("items.setting_force_weather"), getLoreToggle(forceWeather)));
        inv.setItem(15, makeItem(Material.GOLDEN_APPLE, getMsg("items.setting_save_stats"), getLoreToggle(saveStats)));

        // THÊM: Click to cycle Death Action & Clear Mob Drops
        inv.setItem(16, makeItem(Material.SKELETON_SKULL, getMsg("items.setting_death_action"), Arrays.asList("<gray>Current: <white>" + deathAction.toUpperCase(), "<yellow>Left Click to toggle (RESPAWN/FAIL)")));
        inv.setItem(17, makeItem(Material.ROTTEN_FLESH, getMsg("items.setting_clear_drops"), getLoreToggle(clearMobDrops)));

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

        int slot = 0;
        for (String key : sec.getKeys(false)) {
            if (slot >= 45) break;
            String val = String.valueOf(sec.get(key));
            Material icon = Material.BOOK;
            String hint = getMsg("items.action_val_hint_edit");
            boolean isLocation = key.toLowerCase().contains("location") || key.equals("target") || key.equals("trigger") || key.equals("corner1") || key.equals("corner2") || key.equals("pos");
            boolean isList = sec.isList(key);

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
                default:
                    if (isLocation) {
                        icon = Material.COMPASS;
                        hint = isList ? getMsg("items.action_val_hint_loc_list") : getMsg("items.action_val_hint_loc_single");
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

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof EditorHolder) {
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
        if (!(e.getView().getTopInventory().getHolder() instanceof EditorHolder holder)) return;

        if (e.getClick() == ClickType.NUMBER_KEY || e.getClick() == ClickType.DOUBLE_CLICK || e.getClick() == ClickType.SWAP_OFFHAND) {
            e.setCancelled(true);
            return;
        }

        if (e.getClickedInventory() != e.getView().getTopInventory()) {
            if (e.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY || e.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
                e.setCancelled(true);
            }
            return;
        }

        e.setCancelled(true);

        ItemStack cur = e.getCurrentItem();
        if (cur == null || cur.getType() == Material.AIR) return;

        EditorManager manager = plugin.getEditorManager();
        final EditorSession session = holder.session();
        String menuType = holder.menuType();
        int page = holder.page();
        int slot = e.getRawSlot();

        if (menuType.equals("MAIN")) {
            if (slot == 48 && cur.getType() == getNavItem()) {
                openMainMenu(p, page - 1);
                return;
            }
            if (slot == 50 && cur.getType() == getNavItem()) {
                openMainMenu(p, page + 1);
                return;
            }

            if (slot < 45 && cur.getType() == Material.PAPER) {
                String name = getPlainText(cur.getItemMeta().displayName());
                manager.startEditing(p, name);
            } else if (slot == 49) {
                EditorSession tempSession = new EditorSession(plugin, p, null);
                tempSession.setLastMenuOpener(player -> openMainMenu(player, page));
                tempSession.awaitInput(EditorSession.InputType.CREATE_FILENAME, "create_filename", val -> plugin.getEditorManager().startEditing(p, val));
                plugin.getEditorListener().startListening(p, tempSession);
            }
            return;
        }

        if (session == null) return;

        switch (menuType) {
            case "SELECT_TYPE" -> {
                if (slot == 48 && cur.getType() == getNavItem()) {
                    openActionTypeSelector(p, session, page - 1);
                    return;
                }
                if (slot == 50 && cur.getType() == getNavItem()) {
                    openActionTypeSelector(p, session, page + 1);
                    return;
                }
                if (slot == 45) {
                    openActionList(p, session, session.getPage("ACTIONS"));
                    return;
                }

                if (slot < 45) {
                    NamespacedKey key = new NamespacedKey(plugin, "action_id");
                    ItemMeta meta = cur.getItemMeta();
                    if (meta != null && meta.getPersistentDataContainer().has(key, PersistentDataType.STRING)) {
                        String type = meta.getPersistentDataContainer().get(key, PersistentDataType.STRING);
                        DungeonManager.ActionMeta actMeta = plugin.getDungeonManager().getActionMeta(type);
                        if (actMeta != null) {
                            String newKey = type.toLowerCase() + "_" + System.currentTimeMillis();
                            String path = "stages." + session.getCurrentStage() + ".actions." + newKey;

                            session.getConfig().set(path + ".type", type);
                            for (Map.Entry<String, Object> entry : actMeta.defaults().entrySet()) {
                                session.getConfig().set(path + "." + entry.getKey(), entry.getValue());
                            }

                            session.setCurrentActionKey(newKey);
                            openActionEditor(p, session);
                        }
                    }
                }
            }

            case "DUNGEON" -> {
                if (slot == 10) {
                    session.awaitInput(EditorSession.InputType.EDIT_STRING, "edit_world_name", val -> {
                        session.getConfig().set("template-world", val);
                        sendMessage(p, "template_set", "<world>", val);
                        openDungeonMenu(p, session);
                    });
                    plugin.getEditorListener().startListening(p, session);
                } else if (slot == 20) {
                    boolean current = session.getConfig().getBoolean("public", false);
                    session.getConfig().set("public", !current);
                    sendMessage(p, "public_toggled", "<status>", String.valueOf(!current));
                    openDungeonMenu(p, session);
                } else if (slot == 12) openConditionList(p, session, session.getPage("CONDITIONS"));
                else if (slot == 13) openSettingsMenu(p, session);
                else if (slot == 14) openRewardMenu(p, session);
                else if (slot == 16) openStageList(p, session, session.getPage("STAGES"));
                else if (slot == 22) session.save();
                else if (slot == 18) openMainMenu(p, 0);
            }

            case "SETTINGS" -> {
                if (slot == 18) {
                    openDungeonMenu(p, session);
                    return;
                }

                String path = "";
                boolean isBool = true;

                if (slot == 10) path = "settings.keep-inventory-on-death";
                else if (slot == 11) path = "settings.prevent-item-dropping";
                else if (slot == 12) path = "settings.block-ender-pearls";
                else if (slot == 13) {
                    path = "settings.kick-delay-after-finish";
                    isBool = false;
                } else if (slot == 14) path = "settings.force-daylight-and-clear-weather";
                else if (slot == 15) path = "settings.save-and-restore-stats";
                else if (slot == 16) {
                    // Click to cycle Death Action
                    String current = session.getConfig().contains("settings.death-action") ? session.getConfig().getString("settings.death-action") : plugin.getConfigFile().getString("dungeon.death-action", "RESPAWN");
                    String next = current.equalsIgnoreCase("RESPAWN") ? "FAIL" : "RESPAWN";
                    session.getConfig().set("settings.death-action", next);
                    p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                    openSettingsMenu(p, session);
                    return;
                } else if (slot == 17) path = "settings.clear-mob-drops";

                if (!path.isEmpty()) {
                    if (isBool) {
                        boolean current;
                        if (path.equals("settings.save-and-restore-stats")) {
                            current = session.getConfig().contains(path) ? session.getConfig().getBoolean(path) : plugin.getConfigFile().getConfig().getBoolean("dungeon.save-and-restore-stats", false);
                        } else if (path.equals("settings.clear-mob-drops")) {
                            current = session.getConfig().contains(path) ? session.getConfig().getBoolean(path) : plugin.getConfigFile().getConfig().getBoolean("dungeon.clear-mob-drops", true);
                        } else {
                            current = session.getConfig().contains(path) ? session.getConfig().getBoolean(path) : plugin.getConfigFile().getConfig().getBoolean("dungeon.gameplay." + path.replace("settings.", ""), true);
                        }

                        session.getConfig().set(path, !current);
                        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                        openSettingsMenu(p, session);
                    } else {
                        final String finalPath = path;
                        session.awaitInput(EditorSession.InputType.EDIT_KICK_DELAY, "edit_kick_delay", val -> {
                            try {
                                int newDelay = Integer.parseInt(val);
                                session.getConfig().set(finalPath, newDelay);
                                sendMessage(p, "update_val", "<key>", "Kick Delay", "<val>", val);
                                openSettingsMenu(p, session);
                            } catch (Exception ex) {
                                sendMessage(p, "number_error");
                                openSettingsMenu(p, session);
                            }
                        });
                        plugin.getEditorListener().startListening(p, session);
                    }
                }
            }

            case "CONDITIONS" -> {
                if (slot == 48 && cur.getType() == getNavItem()) {
                    openConditionList(p, session, page - 1);
                    return;
                }
                if (slot == 50 && cur.getType() == getNavItem()) {
                    openConditionList(p, session, page + 1);
                    return;
                }

                if (slot == 49) {
                    session.awaitInput(EditorSession.InputType.EDIT_CONDITION_CHECK, "edit_condition_check", val -> {
                        String newKey = "cond_" + System.currentTimeMillis();
                        session.getConfig().set("conditions." + newKey + ".check", val);
                        session.getConfig().set("conditions." + newKey + ".msg", getWord("default_condition_msg"));
                        session.getConfig().set("conditions." + newKey + ".name", getWord("default_condition_name").replace("<key>", newKey));
                        openConditionList(p, session, page);
                    });
                    plugin.getEditorListener().startListening(p, session);
                } else if (slot == 45) {
                    openDungeonMenu(p, session);
                } else if (slot < 45 && cur.getType() == Material.NAME_TAG) {
                    ConfigurationSection sec = session.getConfig().getConfigurationSection("conditions");
                    if (sec != null) {
                        List<String> keys = new ArrayList<>(sec.getKeys(false));
                        int actualIdx = slot + page * 45;
                        if (actualIdx < keys.size()) {
                            String key = keys.get(actualIdx);
                            if (e.getClick() == ClickType.SHIFT_RIGHT) {
                                session.getConfig().set("conditions." + key, null);
                                openConditionList(p, session, page);
                            } else if (e.getClick() == ClickType.RIGHT) {
                                session.awaitInput(EditorSession.InputType.EDIT_STRING, "edit_condition_msg", val -> {
                                    session.getConfig().set("conditions." + key + ".msg", val);
                                    openConditionList(p, session, page);
                                });
                                plugin.getEditorListener().startListening(p, session);
                            } else if (e.getClick() == ClickType.LEFT) {
                                session.awaitInput(EditorSession.InputType.EDIT_CONDITION_CHECK, "edit_condition_check", val -> {
                                    session.getConfig().set("conditions." + key + ".check", val);
                                    openConditionList(p, session, page);
                                });
                                plugin.getEditorListener().startListening(p, session);
                            }
                        }
                    }
                }
            }

            case "REWARDS_MAIN" -> {
                if (cur.getType() == Material.CLOCK) openRewardTiers(p, session, session.getPage("REWARD_TIERS"));
                else if (cur.getType() == Material.CHEST) openRewardPool(p, session, session.getPage("REWARD_POOL"));
                else if (slot == 18) openDungeonMenu(p, session);
            }

            case "REWARD_TIERS" -> {
                if (slot == 48 && cur.getType() == getNavItem()) {
                    openRewardTiers(p, session, page - 1);
                    return;
                }
                if (slot == 50 && cur.getType() == getNavItem()) {
                    openRewardTiers(p, session, page + 1);
                    return;
                }

                if (slot == 49) {
                    session.awaitInput(EditorSession.InputType.EDIT_TIER, "edit_tier", val -> {
                        try {
                            String[] parts = val.split(" ");
                            if (parts.length < 2) throw new Exception();
                            int time = Integer.parseInt(parts[0]);
                            int amt = Integer.parseInt(parts[1]);
                            session.getConfig().set("rewards.tiers." + time, amt);
                            sendMessage(p, "tier_added", "<time>", String.valueOf(time), "<amount>", String.valueOf(amt));
                            openRewardTiers(p, session, page);
                        } catch (Exception ex) {
                            sendMessage(p, "format_tier_error");
                            new EditorGUI(plugin).openRewardTiers(p, session, page);
                        }
                    });
                    plugin.getEditorListener().startListening(p, session);
                } else if (slot == 45) {
                    openRewardMenu(p, session);
                } else if (slot < 45 && cur.getType() == Material.CLOCK) {
                    ConfigurationSection tiers = session.getConfig().getConfigurationSection("rewards.tiers");
                    if (tiers != null) {
                        List<String> keys = new ArrayList<>(tiers.getKeys(false));
                        keys.sort(Comparator.comparingInt(Integer::parseInt));
                        int actualIdx = slot + page * 45;

                        if (actualIdx < keys.size()) {
                            String timeStr = keys.get(actualIdx);
                            if (e.getClick() == ClickType.RIGHT) {
                                session.getConfig().set("rewards.tiers." + timeStr, null);
                                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                                openRewardTiers(p, session, page);
                            } else if (e.getClick() == ClickType.LEFT) {
                                session.awaitInput(EditorSession.InputType.EDIT_NUMBER, "edit_number", val -> {
                                    try {
                                        int newAmount = Integer.parseInt(val);
                                        session.getConfig().set("rewards.tiers." + timeStr, newAmount);
                                        sendMessage(p, "update_val", "<key>", getWord("chest_amount").replace("<time>", timeStr), "<val>", val);
                                        openRewardTiers(p, session, page);
                                    } catch (Exception ex) {
                                        sendMessage(p, "number_error");
                                        new EditorGUI(plugin).openRewardTiers(p, session, page);
                                    }
                                });
                                plugin.getEditorListener().startListening(p, session);
                            }
                        }
                    }
                }
            }

            case "REWARD_POOL" -> {
                if (slot == 48 && cur.getType() == getNavItem()) {
                    openRewardPool(p, session, page - 1);
                    return;
                }
                if (slot == 50 && cur.getType() == getNavItem()) {
                    openRewardPool(p, session, page + 1);
                    return;
                }

                if (slot == 49) {
                    String newKey = "reward_" + System.currentTimeMillis();
                    session.getConfig().set("rewards.pool." + newKey + ".type", "ITEM");
                    session.getConfig().set("rewards.pool." + newKey + ".value", "DIAMOND:1");
                    session.getConfig().set("rewards.pool." + newKey + ".chance", 100.0);
                    sendMessage(p, "reward_added");
                    openRewardPool(p, session, page);
                } else if (slot == 45) {
                    openRewardMenu(p, session);
                } else if (slot < 45 && cur.getType() == Material.BUNDLE) {
                    ConfigurationSection pool = session.getConfig().getConfigurationSection("rewards.pool");
                    if (pool != null) {
                        List<String> keys = new ArrayList<>(pool.getKeys(false));
                        int actualIdx = slot + page * 45;
                        if (actualIdx < keys.size()) {
                            String key = keys.get(actualIdx);
                            if (e.getClick() == ClickType.SHIFT_RIGHT) {
                                session.getConfig().set("rewards.pool." + key, null);
                                openRewardPool(p, session, page);
                            } else if (e.getClick() == ClickType.LEFT) {
                                session.setCurrentRewardKey(key);
                                openRewardEditor(p, session);
                            }
                        }
                    }
                }
            }

            case "EDIT_REWARD" -> {
                if (slot == 18) {
                    openRewardPool(p, session, session.getPage("REWARD_POOL"));
                    return;
                }

                String currentKey = session.getCurrentRewardKey();
                String path = "rewards.pool." + currentKey;
                String currentType = session.getConfig().getString(path + ".type", "ITEM");

                if (slot == 10 && e.getClick() == ClickType.LEFT) {
                    String nextType = switch (currentType) {
                        case "ITEM" -> "COMMAND";
                        case "COMMAND" -> "MMOITEM";
                        default -> "ITEM";
                    };
                    session.getConfig().set(path + ".type", nextType);
                    sendMessage(p, "type_changed", "<type>", nextType);
                    openRewardEditor(p, session);
                } else if (slot == 12) {
                    if (e.getClick() == ClickType.RIGHT) {
                        ItemStack hand = p.getInventory().getItemInMainHand();
                        if (hand.getType() != Material.AIR) {
                            String val = hand.getType().name() + ":" + hand.getAmount();
                            session.getConfig().set(path + ".value", val);
                            sendMessage(p, "item_set_hand", "<item>", val);
                            openRewardEditor(p, session);
                        } else sendMessage(p, "hand_empty");
                    } else if (e.getClick() == ClickType.LEFT) {
                        session.awaitInput(EditorSession.InputType.EDIT_STRING, "edit_reward_value_" + currentType.toLowerCase(), val -> {
                            session.getConfig().set(path + ".value", val);
                            sendMessage(p, "update_val", "<key>", getWord("value"), "<val>", val);
                            new EditorGUI(plugin).openRewardEditor(p, session);
                        });
                        plugin.getEditorListener().startListening(p, session);
                    }
                } else if (slot == 14 && e.getClick() == ClickType.LEFT) {
                    session.awaitInput(EditorSession.InputType.EDIT_NUMBER, "edit_reward_chance", val -> {
                        try {
                            double chance = Double.parseDouble(val);
                            session.getConfig().set(path + ".chance", chance);
                            new EditorGUI(plugin).openRewardEditor(p, session);
                        } catch (Exception ex) {
                            sendMessage(p, "number_error");
                            new EditorGUI(plugin).openRewardEditor(p, session);
                        }
                    });
                    plugin.getEditorListener().startListening(p, session);
                } else if (slot == 16 && e.getClick() == ClickType.LEFT) {
                    session.awaitInput(EditorSession.InputType.EDIT_STRING, "edit_reward_name", val -> {
                        session.getConfig().set(path + ".name", val);
                        new EditorGUI(plugin).openRewardEditor(p, session);
                    });
                    plugin.getEditorListener().startListening(p, session);
                }
            }

            case "STAGES" -> {
                if (slot == 48 && cur.getType() == getNavItem()) {
                    openStageList(p, session, page - 1);
                    return;
                }
                if (slot == 50 && cur.getType() == getNavItem()) {
                    openStageList(p, session, page + 1);
                    return;
                }

                if (slot == 49) {
                    ConfigurationSection sec = session.getConfig().getConfigurationSection("stages");
                    int next = 1;
                    if (sec != null) {
                        for (String k : sec.getKeys(false)) {
                            try {
                                int id = Integer.parseInt(k);
                                if (id >= next) next = id + 1;
                            } catch (Exception ignored) {
                            }
                        }
                    }
                    session.getConfig().createSection("stages." + next + ".actions");
                    openStageList(p, session, page);
                } else if (slot == 45) {
                    openDungeonMenu(p, session);
                } else if (slot < 45 && cur.getType() == Material.FILLED_MAP) {
                    ConfigurationSection sec = session.getConfig().getConfigurationSection("stages");
                    if (sec != null) {
                        List<String> keys = new ArrayList<>(sec.getKeys(false));
                        keys.sort(Comparator.comparingInt(s -> {
                            try {
                                return Integer.parseInt(s);
                            } catch (Exception ex) {
                                return 0;
                            }
                        }));
                        int actualIdx = slot + page * 45;
                        if (actualIdx < keys.size()) {
                            String stage = keys.get(actualIdx);
                            session.setCurrentStage(stage);
                            openActionList(p, session, session.getPage("ACTIONS"));
                        }
                    }
                }
            }

            case "ACTIONS" -> {
                if (slot == 48 && cur.getType() == getNavItem()) {
                    openActionList(p, session, page - 1);
                    return;
                }
                if (slot == 50 && cur.getType() == getNavItem()) {
                    openActionList(p, session, page + 1);
                    return;
                }

                if (slot == 49) {
                    openActionTypeSelector(p, session, session.getPage("SELECT_TYPE"));
                } else if (slot == 45) {
                    openStageList(p, session, session.getPage("STAGES"));
                } else if (slot < 45 && cur.getType() != Material.EMERALD && cur.getType() != getNavItem()) {
                    ConfigurationSection sec = session.getConfig().getConfigurationSection("stages." + session.getCurrentStage() + ".actions");
                    if (sec != null) {
                        List<String> keys = new ArrayList<>(sec.getKeys(false));
                        int actualIdx = slot + page * 45;
                        if (actualIdx < keys.size()) {
                            String key = keys.get(actualIdx);
                            if (e.getClick() == ClickType.SHIFT_RIGHT) {
                                session.getConfig().set("stages." + session.getCurrentStage() + ".actions." + key, null);
                                openActionList(p, session, page);
                            } else if (e.getClick() == ClickType.LEFT) {
                                session.setCurrentActionKey(key);
                                openActionEditor(p, session);
                            }
                        }
                    }
                }
            }

            case "EDIT_ACTION" -> {
                if (slot == 45) {
                    openActionList(p, session, session.getPage("ACTIONS"));
                    return;
                }
                if (cur.getType() == Material.BARRIER) return;

                String key = getPlainText(cur.getItemMeta().displayName());
                boolean isLocation = key.toLowerCase().contains("location") || key.equals("target") || key.equals("trigger") || key.equals("corner1") || key.equals("corner2") || key.equals("pos");

                String fullPath = "stages." + session.getCurrentStage() + ".actions." + session.getCurrentActionKey() + "." + key;
                boolean isList = session.getConfig().isList(fullPath);

                EditorSession.InputType inputType = EditorSession.InputType.EDIT_STRING;
                if (isLocation) {
                    inputType = isList ? EditorSession.InputType.EDIT_LOCATION_LIST : EditorSession.InputType.EDIT_LOCATION;
                } else if (isList) {
                    inputType = EditorSession.InputType.EDIT_LIST;
                } else if (key.equals("amount") || key.equals("radius") || key.equals("chance") || key.equals("level")) {
                    inputType = EditorSession.InputType.EDIT_NUMBER;
                }

                if (isLocation) {
                    if (e.getClick() == ClickType.SHIFT_RIGHT) {
                        if (isList) {
                            session.getConfig().set(fullPath, new ArrayList<>());
                        } else {
                            session.getConfig().set(fullPath, null);
                        }
                        sendMessage(p, "loc_cleared");
                        openActionEditor(p, session);
                        return;
                    } else if (e.getClick() == ClickType.RIGHT) {
                        String locStr = locToString(p.getLocation());
                        if (isList) {
                            List<String> list = session.getConfig().getStringList(fullPath);
                            list.add(locStr);
                            session.getConfig().set(fullPath, list);
                        } else {
                            session.getConfig().set(fullPath, locStr);
                        }
                        sendMessage(p, "update_loc", "<loc>", locStr);
                        openActionEditor(p, session);
                        return;
                    }
                }

                if (e.getClick() == ClickType.LEFT) {
                    String promptKey = "edit_action_" + key.toLowerCase();

                    session.awaitInput(inputType, promptKey, val -> {
                        Object finalVal = getFinalVal(val);
                        String clearKw = plugin.getMessagesFile().getString("editor.words.clear", "clear");

                        if (isList) {
                            List<String> list = session.getConfig().getStringList(fullPath);
                            if (val.equalsIgnoreCase(clearKw)) list.clear();
                            else list.add(val);
                            session.getConfig().set(fullPath, list);
                        } else {
                            session.getConfig().set(fullPath, finalVal);
                        }

                        sendMessage(p, "update_val", "<key>", key, "<val>", val);
                        new EditorGUI(plugin).openActionEditor(p, session);
                    });
                    plugin.getEditorListener().startListening(p, session);
                }
            }
        }
    }

    private @NonNull Object getFinalVal(String val) {
        Object finalVal = val;
        if (val.equalsIgnoreCase("true")) finalVal = true;
        else if (val.equalsIgnoreCase("false")) finalVal = false;
        else {
            try {
                finalVal = Integer.parseInt(val);
            } catch (Exception e1) {
                try {
                    finalVal = Double.parseDouble(val);
                } catch (Exception ignored) {
                }
            }
        }
        return finalVal;
    }
}