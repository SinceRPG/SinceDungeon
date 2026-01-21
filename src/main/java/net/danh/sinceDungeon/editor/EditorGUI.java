package net.danh.sinceDungeon.editor;

import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.manager.DungeonManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.util.*;

public class EditorGUI implements Listener {
    private final SinceDungeon plugin;

    public EditorGUI(SinceDungeon plugin) {
        this.plugin = plugin;
    }

    // --- UTILS ---
    private String getMsg(String key) {
        return plugin.getMessagesFile().getString("editor." + key);
    }

    private void sendMessage(Player p, String key, String... placeholders) {
        String msg = getMsg("chat." + key);
        if (msg == null) return;
        for (int i = 0; i < placeholders.length; i += 2) {
            msg = msg.replace(placeholders[i], placeholders[i + 1]);
        }
        p.sendMessage(MiniMessage.miniMessage().deserialize(msg));
    }

    private ItemStack makeItem(Material mat, String nameRaw, List<String> loreRaw) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (nameRaw != null) meta.displayName(MiniMessage.miniMessage().deserialize("<!i>" + nameRaw));
            List<Component> lore = new ArrayList<>();
            if (loreRaw != null) {
                for (String s : loreRaw) {
                    lore.add(MiniMessage.miniMessage().deserialize("<!i>" + s));
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

    private boolean isTitle(Component guiTitleComp, String configKey, String placeholder) {
        String guiTitle = getPlainText(guiTitleComp);
        String rawConfig = getMsg(configKey);
        if (rawConfig == null) return false;

        String configPlain = getPlainText(MiniMessage.miniMessage().deserialize(rawConfig));

        if (placeholder == null) {
            return guiTitle.equals(configPlain);
        }

        String[] parts = configPlain.split(placeholder);
        int lastIndex = 0;
        for (String part : parts) {
            if (part.isEmpty()) continue;
            int index = guiTitle.indexOf(part, lastIndex);
            if (index == -1) return false;
            lastIndex = index + part.length();
        }
        return true;
    }

    private String locToString(Location l) {
        return String.format("%.1f,%.1f,%.1f", l.getX(), l.getY(), l.getZ());
    }

    // --- GUI OPENERS ---

    public void openMainMenu(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, MiniMessage.miniMessage().deserialize(getMsg("title.main")));
        File folder = new File(plugin.getDataFolder(), "dungeons");
        if (folder.exists()) {
            File[] files = folder.listFiles((d, n) -> n.endsWith(".yml"));
            if (files != null) {
                for (File f : files) {
                    inv.addItem(makeItem(Material.PAPER, "<yellow>" + f.getName().replace(".yml", ""), Collections.singletonList(getMsg("items.click_edit"))));
                }
            }
        }
        inv.setItem(49, makeItem(Material.EMERALD_BLOCK, getMsg("items.create_new"), null));
        p.openInventory(inv);
    }

    public void openDungeonMenu(Player p, EditorSession session) {
        session.setLastMenuOpener(player -> openDungeonMenu(player, session));
        String title = getMsg("title.dungeon").replace("<name>", session.getFile().getName());
        Inventory inv = Bukkit.createInventory(null, 27, MiniMessage.miniMessage().deserialize(title));

        String world = session.getConfig().getString("template-world", "Chưa đặt");
        boolean isPublic = session.getConfig().getBoolean("public", false);
        String publicStatus = isPublic ? "<green>TRUE" : "<red>FALSE";

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

        inv.setItem(22, makeItem(Material.WRITABLE_BOOK, getMsg("items.save"), null));
        inv.setItem(18, makeItem(Material.ARROW, getMsg("items.back"), null));
        p.openInventory(inv);
    }

    public void openConditionList(Player p, EditorSession session) {
        session.setLastMenuOpener(player -> openConditionList(player, session));
        Inventory inv = Bukkit.createInventory(null, 54, MiniMessage.miniMessage().deserialize(getMsg("title.conditions")));

        List<?> conditions = session.getConfig().getList("conditions");
        if (conditions == null) conditions = new ArrayList<>();

        int index = 0;
        for (Object obj : conditions) {
            String check = "???";
            String msg = "Mặc định";

            if (obj instanceof String) {
                check = (String) obj;
            } else if (obj instanceof Map) {
                // [FIXED] Lấy thủ công để tránh lỗi getOrDefault
                Map<?, ?> map = (Map<?, ?>) obj;
                Object cVal = map.get("check");
                Object mVal = map.get("msg");

                check = (cVal != null) ? cVal.toString() : "???";
                msg = (mVal != null) ? mVal.toString() : "Mặc định";
            }

            String name = getMsg("items.condition_item").replace("<index>", String.valueOf(index + 1));
            List<String> lore = new ArrayList<>();
            for (String s : plugin.getMessagesFile().getStringList("editor.items.condition_lore"))
                lore.add(s.replace("<check>", check).replace("<msg>", msg));

            inv.setItem(index++, makeItem(Material.NAME_TAG, name, lore));
        }
        inv.setItem(49, makeItem(Material.EMERALD, getMsg("items.add_condition"), null));
        inv.setItem(45, makeItem(Material.ARROW, getMsg("items.back"), null));
        p.openInventory(inv);
    }

    public void openRewardMenu(Player p, EditorSession session) {
        session.setLastMenuOpener(player -> openRewardMenu(player, session));
        Inventory inv = Bukkit.createInventory(null, 27, MiniMessage.miniMessage().deserialize(getMsg("title.rewards_main")));
        inv.setItem(11, makeItem(Material.CLOCK, getMsg("items.reward_tiers_item"), null));
        inv.setItem(15, makeItem(Material.CHEST, getMsg("items.reward_pool_item"), null));
        inv.setItem(18, makeItem(Material.ARROW, getMsg("items.back"), null));
        p.openInventory(inv);
    }

    public void openRewardTiers(Player p, EditorSession session) {
        session.setLastMenuOpener(player -> openRewardTiers(player, session));
        Inventory inv = Bukkit.createInventory(null, 54, MiniMessage.miniMessage().deserialize(getMsg("title.reward_tiers")));
        ConfigurationSection tiers = session.getConfig().getConfigurationSection("rewards.tiers");
        if (tiers != null) {
            List<String> keys = new ArrayList<>(tiers.getKeys(false));
            keys.sort(Comparator.comparingInt(Integer::parseInt));
            for (String key : keys) {
                int amount = tiers.getInt(key);
                String name = getMsg("items.tier_item").replace("<time>", key);
                List<String> lore = new ArrayList<>();
                for (String s : plugin.getMessagesFile().getStringList("editor.items.tier_lore"))
                    lore.add(s.replace("<amount>", String.valueOf(amount)));
                inv.addItem(makeItem(Material.CLOCK, name, lore));
            }
        }
        inv.setItem(49, makeItem(Material.EMERALD, getMsg("items.add_tier"), null));
        inv.setItem(45, makeItem(Material.ARROW, getMsg("items.back"), null));
        p.openInventory(inv);
    }

    public void openRewardPool(Player p, EditorSession session) {
        session.setLastMenuOpener(player -> openRewardPool(player, session));
        Inventory inv = Bukkit.createInventory(null, 54, MiniMessage.miniMessage().deserialize(getMsg("title.reward_pool")));
        List<Map<?, ?>> pool = session.getConfig().getMapList("rewards.pool");
        int index = 0;
        for (Map<?, ?> raw : pool) {
            Map<String, Object> map = (Map<String, Object>) raw;
            String type = (String) map.getOrDefault("type", "UNKNOWN");
            String val = (String) map.getOrDefault("value", "???");
            String name = getMsg("items.pool_item").replace("<index>", String.valueOf(index + 1));
            List<String> lore = new ArrayList<>();
            for (String s : plugin.getMessagesFile().getStringList("editor.items.pool_lore"))
                lore.add(s.replace("<type>", type).replace("<val>", val));
            inv.setItem(index++, makeItem(Material.BUNDLE, name, lore));
        }
        inv.setItem(49, makeItem(Material.EMERALD, getMsg("items.add_pool_item"), null));
        inv.setItem(45, makeItem(Material.ARROW, getMsg("items.back"), null));
        p.openInventory(inv);
    }

    public void openRewardEditor(Player p, EditorSession session) {
        session.setLastMenuOpener(player -> openRewardEditor(player, session));
        int idx = session.getCurrentRewardIndex();
        List<Map<?, ?>> pool = session.getConfig().getMapList("rewards.pool");
        if (idx < 0 || idx >= pool.size()) {
            openRewardPool(p, session);
            return;
        }

        Map<?, ?> rawAny = pool.get(idx);
        Map<String, Object> raw = new HashMap<>();
        for (Map.Entry<?, ?> e : rawAny.entrySet()) raw.put(e.getKey().toString(), e.getValue());

        String type = (String) raw.getOrDefault("type", "ITEM");
        String value = (String) raw.getOrDefault("value", "AIR:1");
        double chance = (raw.get("chance") instanceof Number) ? ((Number) raw.get("chance")).doubleValue() : 100.0;
        String displayName = (String) raw.getOrDefault("name", "<gray>Mặc định");

        String title = getMsg("title.edit_reward").replace("<index>", String.valueOf(idx + 1));
        Inventory inv = Bukkit.createInventory(null, 27, MiniMessage.miniMessage().deserialize(title));

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

        inv.setItem(18, makeItem(Material.ARROW, getMsg("items.back"), null));
        p.openInventory(inv);
    }

    public void openStageList(Player p, EditorSession session) {
        session.setLastMenuOpener(player -> openStageList(player, session));
        Inventory inv = Bukkit.createInventory(null, 54, MiniMessage.miniMessage().deserialize(getMsg("title.stages")));
        ConfigurationSection stages = session.getConfig().getConfigurationSection("stages");
        if (stages != null) {
            List<String> keys = new ArrayList<>(stages.getKeys(false));
            keys.sort(Comparator.comparingInt(s -> {
                try {
                    return Integer.parseInt(s);
                } catch (Exception e) {
                    return 0;
                }
            }));
            for (String key : keys) {
                String name = getMsg("items.stage_item").replace("<stage>", key);
                inv.addItem(makeItem(Material.FILLED_MAP, name, plugin.getMessagesFile().getStringList("editor.items.stage_lore")));
            }
        }
        inv.setItem(49, makeItem(Material.EMERALD, getMsg("items.add_stage"), null));
        inv.setItem(45, makeItem(Material.ARROW, getMsg("items.back"), null));
        p.openInventory(inv);
    }

    public void openActionList(Player p, EditorSession session) {
        session.setLastMenuOpener(player -> openActionList(player, session));
        String title = getMsg("title.actions").replace("<stage>", session.getCurrentStage());
        Inventory inv = Bukkit.createInventory(null, 54, MiniMessage.miniMessage().deserialize(title));
        List<Map<?, ?>> actions = session.getConfig().getMapList("stages." + session.getCurrentStage() + ".actions");
        int index = 0;
        for (Map<?, ?> raw : actions) {
            Map<String, Object> map = (Map<String, Object>) raw;
            String type = (String) map.getOrDefault("type", "UNKNOWN");
            String name = getMsg("items.action_item").replace("<index>", String.valueOf(index + 1));
            List<String> lore = new ArrayList<>();
            for (String s : plugin.getMessagesFile().getStringList("editor.items.action_lore"))
                lore.add(s.replace("<type>", type));
            inv.setItem(index++, makeItem(Material.PAPER, name, lore));
        }
        inv.setItem(49, makeItem(Material.EMERALD, getMsg("items.add_action"), null));
        inv.setItem(45, makeItem(Material.ARROW, getMsg("items.back"), null));
        p.openInventory(inv);
    }

    public void openActionEditor(Player p, EditorSession session) {
        session.setLastMenuOpener(player -> openActionEditor(player, session));
        String title = getMsg("title.edit_action").replace("<index>", String.valueOf(session.getCurrentActionIndex() + 1));
        Inventory inv = Bukkit.createInventory(null, 54, MiniMessage.miniMessage().deserialize(title));
        List<Map<?, ?>> list = session.getConfig().getMapList("stages." + session.getCurrentStage() + ".actions");
        if (session.getCurrentActionIndex() >= list.size()) {
            openActionList(p, session);
            return;
        }

        Map<?, ?> rawMap = list.get(session.getCurrentActionIndex());
        int slot = 0;
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            String key = entry.getKey().toString();
            String val = entry.getValue().toString();
            Material icon = Material.BOOK;
            String hint = getMsg("items.action_val_hint_edit");
            boolean isLocation = key.toLowerCase().contains("location") || key.equals("target") || key.equals("trigger") || key.equals("corner1") || key.equals("corner2") || key.equals("pos");
            boolean isList = val.startsWith("[");
            if (key.equals("type")) {
                icon = Material.BARRIER;
                hint = getMsg("items.action_type_cant_edit");
            } else if (key.equals("amount")) icon = Material.GOLD_NUGGET;
            else if (key.equals("mob")) icon = Material.CREEPER_HEAD;
            else if (isLocation) {
                icon = Material.COMPASS;
                hint = isList ? getMsg("items.action_val_hint_loc") + " | " + getMsg("items.action_val_hint_loc_clear") : getMsg("items.action_val_hint_loc_replace");
            }
            inv.setItem(slot++, makeItem(icon, "<gold>" + key, Arrays.asList("<gray>Value: <white>" + val, hint)));
        }
        inv.setItem(45, makeItem(Material.ARROW, getMsg("items.back"), null));
        p.openInventory(inv);
    }

