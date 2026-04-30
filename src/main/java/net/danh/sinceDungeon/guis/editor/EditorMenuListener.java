package net.danh.sinceDungeon.guis.editor;

import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.hooks.MMOItemsHook;
import net.danh.sinceDungeon.managers.DungeonManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class EditorMenuListener implements Listener {
    private final SinceDungeon plugin;

    public EditorMenuListener(SinceDungeon plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof EditorHolder holder) {
            if (holder.menuType() != null && holder.menuType().equals("EDIT_ACTION_ITEMS")) {
                return;
            }

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
        EditorManager manager = plugin.getEditorManager();
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
                String name = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(cur.getItemMeta().displayName());
                manager.startEditing(p, name);
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
                else if (slot == 13) gui.openSettingsMenu(p, session);
                else if (slot == 14) gui.openRewardMenu(p, session);
                else if (slot == 16) gui.openStageList(p, session, session.getPage("STAGES"));
                else if (slot == 22) session.save();
                else if (slot == 18) gui.openMainMenu(p, 0);
            }

            case "SETTINGS" -> {
                if (slot == 18) {
                    gui.openDungeonMenu(p, session);
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
                    String current = session.getConfig().contains("settings.death-action") ? session.getConfig().getString("settings.death-action") : plugin.getConfigFile().getString("dungeon.death-action", "RESPAWN");
                    String next = current.equalsIgnoreCase("RESPAWN") ? "FAIL" : "RESPAWN";
                    session.getConfig().set("settings.death-action", next);
                    p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                    gui.openSettingsMenu(p, session);
                    return;
                } else if (slot == 17) {
                    path = "settings.clear-mob-drops";
                } else if (slot == 19) {
                    session.awaitInput(EditorSession.InputType.EDIT_NUMBER, "edit_req_lives", val -> {
                        try {
                            int newVal = Math.max(0, Integer.parseInt(val));
                            session.getConfig().set("settings.required-lives-to-join", newVal);
                            gui.sendMessage(p, "update_val", "<key>", "Required Lives", "<val>", String.valueOf(newVal));
                            gui.openSettingsMenu(p, session);
                        } catch (Exception ex) {
                            gui.sendMessage(p, "number_error");
                            gui.openSettingsMenu(p, session);
                        }
                    });
                    plugin.getEditorListener().startListening(p, session);
                } else if (slot == 20) {
                    session.awaitInput(EditorSession.InputType.EDIT_NUMBER, "edit_deduct_lives", val -> {
                        try {
                            int newVal = Math.max(0, Integer.parseInt(val));
                            session.getConfig().set("settings.lives-deducted-per-death", newVal);
                            gui.sendMessage(p, "update_val", "<key>", "Deduct Lives", "<val>", String.valueOf(newVal));
                            gui.openSettingsMenu(p, session);
                        } catch (Exception ex) {
                            gui.sendMessage(p, "number_error");
                            gui.openSettingsMenu(p, session);
                        }
                    });
                    plugin.getEditorListener().startListening(p, session);
                }

                if (!path.isEmpty()) {
                    if (isBool) {
                        boolean current;
                        if (path.equals("settings.save-and-restore-stats")) {
                            current = session.getConfig().contains(path) ? session.getConfig().getBoolean(path) : plugin.getConfigFile().getBoolean("dungeon.save-and-restore-stats", false);
                        } else if (path.equals("settings.clear-mob-drops")) {
                            current = session.getConfig().contains(path) ? session.getConfig().getBoolean(path) : plugin.getConfigFile().getBoolean("dungeon.clear-mob-drops", true);
                        } else {
                            current = session.getConfig().contains(path) ? session.getConfig().getBoolean(path) : plugin.getConfigFile().getBoolean("dungeon.gameplay." + path.replace("settings.", ""), true);
                        }

                        session.getConfig().set(path, !current);
                        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                        gui.openSettingsMenu(p, session);
                    } else {
                        final String finalPath = path;
                        session.awaitInput(EditorSession.InputType.EDIT_KICK_DELAY, "edit_kick_delay", val -> {
                            try {
                                int newDelay = Math.max(1, Integer.parseInt(val));
                                session.getConfig().set(finalPath, newDelay);
                                gui.sendMessage(p, "update_val", "<key>", "Kick Delay", "<val>", String.valueOf(newDelay));
                                gui.openSettingsMenu(p, session);
                            } catch (Exception ex) {
                                gui.sendMessage(p, "number_error");
                                gui.openSettingsMenu(p, session);
                            }
                        });
                        plugin.getEditorListener().startListening(p, session);
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
                        session.getConfig().set("conditions." + newKey + ".msg", gui.getWord("default_condition_msg"));
                        session.getConfig().set("conditions." + newKey + ".name", gui.getWord("default_condition_name").replace("<key>", newKey));
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
                                        gui.sendMessage(p, "update_val", "<key>", gui.getWord("chest_amount").replace("<time>", timeStr), "<val>", String.valueOf(newAmount));
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
                        default -> "ITEM";
                    };
                    session.getConfig().set(path + ".type", nextType);
                    gui.sendMessage(p, "type_changed", "<type>", nextType);
                    gui.openRewardEditor(p, session);
                } else if (slot == 12) {
                    if (e.getClick() == ClickType.RIGHT) {
                        ItemStack hand = p.getInventory().getItemInMainHand();
                        if (hand.getType() != Material.AIR) {
                            String val = hand.getType().name() + ":" + hand.getAmount();
                            session.getConfig().set(path + ".value", val);
                            gui.sendMessage(p, "item_set_hand", "<item>", val);
                            gui.openRewardEditor(p, session);
                        } else gui.sendMessage(p, "hand_empty");
                    } else if (e.getClick() == ClickType.LEFT) {
                        session.awaitInput(EditorSession.InputType.EDIT_STRING, "edit_reward_value_" + currentType.toLowerCase(), val -> {
                            session.getConfig().set(path + ".value", val);
                            gui.sendMessage(p, "update_val", "<key>", gui.getWord("value"), "<val>", val);
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
                                String msg = gui.getMsg("chat.stage_deleted");
                                if (msg != null && !msg.isEmpty()) {
                                    p.sendMessage(net.danh.sinceDungeon.utils.ColorUtils.parseWithPrefix(msg.replace("<stage>", stage)));
                                }
                            } else if (e.getClick() == ClickType.LEFT) {
                                session.setCurrentStage(stage);
                                gui.openActionList(p, session, session.getPage("ACTIONS"));
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

                String key = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(cur.getItemMeta().displayName());

                if (key.equalsIgnoreCase("type")) return;

                if (key.equalsIgnoreCase("items")) {
                    gui.openActionChestEditor(p, session);
                    return;
                }

                if (key.equalsIgnoreCase("notifications")) {
                    String tipMsg = "&7[Notifications] Edit this field manually in the dungeon YAML file.\n" + "&7Format: notifications:\n" + "&7  init: false\n" + "&7  complete: true\n" + "&7Available keys: custom_start, init, progress, complete, warning";
                    for (String line : tipMsg.split("\\n")) {
                        p.sendMessage(net.danh.sinceDungeon.utils.ColorUtils.parse(line));
                    }
                    return;
                }

                boolean isLocation = key.toLowerCase().contains("location") || key.equals("target") || key.equals("trigger") || key.equals("corner1") || key.equals("corner2") || key.equals("pos");
                boolean isRandomMobs = key.equalsIgnoreCase("random_mobs");

                String fullPath = "stages." + session.getCurrentStage() + ".actions." + session.getCurrentActionKey() + "." + key;
                boolean isList = isRandomMobs || session.getConfig().isList(fullPath);

                EditorSession.InputType inputType;
                if (isLocation) {
                    inputType = isList ? EditorSession.InputType.EDIT_LOCATION_LIST : EditorSession.InputType.EDIT_LOCATION;
                } else if (isList) {
                    inputType = EditorSession.InputType.EDIT_LIST;
                } else if (key.equals("amount") || key.equals("radius") || key.equals("chance") || key.equals("level")) {
                    inputType = EditorSession.InputType.EDIT_NUMBER;
                } else {
                    inputType = EditorSession.InputType.EDIT_STRING;
                }

                int maxLocs = plugin.getConfigFile().getInt("editor.limits.max-locations", 50);

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
                } else if (isList) {
                    if (e.getClick() == ClickType.RIGHT) {
                        List<String> list = session.getConfig().getStringList(fullPath);
                        if (!list.isEmpty()) {
                            list.remove(list.size() - 1);
                            session.getConfig().set(fullPath, list);
                            gui.sendMessage(p, "line_removed");
                        } else {
                            gui.sendMessage(p, "list_empty");
                        }
                        gui.openActionEditor(p, session);
                        return;
                    }
                }

                if (e.getClick() == ClickType.LEFT) {
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
                        String clearKw = plugin.getMessagesFile().getString("editor.words.clear", "clear");

                        if (isList) {
                            List<String> list = session.getConfig().getStringList(fullPath);
                            if (val.equalsIgnoreCase(clearKw)) {
                                list.clear();
                            } else {
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
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof EditorHolder holder && e.getPlayer() instanceof Player p) {
            EditorSession session = holder.session();
            if (session != null && "EDIT_ACTION_ITEMS".equals(holder.menuType())) {
                String path = "stages." + session.getCurrentStage() + ".actions." + session.getCurrentActionKey() + ".items";
                session.getConfig().set(path, null);

                org.bukkit.inventory.Inventory inv = e.getView().getTopInventory();
                for (int i = 0; i < inv.getSize(); i++) {
                    ItemStack item = inv.getItem(i);
                    if (item != null && item.getType() != Material.AIR) {
                        String itemStr = null;
                        if (Bukkit.getPluginManager().isPluginEnabled("MMOItems")) {
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