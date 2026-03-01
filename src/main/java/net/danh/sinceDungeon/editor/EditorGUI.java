package net.danh.sinceDungeon.editor;

import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.manager.DungeonManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
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

    // =================================================================================
    //                                     UTILS
    // =================================================================================

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

    // =================================================================================
    //                                  GUI OPENERS
    // =================================================================================

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

        ConfigurationSection sec = session.getConfig().getConfigurationSection("conditions");
        if (sec != null) {
            int index = 0;
            for (String key : sec.getKeys(false)) {
                String nameStr = sec.getString(key + ".name", key);
                String check = sec.getString(key + ".check", "???");
                String msg = sec.getString(key + ".msg", "Mặc định");

                String displayName = getMsg("items.condition_item").replace("<index>", nameStr);
                List<String> lore = new ArrayList<>();
                for (String s : plugin.getMessagesFile().getStringList("editor.items.condition_lore"))
                    lore.add(s.replace("<check>", check).replace("<msg>", msg));

                inv.setItem(index++, makeItem(Material.NAME_TAG, displayName, lore));
            }
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

        ConfigurationSection pool = session.getConfig().getConfigurationSection("rewards.pool");
        if (pool != null) {
            int index = 0;
            for (String key : pool.getKeys(false)) {
                String type = pool.getString(key + ".type", "UNKNOWN");
                String val = pool.getString(key + ".value", "???");
                String name = getMsg("items.pool_item").replace("<index>", key);
                List<String> lore = new ArrayList<>();
                for (String s : plugin.getMessagesFile().getStringList("editor.items.pool_lore"))
                    lore.add(s.replace("<type>", type).replace("<val>", val));
                inv.setItem(index++, makeItem(Material.BUNDLE, name, lore));
            }
        }

        inv.setItem(49, makeItem(Material.EMERALD, getMsg("items.add_pool_item"), null));
        inv.setItem(45, makeItem(Material.ARROW, getMsg("items.back"), null));
        p.openInventory(inv);
    }

    public void openRewardEditor(Player p, EditorSession session) {
        session.setLastMenuOpener(player -> openRewardEditor(player, session));
        String key = session.getCurrentRewardKey();
        if (key == null) {
            openRewardPool(p, session);
            return;
        }

        String path = "rewards.pool." + key;
        String type = session.getConfig().getString(path + ".type", "ITEM");
        String value = session.getConfig().getString(path + ".value", "AIR:1");
        double chance = session.getConfig().getDouble(path + ".chance", 100.0);
        String displayName = session.getConfig().getString(path + ".name", "<gray>Mặc định");

        String title = getMsg("title.edit_reward").replace("<index>", key);
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

        ConfigurationSection sec = session.getConfig().getConfigurationSection("stages." + session.getCurrentStage() + ".actions");
        if (sec != null) {
            int index = 0;
            for (String key : sec.getKeys(false)) {
                String type = sec.getString(key + ".type", "UNKNOWN");
                String name = getMsg("items.action_item").replace("<index>", key);
                List<String> lore = new ArrayList<>();
                for (String s : plugin.getMessagesFile().getStringList("editor.items.action_lore"))
                    lore.add(s.replace("<type>", type));
                inv.setItem(index++, makeItem(Material.PAPER, name, lore));
            }
        }

        inv.setItem(49, makeItem(Material.EMERALD, getMsg("items.add_action"), null));
        inv.setItem(45, makeItem(Material.ARROW, getMsg("items.back"), null));
        p.openInventory(inv);
    }

    public void openActionEditor(Player p, EditorSession session) {
        session.setLastMenuOpener(player -> openActionEditor(player, session));
        String title = getMsg("title.edit_action").replace("<index>", session.getCurrentActionKey());
        Inventory inv = Bukkit.createInventory(null, 54, MiniMessage.miniMessage().deserialize(title));

        String path = "stages." + session.getCurrentStage() + ".actions." + session.getCurrentActionKey();
        ConfigurationSection sec = session.getConfig().getConfigurationSection(path);

        if (sec == null) {
            openActionList(p, session);
            return;
        }

        int slot = 0;
        for (String key : sec.getKeys(false)) {
            String val = String.valueOf(sec.get(key));
            Material icon = Material.BOOK;
            String hint = getMsg("items.action_val_hint_edit");
            boolean isLocation = key.toLowerCase().contains("location") || key.equals("target") || key.equals("trigger") || key.equals("corner1") || key.equals("corner2") || key.equals("pos");
            boolean isList = sec.isList(key);

            if (key.equals("type")) {
                icon = Material.BARRIER;
                hint = getMsg("items.action_type_cant_edit");
            } else if (key.equals("amount")) icon = Material.GOLD_NUGGET;
            else if (key.equals("mob")) icon = Material.CREEPER_HEAD;
            else if (isLocation) {
                icon = Material.COMPASS;
                hint = isList ? getMsg("items.action_val_hint_loc") + " | " + getMsg("items.action_val_hint_loc_clear") : getMsg("items.action_val_hint_loc_replace");
            }

            if (isList) {
                val = sec.getStringList(key).toString();
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
            inv.addItem(makeItem(meta.icon(), type, Collections.singletonList("<gray>" + meta.description())));
        }
        inv.setItem(45, makeItem(Material.ARROW, getMsg("items.cancel"), null));
        p.openInventory(inv);
    }

    // =================================================================================
    //                                  EVENT HANDLING
    // =================================================================================

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;

        ItemStack cur = e.getCurrentItem();
        if (cur == null || cur.getType() == Material.AIR) return;
        if (e.getClickedInventory() != e.getView().getTopInventory()) {
            e.setCancelled(true);
            return;
        }

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
                tempSession.setLastMenuOpener(this::openMainMenu);
                // Sửa dòng này: truyền tempSession vào
                plugin.getEditorListener().startListening(p, tempSession);
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
                plugin.getEditorListener().startListening(p, session);
                session.awaitInput(EditorSession.InputType.EDIT_VALUE, val -> {
                    session.getConfig().set("template-world", val);
                    sendMessage(p, "template_set", "<world>", val);
                    openDungeonMenu(p, session);
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

        // 3. CONDITIONS (UPDATED FOR KEYS)
        if (isTitle(titleComp, "title.conditions", null)) {
            e.setCancelled(true);
            int slot = e.getRawSlot();
            if (slot == 49) { // ADD
                sendMessage(p, "condition_val_hint_check");
                plugin.getEditorListener().startListening(p, session);
                session.awaitInput(EditorSession.InputType.EDIT_VALUE, val -> {
                    String newKey = "cond_" + System.currentTimeMillis();
                    session.getConfig().set("conditions." + newKey + ".check", val);
                    session.getConfig().set("conditions." + newKey + ".msg", "Condition Failed");
                    session.getConfig().set("conditions." + newKey + ".name", "Condition " + newKey);
                    openConditionList(p, session);
                });
            } else if (slot == 45) { // BACK
                openDungeonMenu(p, session);
            } else if (cur.getType() == Material.NAME_TAG) { // ITEM
                ConfigurationSection sec = session.getConfig().getConfigurationSection("conditions");
                if (sec != null) {
                    List<String> keys = new ArrayList<>(sec.getKeys(false));
                    if (slot < keys.size()) {
                        String key = keys.get(slot);
                        if (e.isShiftClick() && e.isRightClick()) { // DEL
                            session.getConfig().set("conditions." + key, null);
                            openConditionList(p, session);
                        } else if (e.isRightClick()) { // EDIT MSG
                            sendMessage(p, "condition_val_hint_msg");
                            plugin.getEditorListener().startListening(p, session);
                            session.awaitInput(EditorSession.InputType.EDIT_VALUE, val -> {
                                session.getConfig().set("conditions." + key + ".msg", val);
                                openConditionList(p, session);
                            });
                        } else { // EDIT CHECK
                            sendMessage(p, "condition_val_hint_check");
                            plugin.getEditorListener().startListening(p, session);
                            session.awaitInput(EditorSession.InputType.EDIT_VALUE, val -> {
                                session.getConfig().set("conditions." + key + ".check", val);
                                openConditionList(p, session);
                            });
                        }
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
                plugin.getEditorListener().startListening(p, session);
                session.awaitInput(EditorSession.InputType.EDIT_VALUE, val -> {
                    try {
                        String[] parts = val.split(" ");
                        if (parts.length < 2) throw new Exception();
                        int time = Integer.parseInt(parts[0]);
                        int amt = Integer.parseInt(parts[1]);
                        session.getConfig().set("rewards.tiers." + time, amt);
                        sendMessage(p, "tier_added", "<time>", String.valueOf(time), "<amount>", String.valueOf(amt));
                        openRewardTiers(p, session);
                    } catch (Exception ex) {
                        sendMessage(p, "format_tier_error");
                        new EditorGUI(plugin).openRewardTiers(p, session);
                    }
                });
            } else if (e.getRawSlot() == 45) { // BACK
                openRewardMenu(p, session);
            } else if (cur.getType() == Material.CLOCK) {
                String name = getPlainText(cur.getItemMeta().displayName());
                String timeStr = name.replaceAll("[^0-9]", "");
                if (e.isRightClick()) {
                    session.getConfig().set("rewards.tiers." + timeStr, null);
                    p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                    openRewardTiers(p, session);
                } else {
                    sendMessage(p, "action_val_hint_edit");
                    plugin.getEditorListener().startListening(p, session);
                    session.awaitInput(EditorSession.InputType.EDIT_VALUE, val -> {
                        try {
                            int newAmount = Integer.parseInt(val);
                            session.getConfig().set("rewards.tiers." + timeStr, newAmount);
                            sendMessage(p, "update_val", "<key>", "Số rương (" + timeStr + "s)", "<val>", val);
                            openRewardTiers(p, session);
                        } catch (Exception ex) {
                            sendMessage(p, "number_error");
                            new EditorGUI(plugin).openRewardTiers(p, session);
                        }
                    });
                }
            }
            return;
        }

        // 6. REWARD POOL (UPDATED FOR KEYS)
        if (isTitle(titleComp, "title.reward_pool", null)) {
            e.setCancelled(true);
            if (e.getRawSlot() == 49) { // ADD
                String newKey = "reward_" + System.currentTimeMillis();
                session.getConfig().set("rewards.pool." + newKey + ".type", "ITEM");
                session.getConfig().set("rewards.pool." + newKey + ".value", "DIAMOND:1");
                session.getConfig().set("rewards.pool." + newKey + ".chance", 100.0);
                sendMessage(p, "reward_added");
                openRewardPool(p, session);
            } else if (e.getRawSlot() == 45) { // BACK
                openRewardMenu(p, session);
            } else if (cur.getType() == Material.BUNDLE) { // CLICK
                ConfigurationSection pool = session.getConfig().getConfigurationSection("rewards.pool");
                if (pool != null) {
                    List<String> keys = new ArrayList<>(pool.getKeys(false));
                    int idx = e.getRawSlot();
                    if (idx < keys.size()) {
                        String key = keys.get(idx);
                        if (e.isShiftClick() && e.isRightClick()) { // DEL
                            session.getConfig().set("rewards.pool." + key, null);
                            openRewardPool(p, session);
                        } else { // EDIT
                            session.setCurrentRewardKey(key);
                            openRewardEditor(p, session);
                        }
                    }
                }
            }
            return;
        }

        // 7. REWARD EDITOR (UPDATED FOR KEYS)
        if (isTitle(titleComp, "title.edit_reward", "<index>")) {
            e.setCancelled(true);
            if (e.getRawSlot() == 18) {
                openRewardPool(p, session);
                return;
            }

            String currentKey = session.getCurrentRewardKey();
            String path = "rewards.pool." + currentKey;

            if (e.getRawSlot() == 10) { // TYPE
                String currentType = session.getConfig().getString(path + ".type", "ITEM");
                String nextType = switch (currentType) {
                    case "ITEM" -> "COMMAND";
                    case "COMMAND" -> "MMOITEM";
                    default -> "ITEM";
                };
                session.getConfig().set(path + ".type", nextType);
                sendMessage(p, "type_changed", "<type>", nextType);
                openRewardEditor(p, session);
            } else if (e.getRawSlot() == 12) { // VALUE
                if (e.isRightClick()) {
                    ItemStack hand = p.getInventory().getItemInMainHand();
                    if (hand.getType() != Material.AIR) {
                        String val = hand.getType().name() + ":" + hand.getAmount();
                        session.getConfig().set(path + ".value", val);
                        sendMessage(p, "item_set_hand", "<item>", val);
                        openRewardEditor(p, session);
                    } else sendMessage(p, "hand_empty");
                    return;
                }
                plugin.getEditorListener().startListening(p, session);
                session.awaitInput(EditorSession.InputType.EDIT_VALUE, val -> {
                    session.getConfig().set(path + ".value", val);
                    sendMessage(p, "update_val", "<key>", "Value", "<val>", val);
                    new EditorGUI(plugin).openRewardEditor(p, session);
                });
            } else if (e.getRawSlot() == 14) { // CHANCE
                plugin.getEditorListener().startListening(p, session);
                session.awaitInput(EditorSession.InputType.EDIT_VALUE, val -> {
                    try {
                        double chance = Double.parseDouble(val);
                        session.getConfig().set(path + ".chance", chance);
                        new EditorGUI(plugin).openRewardEditor(p, session);
                    } catch (Exception ex) {
                        sendMessage(p, "number_error");
                        new EditorGUI(plugin).openRewardEditor(p, session);
                    }
                });
            } else if (e.getRawSlot() == 16) { // NAME
                plugin.getEditorListener().startListening(p, session);
                session.awaitInput(EditorSession.InputType.EDIT_VALUE, val -> {
                    session.getConfig().set(path + ".name", val);
                    new EditorGUI(plugin).openRewardEditor(p, session);
                });
            }
            return;
        }

        // 8. STAGES
        if (isTitle(titleComp, "title.stages", null)) {
            e.setCancelled(true);
            if (e.getRawSlot() == 49) {
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

        // 9. ACTION LIST (UPDATED FOR KEYS)
        if (isTitle(titleComp, "title.actions", "<stage>")) {
            e.setCancelled(true);
            if (e.getRawSlot() == 49) openActionTypeSelector(p);
            else if (e.getRawSlot() == 45) openStageList(p, session);
            else if (cur.getType() == Material.PAPER) {
                ConfigurationSection sec = session.getConfig().getConfigurationSection("stages." + session.getCurrentStage() + ".actions");
                if (sec != null) {
                    List<String> keys = new ArrayList<>(sec.getKeys(false));
                    int slot = e.getRawSlot();
                    if (slot < keys.size()) {
                        String key = keys.get(slot);
                        if (e.isShiftClick()) {
                            session.getConfig().set("stages." + session.getCurrentStage() + ".actions." + key, null);
                            openActionList(p, session);
                        } else {
                            session.setCurrentActionKey(key);
                            openActionEditor(p, session);
                        }
                    }
                }
            }
            return;
        }

        // 10. SELECT TYPE (UPDATED FOR KEYS)
        if (isTitle(titleComp, "title.select_type", null)) {
            e.setCancelled(true);
            if (e.getRawSlot() == 45) {
                openActionList(p, session);
                return;
            }
            String type = getPlainText(cur.getItemMeta().displayName());
            DungeonManager.ActionMeta meta = plugin.getDungeonManager().getActionMeta(type);
            if (meta == null) return;

            String newKey = type.toLowerCase() + "_" + System.currentTimeMillis();
            String path = "stages." + session.getCurrentStage() + ".actions." + newKey;

            session.getConfig().set(path + ".type", type);
            for (Map.Entry<String, Object> entry : meta.defaults().entrySet()) {
                session.getConfig().set(path + "." + entry.getKey(), entry.getValue());
            }

            session.setCurrentActionKey(newKey);
            openActionEditor(p, session);
            return;
        }

        // 11. ACTION EDITOR (UPDATED FOR KEYS)
        if (isTitle(titleComp, "title.edit_action", "<index>")) {
            e.setCancelled(true);
            if (e.getRawSlot() == 45) {
                openActionList(p, session);
                return;
            }
            if (cur.getType() == Material.BARRIER) return;

            String key = getPlainText(cur.getItemMeta().displayName());
            boolean isLocation = key.toLowerCase().contains("location") || key.equals("target") || key.equals("trigger") || key.equals("corner1") || key.equals("corner2") || key.equals("pos");

            String fullPath = "stages." + session.getCurrentStage() + ".actions." + session.getCurrentActionKey() + "." + key;

            if (isLocation) {
                if (e.isShiftClick() && e.isRightClick()) {
                    // Clear list if it is a list, or set null/default if value
                    if (session.getConfig().isList(fullPath)) {
                        session.getConfig().set(fullPath, new ArrayList<>());
                    } else {
                        session.getConfig().set(fullPath, null);
                    }
                    sendMessage(p, "loc_cleared");
                    openActionEditor(p, session);
                    return;
                }
                if (e.isRightClick()) {
                    String locStr = locToString(p.getLocation());
                    if (session.getConfig().isList(fullPath)) {
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

            plugin.getEditorListener().startListening(p, session);
            session.awaitInput(EditorSession.InputType.EDIT_VALUE, val -> {
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
                // Try parse numbers
                try {
                    finalVal = Integer.parseInt(val);
                } catch (Exception e1) {
                    try {
                        finalVal = Double.parseDouble(val);
                    } catch (Exception ignored) {
                    }
                }

                // If existing value is a list, add to it. Otherwise replace.
                if (session.getConfig().isList(fullPath)) {
                    List<String> list = session.getConfig().getStringList(fullPath);
                    if (val.equalsIgnoreCase("clear")) list.clear();
                    else list.add(val);
                    session.getConfig().set(fullPath, list);
                } else {
                    session.getConfig().set(fullPath, finalVal);
                }

                sendMessage(p, "update_val", "<key>", key, "<val>", val);
                new EditorGUI(plugin).openActionEditor(p, session);
            });
        }
    }
}