    public void openActionTypeSelector(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, MiniMessage.miniMessage().deserialize(getMsg("title.select_type")));
        for (String type : plugin.getDungeonManager().getRegisteredActions()) {
            DungeonManager.ActionMeta meta = plugin.getDungeonManager().getActionMeta(type);
            inv.addItem(makeItem(meta.icon, type, Collections.singletonList("<gray>" + meta.description)));
        }
        inv.setItem(45, makeItem(Material.ARROW, getMsg("items.cancel"), null));
        p.openInventory(inv);
    }

    // --- EVENT HANDLING ---
    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;

        ItemStack cur = e.getCurrentItem();
        if (cur == null || cur.getType() == Material.AIR) return;

        EditorManager manager = plugin.getEditorManager();
        EditorSession session = manager.getSession(p);
        Component titleComp = e.getView().title();

        // 1. MAIN LIST
        if (isTitle(titleComp, "title.main", null)) {
            e.setCancelled(true);
            if (cur.getType() == Material.PAPER) {
                String name = getPlainText(cur.getItemMeta().displayName());
                manager.startEditing(p, name);
            } else if (cur.getType() == Material.EMERALD_BLOCK) {
                EditorSession tempSession = new EditorSession(plugin, p, null);
                tempSession.setLastMenuOpener(player -> openMainMenu(player));
                plugin.getEditorListener().startListening(p);
                tempSession.awaitInput(EditorSession.InputType.CREATE_FILENAME, val -> plugin.getEditorManager().startEditing(p, val));
            }
            return;
        }

        if (session == null) return;

        // 2. DASHBOARD
        if (isTitle(titleComp, "title.dungeon", "<name>")) {
            e.setCancelled(true);
            int slot = e.getRawSlot();
            if (slot == 10) { // World
                plugin.getEditorListener().startListening(p);
                EditorSession fs = session;
                fs.awaitInput(EditorSession.InputType.EDIT_VALUE, val -> {
                    fs.getConfig().set("template-world", val);
                    sendMessage(p, "template_set", "<world>", val);
                    openDungeonMenu(p, fs);
                });
            } else if (slot == 20) { // Public Toggle
                boolean current = session.getConfig().getBoolean("public", false);
                session.getConfig().set("public", !current);
                sendMessage(p, "public_toggled", "<status>", String.valueOf(!current));
                openDungeonMenu(p, session);
            } else if (slot == 12) openConditionList(p, session);
            else if (slot == 14) openRewardMenu(p, session);
            else if (slot == 16) openStageList(p, session);
            else if (slot == 22) session.save();
            else if (slot == 18) openMainMenu(p);
            return;
        }

        // 3. CONDITIONS
        if (isTitle(titleComp, "title.conditions", null)) {
            e.setCancelled(true);
            int slot = e.getRawSlot();
            if (slot == 49) { // ADD
                sendMessage(p, "condition_val_hint_check");
                plugin.getEditorListener().startListening(p);
                EditorSession fs = session;
                fs.awaitInput(EditorSession.InputType.EDIT_VALUE, val -> {
                    Map<String, String> newCond = new HashMap<>();
                    newCond.put("check", val);
                    newCond.put("msg", "Điều kiện chưa đạt!");

                    List<Object> list = (List<Object>) fs.getConfig().getList("conditions");
                    if (list == null) list = new ArrayList<>();
                    list.add(newCond);

                    fs.getConfig().set("conditions", list);
                    openConditionList(p, fs);
                });
            } else if (slot == 45) { // BACK
                openDungeonMenu(p, session);
            } else if (cur.getType() == Material.NAME_TAG) { // ITEM
                int idx = slot;
                List<Object> list = (List<Object>) session.getConfig().getList("conditions");
                if (list != null && idx < list.size()) {
                    if (e.isShiftClick() && e.isRightClick()) { // DEL
                        list.remove(idx);
                        session.getConfig().set("conditions", list);
                        openConditionList(p, session);
                    } else if (e.isRightClick()) { // EDIT MSG
                        sendMessage(p, "condition_val_hint_msg");
                        plugin.getEditorListener().startListening(p);
                        EditorSession fs = session;
                        fs.awaitInput(EditorSession.InputType.EDIT_VALUE, val -> {
                            updateCondition(fs, idx, "msg", val.equalsIgnoreCase("null") ? null : val);
                            openConditionList(p, fs);
                        });
                    } else { // EDIT CHECK
                        sendMessage(p, "condition_val_hint_check");
                        plugin.getEditorListener().startListening(p);
                        EditorSession fs = session;
                        fs.awaitInput(EditorSession.InputType.EDIT_VALUE, val -> {
                            updateCondition(fs, idx, "check", val);
                            openConditionList(p, fs);
                        });
                    }
                }
            }
            return;
        }

        // 4. REWARDS MENU
        if (isTitle(titleComp, "title.rewards_main", null)) {
            e.setCancelled(true);
            if (cur.getType() == Material.CLOCK) openRewardTiers(p, session);
            else if (cur.getType() == Material.CHEST) openRewardPool(p, session);
            else if (cur.getType() == Material.ARROW) openDungeonMenu(p, session);
            return;
        }

        // 5. REWARD TIERS
        if (isTitle(titleComp, "title.reward_tiers", null)) {
            e.setCancelled(true);
            if (e.getRawSlot() == 49) { // ADD
                sendMessage(p, "format_tier_error");
                plugin.getEditorListener().startListening(p);
                EditorSession fs = session;
                fs.awaitInput(EditorSession.InputType.EDIT_VALUE, val -> {
                    try {
                        String[] parts = val.split(" ");
                        if (parts.length < 2) throw new Exception();
                        int time = Integer.parseInt(parts[0]);
                        int amt = Integer.parseInt(parts[1]);
                        fs.getConfig().set("rewards.tiers." + time, amt);
                        sendMessage(p, "tier_added", "<time>", String.valueOf(time), "<amount>", String.valueOf(amt));
                        openRewardTiers(p, fs);
                    } catch (Exception ex) {
                        sendMessage(p, "format_tier_error");
                        new EditorGUI(plugin).openRewardTiers(p, fs);
                    }
                });
            } else if (e.getRawSlot() == 45) { // BACK
                openRewardMenu(p, session);
            } else if (cur.getType() == Material.CLOCK) {
                String name = getPlainText(cur.getItemMeta().displayName());
                String timeStr = name.replaceAll("[^0-9]", "");
                if (e.isRightClick()) {
                    session.getConfig().set("rewards.tiers." + timeStr, null);
                    p.playSound(p.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 1f, 1f);
                    openRewardTiers(p, session);
                } else {
                    sendMessage(p, "action_val_hint_edit");
                    plugin.getEditorListener().startListening(p);
                    EditorSession fs = session;
                    fs.awaitInput(EditorSession.InputType.EDIT_VALUE, val -> {
                        try {
                            int newAmount = Integer.parseInt(val);
                            fs.getConfig().set("rewards.tiers." + timeStr, newAmount);
                            sendMessage(p, "update_val", "<key>", "Số rương (" + timeStr + "s)", "<val>", val);
                            openRewardTiers(p, fs);
                        } catch (Exception ex) {
                            sendMessage(p, "number_error");
                            new EditorGUI(plugin).openRewardTiers(p, fs);
                        }
                    });
                }
            }
            return;
        }

        // 6. REWARD POOL
        if (isTitle(titleComp, "title.reward_pool", null)) {
            e.setCancelled(true);
            if (e.getRawSlot() == 49) { // ADD
                List<Map<?, ?>> pool = session.getConfig().getMapList("rewards.pool");
                Map<String, Object> newR = new HashMap<>();
                newR.put("type", "ITEM");
                newR.put("value", "DIAMOND:1");
                newR.put("chance", 100.0);
                pool.add(newR);
                session.getConfig().set("rewards.pool", pool);
                sendMessage(p, "reward_added");
                openRewardPool(p, session);
            } else if (e.getRawSlot() == 45) { // BACK
                openRewardMenu(p, session);
            } else if (cur.getType() == Material.BUNDLE) { // CLICK
                int idx = e.getRawSlot();
                if (e.isShiftClick() && e.isRightClick()) { // DEL
                    List<Map<?, ?>> pool = session.getConfig().getMapList("rewards.pool");
                    if (idx < pool.size()) {
                        pool.remove(idx);
                        session.getConfig().set("rewards.pool", pool);
                        openRewardPool(p, session);
                    }
                } else { // EDIT
                    session.setCurrentRewardIndex(idx);
                    openRewardEditor(p, session);
                }
            }
            return;
        }

        // 7. REWARD EDITOR
        if (isTitle(titleComp, "title.edit_reward", "<index>")) {
            e.setCancelled(true);
            if (e.getRawSlot() == 18) {
                openRewardPool(p, session);
                return;
            }

            if (e.getRawSlot() == 10) { // TYPE
                updateRewardData(session, "type", null);
                openRewardEditor(p, session);
            } else if (e.getRawSlot() == 12) { // VALUE
                if (e.isRightClick()) {
                    ItemStack hand = p.getInventory().getItemInMainHand();
                    if (hand.getType() != Material.AIR) {
                        String val = hand.getType().name() + ":" + hand.getAmount();
                        updateRewardData(session, "value", val);
                        sendMessage(p, "item_set_hand", "<item>", val);
                        openRewardEditor(p, session);
                    } else sendMessage(p, "hand_empty");
                    return;
                }
                plugin.getEditorListener().startListening(p);
                EditorSession fs = session;
                fs.awaitInput(EditorSession.InputType.EDIT_VALUE, val -> {
                    updateRewardData(fs, "value", val);
                    sendMessage(p, "update_val", "<key>", "Value", "<val>", val);
                    new EditorGUI(plugin).openRewardEditor(p, fs);
                });
            } else if (e.getRawSlot() == 14) { // CHANCE
                plugin.getEditorListener().startListening(p);
                EditorSession fs = session;
                fs.awaitInput(EditorSession.InputType.EDIT_VALUE, val -> {
                    try {
                        double chance = Double.parseDouble(val);
                        updateRewardData(fs, "chance", chance);
                        new EditorGUI(plugin).openRewardEditor(p, fs);
                    } catch (Exception ex) {
                        sendMessage(p, "number_error");
                        new EditorGUI(plugin).openRewardEditor(p, fs);
                    }
                });
            } else if (e.getRawSlot() == 16) { // NAME
                plugin.getEditorListener().startListening(p);
                EditorSession fs = session;
                fs.awaitInput(EditorSession.InputType.EDIT_VALUE, val -> {
                    updateRewardData(fs, "name", val);
                    new EditorGUI(plugin).openRewardEditor(p, fs);
                });
            }
            return;
        }

        // 8. STAGES
        if (isTitle(titleComp, "title.stages", null)) {
            e.setCancelled(true);
            if (e.getRawSlot() == 49) {
                ConfigurationSection sec = session.getConfig().getConfigurationSection("stages");
                int next = (sec == null) ? 1 : sec.getKeys(false).size() + 1;
                session.getConfig().createSection("stages." + next + ".actions");
                openStageList(p, session);
            } else if (e.getRawSlot() == 45) {
                openDungeonMenu(p, session);
            } else if (cur.getType() == Material.FILLED_MAP) {
                String stage = getPlainText(cur.getItemMeta().displayName()).replaceAll("[^0-9]", "");
                session.setCurrentStage(stage);
                openActionList(p, session);
            }
            return;
        }

        // 9. ACTION LIST
        if (isTitle(titleComp, "title.actions", "<stage>")) {
            e.setCancelled(true);
            if (e.getRawSlot() == 49) openActionTypeSelector(p);
            else if (e.getRawSlot() == 45) openStageList(p, session);
            else if (cur.getType() == Material.PAPER) {
                int slot = e.getRawSlot();
                if (e.isShiftClick()) {
                    List<Map<?, ?>> list = session.getConfig().getMapList("stages." + session.getCurrentStage() + ".actions");
                    if (slot < list.size()) {
                        list.remove(slot);
                        session.getConfig().set("stages." + session.getCurrentStage() + ".actions", list);
                        openActionList(p, session);
                    }
                } else {
                    session.setCurrentActionIndex(slot);
                    openActionEditor(p, session);
                }
            }
            return;
        }

        // 10. SELECT TYPE
        if (isTitle(titleComp, "title.select_type", null)) {
            e.setCancelled(true);
            if (e.getRawSlot() == 45) {
                openActionList(p, session);
                return;
            }
            String type = getPlainText(cur.getItemMeta().displayName());
            DungeonManager.ActionMeta meta = plugin.getDungeonManager().getActionMeta(type);
            if (meta == null) return;
            Map<String, Object> newAction = new HashMap<>();
            newAction.put("type", type);
            for (Map.Entry<String, Object> entry : meta.defaults.entrySet()) {
                Object val = entry.getValue();
                if (val instanceof List) val = new ArrayList<>((List<?>) val);
                else if (val instanceof Map) val = new HashMap<>((Map<?, ?>) val);
                newAction.put(entry.getKey(), val);
            }
            List<Map<?, ?>> list = session.getConfig().getMapList("stages." + session.getCurrentStage() + ".actions");
            list.add(newAction);
            session.getConfig().set("stages." + session.getCurrentStage() + ".actions", list);
            session.setCurrentActionIndex(list.size() - 1);
            openActionEditor(p, session);
            return;
        }

        // 11. ACTION EDITOR
        if (isTitle(titleComp, "title.edit_action", "<index>")) {
            e.setCancelled(true);
            if (e.getRawSlot() == 45) {
                openActionList(p, session);
                return;
            }
            if (cur.getType() == Material.BARRIER) return;

            String key = getPlainText(cur.getItemMeta().displayName());
            boolean isLocation = key.toLowerCase().contains("location") || key.equals("target") || key.equals("trigger") || key.equals("corner1") || key.equals("corner2") || key.equals("pos");

            if (isLocation) {
                if (e.isShiftClick() && e.isRightClick()) {
                    updateActionValue(session, key, null, false);
                    sendMessage(p, "loc_cleared");
                    openActionEditor(p, session);
                    return;
                }
                if (e.isRightClick()) {
                    String locStr = locToString(p.getLocation());
                    updateActionValue(session, key, locStr, true);
                    sendMessage(p, "update_loc", "<loc>", locStr);
                    openActionEditor(p, session);
                    return;
                }
            }

            plugin.getEditorListener().startListening(p);
            EditorSession fs = session;
            fs.awaitInput(EditorSession.InputType.EDIT_VALUE, val -> {
                updateActionValue(fs, key, val, false);
                sendMessage(p, "update_val", "<key>", key, "<val>", val);
                new EditorGUI(plugin).openActionEditor(p, fs);
            });
        }
    }

    // --- LOGIC HELPERS ---

    // [FIXED] Xử lý Map thủ công để tránh lỗi putAll
    private void updateCondition(EditorSession session, int index, String key, String value) {
        List<Object> list = (List<Object>) session.getConfig().getList("conditions");
        if (list == null) return;

        Object obj = list.get(index);
        Map<String, Object> map = new HashMap<>();

        if (obj instanceof String) {
            map.put("check", obj);
            map.put("msg", null);
        } else if (obj instanceof Map) {
            Map<?, ?> raw = (Map<?, ?>) obj;
            for (Map.Entry<?, ?> entry : raw.entrySet()) {
                map.put(entry.getKey().toString(), entry.getValue());
            }
        }

        map.put(key, value);
        list.set(index, map);
        session.getConfig().set("conditions", list);
    }

    private void updateRewardData(EditorSession session, String key, Object value) {
        List<Map<?, ?>> pool = session.getConfig().getMapList("rewards.pool");
        int idx = session.getCurrentRewardIndex();
        if (idx < 0 || idx >= pool.size()) return;

        Map<String, Object> map = new HashMap<>();
        for (Map.Entry<?, ?> e : pool.get(idx).entrySet()) map.put(e.getKey().toString(), e.getValue());

        if (key.equals("type") && value == null) {
            String current = (String) map.getOrDefault("type", "ITEM");
            String next = switch (current) {
                case "ITEM" -> "COMMAND";
                case "COMMAND" -> "MMOITEM";
                default -> "ITEM";
            };
            map.put("type", next);
            sendMessage(session.getPlayer(), "type_changed", "<type>", next);
        } else {
            map.put(key, value);
        }

        pool.set(idx, map);
        session.getConfig().set("rewards.pool", pool);
    }

    private void updateActionValue(EditorSession session, String key, Object value, boolean appendList) {
        String path = "stages." + session.getCurrentStage() + ".actions";
        List<Map<?, ?>> list = session.getConfig().getMapList(path);
        int idx = session.getCurrentActionIndex();

        if (idx < list.size()) {
            Map<String, Object> map = new HashMap<>();
            for (Map.Entry<?, ?> e : list.get(idx).entrySet()) map.put(e.getKey().toString(), e.getValue());

            if (value instanceof String && !appendList) {
                try {
                    value = Integer.parseInt((String) value);
                } catch (Exception e) {
                    try {
                        value = Double.parseDouble((String) value);
                    } catch (Exception ignored) {
                    }
                }
            }

            if (appendList || key.equals("locations") || key.equals("locs")) {
                Object rawList = map.get(key);
                List<String> locs;
                if (rawList instanceof List) locs = new ArrayList<>((List<String>) rawList);
                else locs = new ArrayList<>();

                if (value == null) locs.clear();
                else if (appendList) locs.add(value.toString());
                else {
                    locs.clear();
                    locs.add(value.toString());
                }
                map.put(key, locs);
            } else {
                map.put(key, value);
            }
            list.set(idx, map);
            session.getConfig().set(path, list);
        }
    }
}