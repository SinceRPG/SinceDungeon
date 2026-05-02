package net.danh.sinceDungeon.guis.editor;

import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.api.events.DungeonEndEvent;
import net.danh.sinceDungeon.hooks.MMOItemsHook;
import net.danh.sinceDungeon.managers.DungeonManager;
import net.danh.sinceDungeon.models.DungeonGame;
import net.danh.sinceDungeon.utils.ColorUtils;
import net.danh.sinceDungeon.utils.ItemBuilder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Captures all inventory click events associated with the Editor GUI.
 * Validates admin permissions and routes data modifications securely.
 */
public class EditorMenuListener implements Listener {
    private final SinceDungeon plugin;

    public EditorMenuListener(SinceDungeon plugin) {
        this.plugin = plugin;
    }

    private void deleteDungeonSafely(Player p, String dungeonId, EditorGUI gui) {
        for (DungeonGame activeGame : plugin.getDungeonManager().getActiveGames().values()) {
            if (activeGame.getTemplate() != null && activeGame.getTemplate().id().equals(dungeonId)) {
                activeGame.stop(true, DungeonEndEvent.EndReason.FORCE_STOPPED);
            }
        }

        File file = new File(plugin.getDataFolder(), "dungeons/" + dungeonId + ".yml");
        if (file.exists()) file.delete();

        plugin.getDungeonManager().unregisterTemplate(dungeonId);

        if (plugin.getTopManager() != null) {
            plugin.getTopManager().resetLeaderboard(dungeonId);
        }

        p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_DESTROY, 1f, 1f);
        gui.sendMessage(p, "dungeon_deleted", "<dungeon>", dungeonId);
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof EditorHolder holder) {
            if (holder.menuType() != null && holder.menuType().equals("EDIT_ACTION_ITEMS")) return;

            if (!e.getWhoClicked().hasPermission("SinceDungeon.admin")) {
                e.setCancelled(true);
                e.getWhoClicked().closeInventory();
                return;
            }

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

        if (!p.hasPermission("SinceDungeon.admin")) {
            e.setCancelled(true);
            p.closeInventory();
            return;
        }

        if (holder.menuType() != null && holder.menuType().equals("EDIT_ACTION_ITEMS")) {
            e.setCancelled(true);
            if (e.getClickedInventory() == e.getView().getBottomInventory()) {
                if (e.getCurrentItem() != null && e.getCurrentItem().getType() != Material.AIR) {
                    p.setItemOnCursor(e.getCurrentItem().clone());
                }
            } else if (e.getClickedInventory() == e.getView().getTopInventory()) {
                if (e.getCursor() != null && e.getCursor().getType() != Material.AIR) {
                    e.getClickedInventory().setItem(e.getRawSlot(), e.getCursor().clone());
                } else {
                    e.getClickedInventory().setItem(e.getRawSlot(), new ItemStack(Material.AIR));
                }
            }
            return;
        }

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

        EditorGUI gui = new EditorGUI(plugin);
        net.danh.sinceDungeon.guis.editor.EditorManager manager = plugin.getEditorManager();
        final EditorSession session = holder.session();
        String menuType = holder.menuType();
        int page = holder.page();
        int slot = e.getRawSlot();

        if (menuType.equals("MAIN")) {
            if (slot == 48 && cur.getType() == gui.getNavItem()) {
                gui.openMainMenu(p, page - 1);
                return;
            }
            if (slot == 50 && cur.getType() == gui.getNavItem()) {
                gui.openMainMenu(p, page + 1);
                return;
            }

            if (slot < 45 && cur.getType() == Material.PAPER) {
                NamespacedKey key = new NamespacedKey(plugin, "dungeon_id");
                ItemMeta meta = cur.getItemMeta();
                if (meta != null && meta.getPersistentDataContainer().has(key, PersistentDataType.STRING)) {
                    String dungeonId = meta.getPersistentDataContainer().get(key, PersistentDataType.STRING);

                    if (e.getClick() == ClickType.SHIFT_RIGHT) {
                        deleteDungeonSafely(p, dungeonId, gui);
                        gui.openMainMenu(p, page);
                    } else if (e.getClick() == ClickType.LEFT) {
                        manager.startEditing(p, dungeonId);
                    }
                }
            } else if (slot == 49) {
                EditorSession tempSession = new EditorSession(plugin, p, null);
                tempSession.setLastMenuOpener(player -> gui.openMainMenu(player, page));
                tempSession.awaitInput(EditorSession.InputType.CREATE_FILENAME, "create_filename", val -> manager.startEditing(p, val));
                plugin.getEditorListener().startListening(p, tempSession);
            }
            return;
        }

        if (session == null) return;

        switch (menuType) {
            case "SELECT_TYPE" -> {
                if (slot == 48 && cur.getType() == gui.getNavItem()) {
                    gui.openActionTypeSelector(p, session, page - 1);
                    return;
                }
                if (slot == 50 && cur.getType() == gui.getNavItem()) {
                    gui.openActionTypeSelector(p, session, page + 1);
                    return;
                }
                if (slot == 45) {
                    gui.openActionList(p, session, session.getPage("ACTIONS"));
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
                            gui.openActionEditor(p, session);
                        }
                    }
                }
            }

            case "DUNGEON" -> {
                if (slot == 10) {
                    session.awaitInput(EditorSession.InputType.EDIT_STRING, "edit_world_name", val -> {
                        session.getConfig().set("template-world", val);
                        gui.sendMessage(p, "template_set", "<world>", val);
                        gui.openDungeonMenu(p, session);
                    });
                    plugin.getEditorListener().startListening(p, session);
                } else if (slot == 20) {
                    boolean current = session.getConfig().getBoolean("public", false);
                    session.getConfig().set("public", !current);
                    gui.sendMessage(p, "public_toggled", "<status>", String.valueOf(!current));
                    gui.openDungeonMenu(p, session);
                } else if (slot == 12) gui.openConditionList(p, session, session.getPage("CONDITIONS"));
                else if (slot == 13) gui.openSettingsMenu(p, session, session.getPage("SETTINGS"));
                else if (slot == 14) gui.openRewardMenu(p, session);
                else if (slot == 16) gui.openStageList(p, session, session.getPage("STAGES"));
                else if (slot == 22) session.save();
                else if (slot == 18) gui.openMainMenu(p, 0);
                else if (slot == 26 && cur.getType() == Material.BARRIER) {
                    if (e.getClick() == ClickType.SHIFT_RIGHT) {
                        String dungeonId = session.getFile().getName().replace(".yml", "");
                        deleteDungeonSafely(p, dungeonId, gui);
                        gui.openMainMenu(p, 0);
                    }
                }
            }

            case "SETTINGS" -> {
                if (slot == 18 && cur.getType() == gui.getNavItem()) {
                    gui.openDungeonMenu(p, session);
                    return;
                } else if (slot == 24 || slot == 25 || slot == 26) {
                    String cmdPath = (slot == 24) ? "settings.commands.on-start" : (slot == 25) ? "settings.commands.on-finish" : "settings.commands.on-first-finish";
                    gui.openStringListEditor(p, session, cmdPath, "SETTINGS", 0);
                    return;
                }
                if (slot == 21 && cur.getType() == gui.getNavItem()) {
                    gui.openSettingsMenu(p, session, page - 1);
                    return;
                }
                if (slot == 23 && cur.getType() == gui.getNavItem()) {
                    gui.openSettingsMenu(p, session, page + 1);
                    return;
                }

                NamespacedKey keyTag = new NamespacedKey(plugin, "setting_id");
                if (cur.getItemMeta() != null && cur.getItemMeta().getPersistentDataContainer().has(keyTag, PersistentDataType.STRING)) {
                    String settingIdStr = cur.getItemMeta().getPersistentDataContainer().get(keyTag, PersistentDataType.STRING);
                    if (settingIdStr == null) return;

                    EditorSession.SettingOption opt;
                    try {
                        opt = EditorSession.SettingOption.valueOf(settingIdStr);
                    } catch (Exception ex) {
                        return;
                    }

                    switch (opt.getDataType()) {
                        case "BOOL" -> {
                            boolean current = session.getConfig().contains(opt.getLocalPath()) ? session.getConfig().getBoolean(opt.getLocalPath()) : plugin.getConfigFile().getBoolean(opt.getGlobalFallbackPath(), (Boolean) opt.getDefaultValue());
                            session.getConfig().set(opt.getLocalPath(), !current);
                            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                            gui.openSettingsMenu(p, session, page);
                        }
                        case "DEATH_ENUM" -> {
                            String current = session.getConfig().contains(opt.getLocalPath()) ? session.getConfig().getString(opt.getLocalPath()) : plugin.getConfigFile().getString(opt.getGlobalFallbackPath(), (String) opt.getDefaultValue());
                            String next = (current != null && current.equalsIgnoreCase("RESPAWN")) ? "FAIL" : "RESPAWN";
                            session.getConfig().set(opt.getLocalPath(), next);
                            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                            gui.openSettingsMenu(p, session, page);
                        }
                        case "INT" -> {
                            String prompt = "edit_" + opt.name().toLowerCase();
                            if (opt == EditorSession.SettingOption.KICK_DELAY) prompt = "edit_kick_delay";
                            else if (opt == EditorSession.SettingOption.REQ_LIVES) prompt = "edit_req_lives";
                            else if (opt == EditorSession.SettingOption.DEDUCT_LIVES) prompt = "edit_deduct_lives";
                            else if (opt == EditorSession.SettingOption.MAX_PLAYERS) prompt = "edit_max_players";
                            else if (opt == EditorSession.SettingOption.COOLDOWN) prompt = "edit_cooldown";

                            session.awaitInput(EditorSession.InputType.EDIT_NUMBER, prompt, val -> {
                                try {
                                    int newVal = Integer.parseInt(val);
                                    if (opt != EditorSession.SettingOption.MAX_PLAYERS) {
                                        newVal = Math.max(0, newVal);
                                    }
                                    session.getConfig().set(opt.getLocalPath(), newVal);
                                    gui.sendMessage(p, "update_val", "<key>", gui.getMsg("items." + opt.getLangKey(), opt.getLangKey()), "<val>", String.valueOf(newVal));
                                    gui.openSettingsMenu(p, session, page);
                                } catch (Exception ex) {
                                    gui.sendMessage(p, "number_error");
                                    gui.openSettingsMenu(p, session, page);
                                }
                            });
                            plugin.getEditorListener().startListening(p, session);
                        }
                        case "LIST" -> {
                            gui.openStringListEditor(p, session, opt.getLocalPath(), "SETTINGS", 0);
                        }
                    }
                }
            }

            case "CONDITIONS" -> {
                if (slot == 48 && cur.getType() == gui.getNavItem()) {
                    gui.openConditionList(p, session, page - 1);
                    return;
                }
                if (slot == 50 && cur.getType() == gui.getNavItem()) {
                    gui.openConditionList(p, session, page + 1);
                    return;
                }

                if (slot == 49) {
                    session.awaitInput(EditorSession.InputType.EDIT_CONDITION_CHECK, "edit_condition_check", val -> {
                        String newKey = "cond_" + System.currentTimeMillis();
                        session.getConfig().set("conditions." + newKey + ".check", val);
                        session.getConfig().set("conditions." + newKey + ".msg", gui.getWord("default_condition_msg", "Requirements not met"));
                        session.getConfig().set("conditions." + newKey + ".name", gui.getWord("default_condition_name", "Condition <key>").replace("<key>", newKey));
                        gui.openConditionList(p, session, page);
                    });
                    plugin.getEditorListener().startListening(p, session);
                } else if (slot == 45) {
                    gui.openDungeonMenu(p, session);
                } else if (slot < 45 && cur.getType() == Material.NAME_TAG) {
                    ConfigurationSection sec = session.getConfig().getConfigurationSection("conditions");
                    if (sec != null) {
                        List<String> keys = new ArrayList<>(sec.getKeys(false));
                        int actualIdx = slot + page * 45;
                        if (actualIdx < keys.size()) {
                            String key = keys.get(actualIdx);
                            if (e.getClick() == ClickType.SHIFT_RIGHT) {
                                session.getConfig().set("conditions." + key, null);
                                gui.openConditionList(p, session, page);
                            } else if (e.getClick() == ClickType.RIGHT) {
                                session.awaitInput(EditorSession.InputType.EDIT_STRING, "edit_condition_msg", val -> {
                                    session.getConfig().set("conditions." + key + ".msg", val);
                                    gui.openConditionList(p, session, page);
                                });
                                plugin.getEditorListener().startListening(p, session);
                            } else if (e.getClick() == ClickType.LEFT) {
                                session.awaitInput(EditorSession.InputType.EDIT_CONDITION_CHECK, "edit_condition_check", val -> {
                                    session.getConfig().set("conditions." + key + ".check", val);
                                    gui.openConditionList(p, session, page);
                                });
                                plugin.getEditorListener().startListening(p, session);
                            }
                        }
                    }
                }
            }

            case "REWARDS_MAIN" -> {
                if (cur.getType() == Material.CLOCK) gui.openRewardTiers(p, session, session.getPage("REWARD_TIERS"));
                else if (cur.getType() == Material.CHEST)
                    gui.openRewardPool(p, session, session.getPage("REWARD_POOL"));
                else if (slot == 18) gui.openDungeonMenu(p, session);
            }

            case "REWARD_TIERS" -> {
                if (slot == 48 && cur.getType() == gui.getNavItem()) {
                    gui.openRewardTiers(p, session, page - 1);
                    return;
                }
                if (slot == 50 && cur.getType() == gui.getNavItem()) {
                    gui.openRewardTiers(p, session, page + 1);
                    return;
                }

                if (slot == 49) {
                    session.awaitInput(EditorSession.InputType.EDIT_TIER, "edit_tier", val -> {
                        try {
                            String[] parts = val.split(" ");
                            if (parts.length < 2) throw new Exception();
                            int time = Math.max(1, Integer.parseInt(parts[0]));
                            int amt = Math.max(1, Integer.parseInt(parts[1]));
                            session.getConfig().set("rewards.tiers." + time, amt);
                            gui.sendMessage(p, "tier_added", "<time>", String.valueOf(time), "<amount>", String.valueOf(amt));
                            gui.openRewardTiers(p, session, page);
                        } catch (Exception ex) {
                            gui.sendMessage(p, "format_tier_error");
                            gui.openRewardTiers(p, session, page);
                        }
                    });
                    plugin.getEditorListener().startListening(p, session);
                } else if (slot == 45) {
                    gui.openRewardMenu(p, session);
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
                                gui.openRewardTiers(p, session, page);
                            } else if (e.getClick() == ClickType.LEFT) {
                                session.awaitInput(EditorSession.InputType.EDIT_NUMBER, "edit_number", val -> {
                                    try {
                                        int newAmount = Math.max(0, Integer.parseInt(val));
                                        session.getConfig().set("rewards.tiers." + timeStr, newAmount);
                                        gui.sendMessage(p, "update_val", "<key>", gui.getWord("chest_amount", "Chests").replace("<time>", timeStr), "<val>", String.valueOf(newAmount));
                                        gui.openRewardTiers(p, session, page);
                                    } catch (Exception ex) {
                                        gui.sendMessage(p, "number_error");
                                        gui.openRewardTiers(p, session, page);
                                    }
                                });
                                plugin.getEditorListener().startListening(p, session);
                            }
                        }
                    }
                }
            }

            case "REWARD_POOL" -> {
                if (slot == 48 && cur.getType() == gui.getNavItem()) {
                    gui.openRewardPool(p, session, page - 1);
                    return;
                }
                if (slot == 50 && cur.getType() == gui.getNavItem()) {
                    gui.openRewardPool(p, session, page + 1);
                    return;
                }

                if (slot == 49) {
                    String newKey = "reward_" + System.currentTimeMillis();
                    session.getConfig().set("rewards.pool." + newKey + ".type", "ITEM");
                    session.getConfig().set("rewards.pool." + newKey + ".value", "DIAMOND:1");
                    session.getConfig().set("rewards.pool." + newKey + ".chance", 100.0);
                    gui.sendMessage(p, "reward_added");
                    gui.openRewardPool(p, session, page);
                } else if (slot == 45) {
                    gui.openRewardMenu(p, session);
                } else if (slot < 45 && cur.getType() == Material.BUNDLE) {
                    ConfigurationSection pool = session.getConfig().getConfigurationSection("rewards.pool");
                    if (pool != null) {
                        List<String> keys = new ArrayList<>(pool.getKeys(false));
                        int actualIdx = slot + page * 45;
                        if (actualIdx < keys.size()) {
                            String key = keys.get(actualIdx);
                            if (e.getClick() == ClickType.SHIFT_RIGHT) {
                                session.getConfig().set("rewards.pool." + key, null);
                                gui.openRewardPool(p, session, page);
                            } else if (e.getClick() == ClickType.LEFT) {
                                session.setCurrentRewardKey(key);
                                gui.openRewardEditor(p, session);
                            }
                        }
                    }
                }
            }

            case "EDIT_REWARD" -> {
                if (slot == 18) {
                    gui.openRewardPool(p, session, session.getPage("REWARD_POOL"));
                    return;
                }

                String currentKey = session.getCurrentRewardKey();
                String path = "rewards.pool." + currentKey;
                String currentType = session.getConfig().getString(path + ".type", "ITEM");

                if (slot == 10 && e.getClick() == ClickType.LEFT) {
                    String nextType = switch (currentType) {
                        case "ITEM" -> "COMMAND";
                        case "COMMAND" -> "MMOITEM";
                        case "MMOITEM" -> "LIFE_ITEM";
                        case "LIFE_ITEM" -> "COOLDOWN_RESET";
                        case "COOLDOWN_RESET" -> "COOLDOWN_REDUCE";
                        default -> "ITEM";
                    };
                    session.getConfig().set(path + ".type", nextType);
                    gui.sendMessage(p, "type_changed", "<type>", nextType);
                    gui.openRewardEditor(p, session);
                } else if (slot == 12) {
                    if (e.getClick() == ClickType.RIGHT) {
                        ItemStack hand = p.getInventory().getItemInMainHand();
                        if (hand.getType() != Material.AIR) {
                            String val;
                            NamespacedKey lifeKey = new NamespacedKey(plugin, "life_amount");
                            NamespacedKey resetKey = new NamespacedKey(plugin, "cooldown_reset");
                            NamespacedKey reduceKey = new NamespacedKey(plugin, "cooldown_reduce");
                            NamespacedKey keyTag = new NamespacedKey(plugin, "dungeon_key_id");

                            if (ItemBuilder.hasTag(hand, lifeKey, PersistentDataType.INTEGER)) {
                                val = String.valueOf(hand.getAmount());
                                session.getConfig().set(path + ".type", "LIFE_ITEM");
                            } else if (ItemBuilder.hasTag(hand, resetKey, PersistentDataType.BYTE)) {
                                val = String.valueOf(hand.getAmount());
                                session.getConfig().set(path + ".type", "COOLDOWN_RESET");
                            } else if (ItemBuilder.hasTag(hand, reduceKey, PersistentDataType.INTEGER)) {
                                int secs = ItemBuilder.getTag(hand, reduceKey, PersistentDataType.INTEGER);
                                val = secs + ":" + hand.getAmount();
                                session.getConfig().set(path + ".type", "COOLDOWN_REDUCE");
                            } else if (ItemBuilder.hasTag(hand, keyTag, PersistentDataType.STRING)) {
                                String keyId = ItemBuilder.getTag(hand, keyTag, PersistentDataType.STRING);
                                val = keyId + ":" + hand.getAmount();
                                session.getConfig().set(path + ".type", "ITEM");
                            } else if (Bukkit.getPluginManager().isPluginEnabled("MMOItems") && MMOItemsHook.getMMOItemString(hand) != null) {
                                String mmoStr = MMOItemsHook.getMMOItemString(hand);
                                String[] mmoParts = mmoStr.split(":");
                                val = mmoParts[1] + ":" + mmoParts[2] + ":" + mmoParts[3];
                                session.getConfig().set(path + ".type", "MMOITEM");
                            } else {
                                val = hand.getType().name() + ":" + hand.getAmount();
                                session.getConfig().set(path + ".type", "ITEM");
                            }

                            session.getConfig().set(path + ".value", val);
                            gui.sendMessage(p, "item_set_hand", "<item>", val);
                            gui.openRewardEditor(p, session);
                        } else gui.sendMessage(p, "hand_empty");
                    } else if (e.getClick() == ClickType.LEFT) {
                        session.awaitInput(EditorSession.InputType.EDIT_STRING, "edit_reward_value_" + currentType.toLowerCase(), val -> {
                            session.getConfig().set(path + ".value", val);
                            gui.sendMessage(p, "update_val", "<key>", gui.getWord("value", "Value"), "<val>", val);
                            gui.openRewardEditor(p, session);
                        });
                        plugin.getEditorListener().startListening(p, session);
                    }
                } else if (slot == 14 && e.getClick() == ClickType.LEFT) {
                    session.awaitInput(EditorSession.InputType.EDIT_NUMBER, "edit_reward_chance", val -> {
                        try {
                            double chance = Math.max(0.0, Math.min(100.0, Double.parseDouble(val)));
                            session.getConfig().set(path + ".chance", chance);
                            gui.openRewardEditor(p, session);
                        } catch (Exception ex) {
                            gui.sendMessage(p, "number_error");
                            gui.openRewardEditor(p, session);
                        }
                    });
                    plugin.getEditorListener().startListening(p, session);
                } else if (slot == 16 && e.getClick() == ClickType.LEFT) {
                    session.awaitInput(EditorSession.InputType.EDIT_STRING, "edit_reward_name", val -> {
                        session.getConfig().set(path + ".name", val);
                        gui.openRewardEditor(p, session);
                    });
                    plugin.getEditorListener().startListening(p, session);
                }
            }

            case "STAGES" -> {
                if (slot == 48 && cur.getType() == gui.getNavItem()) {
                    gui.openStageList(p, session, page - 1);
                    return;
                }
                if (slot == 50 && cur.getType() == gui.getNavItem()) {
                    gui.openStageList(p, session, page + 1);
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
                    gui.openStageList(p, session, page);
                } else if (slot == 45) {
                    gui.openDungeonMenu(p, session);
                } else if (slot < 45 && cur.getType() == Material.FILLED_MAP) {
                    ConfigurationSection sec = session.getConfig().getConfigurationSection("stages");
                    if (sec != null) {
                        List<String> keys = new ArrayList<>(sec.getKeys(false));
                        keys.sort(Comparator.comparingInt(s -> {
                            try {
                                return Integer.parseInt(s);
                            } catch (Exception err) {
                                return 0;
                            }
                        }));
                        int actualIdx = slot + page * 45;
                        if (actualIdx < keys.size()) {
                            String stage = keys.get(actualIdx);
                            if (e.getClick() == ClickType.SHIFT_RIGHT) {
                                session.getConfig().set("stages." + stage, null);
                                gui.openStageList(p, session, page);
                                String msg = plugin.getLanguageManager().getString("editor.chat.stage_deleted");
                                if (msg != null && !msg.isEmpty()) {
                                    p.sendMessage(ColorUtils.parseWithPrefix(msg.replace("<stage>", stage)));
                                }
                            } else if (e.getClick() == ClickType.LEFT) {
                                session.setCurrentStage(stage);
                                gui.openActionList(p, session, session.getPage("ACTIONS"));
                            } else if (e.getClick() == ClickType.RIGHT) {
                                session.awaitInput(EditorSession.InputType.EDIT_NUMBER, "edit_stage_chance", val -> {
                                    try {
                                        double c = Math.max(0.0, Math.min(100.0, Double.parseDouble(val)));
                                        session.getConfig().set("stages." + stage + ".chance", c);
                                        gui.openStageList(p, session, page);
                                    } catch (Exception ex) {
                                        gui.sendMessage(p, "number_error");
                                        gui.openStageList(p, session, page);
                                    }
                                });
                                plugin.getEditorListener().startListening(p, session);
                            } else if (e.getClick() == ClickType.SHIFT_LEFT) {
                                session.setCurrentStage(stage);
                                gui.openStringListEditor(p, session, "stages." + stage + ".commands", "STAGES", 0);
                            }
                        }
                    }
                }
            }

            case "ACTIONS" -> {
                if (slot == 48 && cur.getType() == gui.getNavItem()) {
                    gui.openActionList(p, session, page - 1);
                    return;
                }
                if (slot == 50 && cur.getType() == gui.getNavItem()) {
                    gui.openActionList(p, session, page + 1);
                    return;
                }

                if (slot == 49) {
                    gui.openActionTypeSelector(p, session, session.getPage("SELECT_TYPE"));
                } else if (slot == 45) {
                    gui.openStageList(p, session, session.getPage("STAGES"));
                } else if (slot < 45 && cur.getType() != Material.EMERALD && cur.getType() != gui.getNavItem()) {
                    ConfigurationSection sec = session.getConfig().getConfigurationSection("stages." + session.getCurrentStage() + ".actions");
                    if (sec != null) {
                        List<String> keys = new ArrayList<>(sec.getKeys(false));
                        int actualIdx = slot + page * 45;
                        if (actualIdx < keys.size()) {
                            String key = keys.get(actualIdx);
                            if (e.getClick() == ClickType.SHIFT_RIGHT) {
                                session.getConfig().set("stages." + session.getCurrentStage() + ".actions." + key, null);
                                gui.openActionList(p, session, page);
                            } else if (e.getClick() == ClickType.LEFT) {
                                session.setCurrentActionKey(key);
                                gui.openActionEditor(p, session);
                            }
                        }
                    }
                }
            }

            case "EDIT_ACTION" -> {
                if (slot == 45) {
                    gui.openActionList(p, session, session.getPage("ACTIONS"));
                    return;
                }
                if (cur.getType() == Material.BARRIER) return;

                String key = PlainTextComponentSerializer.plainText().serialize(cur.getItemMeta().displayName());

                if (key.equalsIgnoreCase("type")) return;

                if (key.equalsIgnoreCase("items")) {
                    gui.openActionChestEditor(p, session);
                    return;
                }

                if (key.equalsIgnoreCase("notifications")) {
                    for (String line : plugin.getLanguageManager().getStringList("editor.chat.notifications_hint")) {
                        p.sendMessage(ColorUtils.parse(line));
                    }
                    return;
                }

                if (key.equalsIgnoreCase("phases")) {
                    gui.openPhaseList(p, session, 0);
                    return;
                }

                boolean isLocation = key.toLowerCase().contains("location") || key.equals("target") || key.equals("trigger") || key.equals("corner1") || key.equals("corner2") || key.equals("pos") || key.equals("center");
                boolean isRandomMobs = key.equalsIgnoreCase("random_mobs");

                String fullPath = "stages." + session.getCurrentStage() + ".actions." + session.getCurrentActionKey() + "." + key;
                boolean isList = isRandomMobs || session.getConfig().isList(fullPath);

                EditorSession.InputType inputType;
                if (isLocation) {
                    inputType = isList ? EditorSession.InputType.EDIT_LOCATION_LIST : EditorSession.InputType.EDIT_LOCATION;
                } else if (isList) {
                    inputType = EditorSession.InputType.EDIT_LIST;
                } else if (key.equals("amount") || key.equals("radius") || key.equals("chance") || key.equals("level") || key.equals("enrage_time") || key.equals("base_health") || key.equals("scale_health_per_player")) {
                    inputType = EditorSession.InputType.EDIT_NUMBER;
                } else {
                    inputType = EditorSession.InputType.EDIT_STRING;
                }

                if (e.getClick() == ClickType.SHIFT_RIGHT) {
                    if (isList) {
                        session.getConfig().set(fullPath, new ArrayList<>());
                    } else {
                        session.getConfig().set(fullPath, null);
                    }
                    gui.sendMessage(p, "val_cleared");
                    gui.openActionEditor(p, session);
                    return;
                }

                if (isLocation) {
                    if (e.getClick() == ClickType.RIGHT) {
                        String locStr = String.format(java.util.Locale.US, "%.1f,%.1f,%.1f", p.getLocation().getX(), p.getLocation().getY(), p.getLocation().getZ());
                        if (isList) {
                            List<String> list = session.getConfig().getStringList(fullPath);
                            int maxLocs = plugin.getConfigFile().getInt("editor.limits.max-locations", 50);
                            if (list.size() >= maxLocs) {
                                gui.sendMessage(p, "list_limit_reached", "<limit>", String.valueOf(maxLocs));
                                return;
                            }
                            list.add(locStr);
                            session.getConfig().set(fullPath, list);
                        } else {
                            session.getConfig().set(fullPath, locStr);
                        }
                        gui.sendMessage(p, "update_loc", "<loc>", locStr);
                        gui.openActionEditor(p, session);
                        return;
                    }
                }

                if (e.getClick() == ClickType.LEFT) {
                    if (isList && !isLocation && !isRandomMobs) {
                        gui.openStringListEditor(p, session, fullPath, "EDIT_ACTION", 0);
                        return;
                    }

                    String promptKey = isRandomMobs ? "edit_random_mobs" : "edit_action_" + key.toLowerCase();

                    session.awaitInput(inputType, promptKey, val -> {
                        if (inputType == EditorSession.InputType.EDIT_NUMBER) {
                            try {
                                Double.parseDouble(val);
                            } catch (Exception ex) {
                                gui.sendMessage(p, "number_error");
                                gui.openActionEditor(p, session);
                                return;
                            }
                        }

                        Object finalVal = gui.getFinalVal(val, key);
                        String clearKw = plugin.getLanguageManager().getString("editor.words.clear", "clear");

                        if (isList) {
                            List<String> list = session.getConfig().getStringList(fullPath);
                            if (val.equalsIgnoreCase(clearKw)) {
                                list.clear();
                            } else {
                                int maxLocs = plugin.getConfigFile().getInt("editor.limits.max-locations", 50);
                                if (list.size() >= maxLocs) {
                                    gui.sendMessage(p, "list_limit_reached", "<limit>", String.valueOf(maxLocs));
                                    return;
                                }
                                list.add(val);
                            }
                            session.getConfig().set(fullPath, list);
                        } else {
                            session.getConfig().set(fullPath, finalVal);
                        }

                        gui.sendMessage(p, "update_val", "<key>", key, "<val>", val);
                        gui.openActionEditor(p, session);
                    });
                    plugin.getEditorListener().startListening(p, session);
                }
            }

            case "PHASE_LIST" -> {
                if (slot == 48 && cur.getType() == gui.getNavItem()) {
                    gui.openPhaseList(p, session, page - 1);
                    return;
                }
                if (slot == 50 && cur.getType() == gui.getNavItem()) {
                    gui.openPhaseList(p, session, page + 1);
                    return;
                }
                if (slot == 45) {
                    gui.openActionEditor(p, session);
                    return;
                }
                if (slot == 49) {
                    session.awaitInput(EditorSession.InputType.EDIT_NUMBER, "edit_phase_threshold", val -> {
                        try {
                            int threshold = Math.max(1, Math.min(100, Integer.parseInt(val)));
                            String path = "stages." + session.getCurrentStage() + ".actions." + session.getCurrentActionKey() + ".phases." + threshold;
                            if (!session.getConfig().contains(path)) {
                                session.getConfig().set(path + ".message", "");
                                session.getConfig().set(path + ".attributes", new ArrayList<>());
                                session.getConfig().set(path + ".skills", new ArrayList<>());
                            }
                            gui.openPhaseList(p, session, page);
                        } catch (Exception ex) {
                            gui.sendMessage(p, "number_error");
                            gui.openPhaseList(p, session, page);
                        }
                    });
                    plugin.getEditorListener().startListening(p, session);
                } else if (slot < 45 && cur.getType() == Material.COMMAND_BLOCK) {
                    String threshold = PlainTextComponentSerializer.plainText().serialize(cur.getItemMeta().displayName()).replaceAll("[^0-9]", "");
                    if (e.getClick() == ClickType.SHIFT_RIGHT) {
                        session.getConfig().set("stages." + session.getCurrentStage() + ".actions." + session.getCurrentActionKey() + ".phases." + threshold, null);
                        gui.openPhaseList(p, session, page);
                    } else if (e.getClick() == ClickType.LEFT) {
                        session.setCurrentPhaseThreshold(threshold);
                        gui.openPhaseEditor(p, session);
                    }
                }
            }

            case "EDIT_PHASE" -> {
                if (slot == 18) {
                    gui.openPhaseList(p, session, session.getPage("PHASE_LIST"));
                    return;
                }
                String basePath = "stages." + session.getCurrentStage() + ".actions." + session.getCurrentActionKey() + ".phases." + session.getCurrentPhaseThreshold();

                if (slot == 11 && e.getClick() == ClickType.LEFT) {
                    session.awaitInput(EditorSession.InputType.EDIT_STRING, "edit_phase_message", val -> {
                        String clearKw = plugin.getLanguageManager().getString("editor.words.clear", "clear");
                        if (val.equalsIgnoreCase(clearKw)) val = "";
                        session.getConfig().set(basePath + ".message", val);
                        gui.openPhaseEditor(p, session);
                    });
                    plugin.getEditorListener().startListening(p, session);
                } else if (slot == 13 && e.getClick() == ClickType.LEFT) {
                    gui.openStringListEditor(p, session, basePath + ".attributes", "EDIT_PHASE", 0);
                } else if (slot == 15 && e.getClick() == ClickType.LEFT) {
                    gui.openReinforcementEditor(p, session);
                }
            }

            case "EDIT_REINFORCEMENTS" -> {
                if (slot == 18) {
                    gui.openPhaseEditor(p, session);
                    return;
                }
                String basePath = "stages." + session.getCurrentStage() + ".actions." + session.getCurrentActionKey() + ".phases." + session.getCurrentPhaseThreshold() + ".reinforcements";

                if (slot == 10 && e.getClick() == ClickType.LEFT) {
                    session.awaitInput(EditorSession.InputType.EDIT_STRING, "edit_reinforcement_mob", val -> {
                        session.getConfig().set(basePath + ".mob", val.toUpperCase());
                        gui.openReinforcementEditor(p, session);
                    });
                    plugin.getEditorListener().startListening(p, session);
                } else if (slot == 12 && e.getClick() == ClickType.LEFT) {
                    session.awaitInput(EditorSession.InputType.EDIT_NUMBER, "edit_reinforcement_amount", val -> {
                        try {
                            int amt = Math.max(1, Integer.parseInt(val));
                            session.getConfig().set(basePath + ".amount", amt);
                            gui.openReinforcementEditor(p, session);
                        } catch (Exception ex) {
                            gui.sendMessage(p, "number_error");
                            gui.openReinforcementEditor(p, session);
                        }
                    });
                    plugin.getEditorListener().startListening(p, session);
                } else if (slot == 13 && e.getClick() == ClickType.LEFT) {
                    session.awaitInput(EditorSession.InputType.EDIT_STRING, "edit_reinforcement_name", val -> {
                        String clearKw = plugin.getLanguageManager().getString("editor.words.clear", "clear");
                        if (val.equalsIgnoreCase(clearKw)) val = "";
                        session.getConfig().set(basePath + ".custom_name", val);
                        gui.openReinforcementEditor(p, session);
                    });
                    plugin.getEditorListener().startListening(p, session);
                } else if (slot == 14 && e.getClick() == ClickType.LEFT) {
                    gui.openStringListEditor(p, session, basePath + ".attributes", "EDIT_REINFORCEMENTS", 0);
                } else if (slot == 16 && e.getClick() == ClickType.LEFT) {
                    gui.openStringListEditor(p, session, basePath + ".equipment", "EDIT_REINFORCEMENTS", 0);
                }
            }

            case "EDIT_STRING_LIST" -> {
                if (slot == 48 && cur.getType() == gui.getNavItem()) {
                    gui.openStringListEditor(p, session, session.getCurrentListPath(), session.getCurrentListReturnMenu(), page - 1);
                    return;
                }
                if (slot == 50 && cur.getType() == gui.getNavItem()) {
                    gui.openStringListEditor(p, session, session.getCurrentListPath(), session.getCurrentListReturnMenu(), page + 1);
                    return;
                }
                if (slot == 45) {
                    if ("SETTINGS".equals(session.getCurrentListReturnMenu())) {
                        gui.openSettingsMenu(p, session, session.getPage("SETTINGS"));
                    } else if ("EDIT_ACTION".equals(session.getCurrentListReturnMenu())) {
                        gui.openActionEditor(p, session);
                    } else if ("EDIT_PHASE".equals(session.getCurrentListReturnMenu())) {
                        gui.openPhaseEditor(p, session);
                    } else if ("EDIT_REINFORCEMENTS".equals(session.getCurrentListReturnMenu())) {
                        gui.openReinforcementEditor(p, session);
                    } else {
                        gui.openDungeonMenu(p, session);
                    }
                    return;
                }
                if (slot == 49) {
                    String prompt = session.getCurrentListPath().contains("commands") ? "edit_commands" : "default";
                    if (session.getCurrentListPath().contains("custom_drops")) prompt = "edit_custom_drops";

                    session.awaitInput(EditorSession.InputType.EDIT_STRING, prompt, val -> {
                        List<String> list = session.getConfig().getStringList(session.getCurrentListPath());
                        list.add(val);
                        session.getConfig().set(session.getCurrentListPath(), list);
                        gui.openStringListEditor(p, session, session.getCurrentListPath(), session.getCurrentListReturnMenu(), page);
                    });
                    plugin.getEditorListener().startListening(p, session);
                    return;
                }
                if (slot < 45 && cur.getType() == Material.PAPER) {
                    if (e.getClick() == ClickType.SHIFT_RIGHT) {
                        int idx = slot + page * 45;
                        List<String> list = session.getConfig().getStringList(session.getCurrentListPath());
                        if (idx < list.size()) {
                            list.remove(idx);
                            session.getConfig().set(session.getCurrentListPath(), list);
                            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                            gui.sendMessage(p, "line_removed");
                            gui.openStringListEditor(p, session, session.getCurrentListPath(), session.getCurrentListReturnMenu(), page);
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof EditorHolder holder && e.getPlayer() instanceof Player p) {
            EditorSession session = holder.session();
            if (session != null && "EDIT_ACTION_ITEMS".equals(holder.menuType())) {
                String path = "stages." + session.getCurrentStage() + ".actions." + session.getCurrentActionKey() + ".items";
                session.getConfig().set(path, null);

                Inventory inv = e.getView().getTopInventory();

                NamespacedKey keyTag = new NamespacedKey(plugin, "dungeon_key_id");
                NamespacedKey lifeKey = new NamespacedKey(plugin, "life_amount");
                NamespacedKey resetKey = new NamespacedKey(plugin, "cooldown_reset");
                NamespacedKey reduceKey = new NamespacedKey(plugin, "cooldown_reduce");

                for (int i = 0; i < inv.getSize(); i++) {
                    ItemStack item = inv.getItem(i);
                    if (item != null && item.getType() != Material.AIR) {
                        String itemStr = null;

                        if (ItemBuilder.hasTag(item, keyTag, PersistentDataType.STRING)) {
                            String keyId = ItemBuilder.getTag(item, keyTag, PersistentDataType.STRING);
                            itemStr = "KEY:" + keyId + ":" + item.getAmount();
                        } else if (ItemBuilder.hasTag(item, lifeKey, PersistentDataType.INTEGER)) {
                            itemStr = "LIFE_ITEM:" + item.getAmount();
                        } else if (ItemBuilder.hasTag(item, resetKey, PersistentDataType.BYTE)) {
                            itemStr = "COOLDOWN_RESET:" + item.getAmount();
                        } else if (ItemBuilder.hasTag(item, reduceKey, PersistentDataType.INTEGER)) {
                            int secs = ItemBuilder.getTag(item, reduceKey, PersistentDataType.INTEGER);
                            itemStr = "COOLDOWN_REDUCE:" + secs + ":" + item.getAmount();
                        } else if (Bukkit.getPluginManager().isPluginEnabled("MMOItems")) {
                            itemStr = MMOItemsHook.getMMOItemString(item);
                        }

                        if (itemStr == null) {
                            itemStr = item.getType().name() + ":" + item.getAmount();
                        }

                        session.getConfig().set(path + "." + i, itemStr);
                    }
                }

                Bukkit.getScheduler().runTaskLater(plugin, () -> new EditorGUI(plugin).openActionEditor(p, session), 1L);
            }
        }
    }
}