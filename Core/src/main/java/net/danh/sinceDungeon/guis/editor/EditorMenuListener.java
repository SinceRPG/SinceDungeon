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
import java.util.*;

/**
 * Editor GUI Interaction Handler
 * <p>
 * Responsibilities:
 * - Listens for all inventory interactions securely, preventing item theft.
 * - Parses and maps inputs back to the YAML configurations dynamically.
 * - Centralizes String List clearing with the new TNT button.
 * - Employs Java 21 Record Patterns and strict Null-Safety checks.
 */
@SuppressWarnings("SpellCheckingInspection")
public class EditorMenuListener implements Listener {
    private final SinceDungeon plugin;

    public EditorMenuListener(SinceDungeon plugin) {
        this.plugin = plugin;
    }

    /**
     * Safely deletes a dungeon file and terminates any active games using it.
     */
    private void deleteDungeonSafely(Player p, String dungeonId, EditorGUI gui) {
        if (dungeonId == null) return;

        for (DungeonGame activeGame : plugin.getDungeonManager().getActiveGames().values()) {
            if (activeGame.getTemplate() != null && activeGame.getTemplate().id().equals(dungeonId)) {
                activeGame.stop(true, DungeonEndEvent.EndReason.FORCE_STOPPED);
            }
        }

        File file = new File(plugin.getDataFolder(), "dungeons/" + dungeonId + ".yml");
        if (file.exists()) {
            if (!file.delete()) {
                plugin.getLogger().warning(plugin.getLanguageManager().getString("admin.log.file_delete_fail", "Failed to delete file: <file>").replace("<file>", file.getName()));
            }
        }

        plugin.getDungeonManager().unregisterTemplate(dungeonId);

        if (plugin.getTopManager() != null) {
            plugin.getTopManager().resetLeaderboard(dungeonId);
        }

        p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_DESTROY, 1f, 1f);
        gui.sendMessage(p, "dungeon_deleted", "<dungeon>", dungeonId);
    }

    /**
     * Determines the correct prompt translation key based on the list configuration path.
     */
    private String determinePrompt(String listPath) {
        if (listPath == null) return "default";
        if (listPath.endsWith("custom_drops") || listPath.contains("custom_drops")) return "edit_custom_drops";
        if (listPath.endsWith("commands") || listPath.contains("commands")) return "edit_commands";
        if (listPath.contains(".actions.")) {
            String[] pathParts = listPath.split("\\.");
            if (pathParts.length > 0) {
                return "edit_action_" + pathParts[pathParts.length - 1];
            }
        }
        return "default";
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof EditorHolder(
                EditorSession session, String menuType, int page
        )) {
            if (menuType != null && menuType.equals("EDIT_ACTION_ITEMS")) return;

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

        // Java 21 Record Pattern Deconstruction
        if (!(e.getView().getTopInventory().getHolder() instanceof EditorHolder(
                EditorSession session, String menuType, int page
        ))) return;

        if (!p.hasPermission("SinceDungeon.admin")) {
            e.setCancelled(true);
            p.closeInventory();
            return;
        }

        if (menuType != null && menuType.equals("EDIT_ACTION_ITEMS")) {
            e.setCancelled(true);
            if (e.getClickedInventory() == e.getView().getBottomInventory()) {
                if (e.getCurrentItem() != null && !e.getCurrentItem().getType().isAir()) {
                    p.setItemOnCursor(e.getCurrentItem().clone());
                }
            } else if (e.getClickedInventory() == e.getView().getTopInventory()) {
                if (!e.getCursor().getType().isAir()) {
                    if (e.getClickedInventory() != null) {
                        e.getClickedInventory().setItem(e.getRawSlot(), e.getCursor().clone());
                    }
                } else {
                    if (e.getClickedInventory() != null) {
                        e.getClickedInventory().setItem(e.getRawSlot(), new ItemStack(Material.AIR));
                    }
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
        int slot = e.getRawSlot();

        if (menuType == null) return;

        if (menuType.equals("MAIN")) {
            handleMainMenu(e, p, gui, page, slot, cur);
            return;
        }

        if (session == null) return;

        switch (menuType) {
            case "SELECT_TYPE" -> handleSelectType(p, session, gui, page, slot, cur);
            case "DUNGEON" -> handleDungeonMenu(e, p, session, gui, slot, cur);
            case "SETTINGS" -> handleSettingsMenu(p, session, gui, page, slot, cur);
            case "CONDITIONS" -> handleConditionsMenu(e, p, session, gui, page, slot, cur);
            case "REWARDS_MAIN" -> handleRewardsMain(p, session, gui, slot);
            case "REWARD_TIERS" -> handleRewardTiers(e, p, session, gui, page, slot, cur);
            case "REWARD_POOL" -> handleRewardPool(e, p, session, gui, page, slot, cur);
            case "EDIT_REWARD" -> handleEditReward(e, p, session, gui, slot);
            case "STAGES" -> handleStagesMenu(e, p, session, gui, page, slot, cur);
            case "ACTIONS" -> handleActionsMenu(e, p, session, gui, page, slot, cur);
            case "EDIT_ACTION" -> handleEditAction(e, p, session, gui, slot, cur);
            case "EDIT_NOTIFICATIONS" -> handleNotifications(p, session, gui, slot, cur);
            case "PHASE_LIST" -> handlePhaseList(e, p, session, gui, page, slot, cur);
            case "EDIT_PHASE" -> handleEditPhase(e, p, session, gui, slot);
            case "EDIT_REINFORCEMENTS" -> handleEditReinforcements(e, p, session, gui, slot);
            case "EDIT_STRING_LIST" -> handleStringList(e, p, session, gui, page, slot, cur);
        }
    }

    private void handleMainMenu(InventoryClickEvent e, Player p, EditorGUI gui, int page, int slot, ItemStack cur) {
        EditorManager manager = plugin.getEditorManager();
        if (slot == 48 && cur.getType() == gui.getNavMaterial()) {
            gui.openMainMenu(p, page - 1);
            return;
        }
        if (slot == 50 && cur.getType() == gui.getNavMaterial()) {
            gui.openMainMenu(p, page + 1);
            return;
        }

        if (slot < 45 && cur.getType() == Material.PAPER) {
            NamespacedKey key = new NamespacedKey(plugin, "dungeon_id");
            ItemMeta meta = cur.getItemMeta();
            if (meta != null && meta.getPersistentDataContainer().has(key, PersistentDataType.STRING)) {
                String dungeonId = meta.getPersistentDataContainer().get(key, PersistentDataType.STRING);
                if (dungeonId == null) return;

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
    }

    private void handleSelectType(Player p, EditorSession session, EditorGUI gui, int page, int slot, ItemStack cur) {
        if (slot == 48 && cur.getType() == gui.getNavMaterial()) {
            gui.openActionTypeSelector(p, session, page - 1);
            return;
        }
        if (slot == 50 && cur.getType() == gui.getNavMaterial()) {
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
                if (type == null) return;

                DungeonManager.ActionMeta actMeta = plugin.getDungeonManager().getActionMeta(type);
                if (actMeta != null) {
                    String newKey = type.toLowerCase(Locale.ROOT) + "_" + System.currentTimeMillis();
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

    private void handleDungeonMenu(InventoryClickEvent e, Player p, EditorSession session, EditorGUI gui, int slot, ItemStack cur) {
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
        } else if (slot == 12) {
            gui.openConditionList(p, session, session.getPage("CONDITIONS"));
        } else if (slot == 13) {
            gui.openSettingsMenu(p, session, session.getPage("SETTINGS"));
        } else if (slot == 14) {
            gui.openRewardMenu(p, session);
        } else if (slot == 16) {
            gui.openStageList(p, session, session.getPage("STAGES"));
        } else if (slot == 22) {
            session.save();
        } else if (slot == 18) {
            gui.openMainMenu(p, 0);
        } else if (slot == 26 && cur.getType() == Material.BARRIER) {
            if (e.getClick() == ClickType.SHIFT_RIGHT) {
                String dungeonId = session.getFile().getName().replace(".yml", "");
                deleteDungeonSafely(p, dungeonId, gui);
                gui.openMainMenu(p, 0);
            }
        }
    }

    private void handleSettingsMenu(Player p, EditorSession session, EditorGUI gui, int page, int slot, ItemStack cur) {
        if (slot == 18 && cur.getType() == gui.getNavMaterial()) {
            gui.openDungeonMenu(p, session);
            return;
        }
        if (slot == 21 && cur.getType() == gui.getNavMaterial()) {
            gui.openSettingsMenu(p, session, page - 1);
            return;
        }
        if (slot == 23 && cur.getType() == gui.getNavMaterial()) {
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
                    boolean current = session.getConfig().contains(opt.getLocalPath())
                            ? session.getConfig().getBoolean(opt.getLocalPath())
                            : (opt.getGlobalFallbackPath() != null ? plugin.getConfigFile().getBoolean(opt.getGlobalFallbackPath(), (Boolean) opt.getDefaultValue()) : (Boolean) opt.getDefaultValue());
                    session.getConfig().set(opt.getLocalPath(), !current);
                    p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                    gui.openSettingsMenu(p, session, page);
                }
                case "DEATH_ENUM" -> {
                    String current = session.getConfig().contains(opt.getLocalPath())
                            ? session.getConfig().getString(opt.getLocalPath())
                            : (opt.getGlobalFallbackPath() != null ? plugin.getConfigFile().getString(opt.getGlobalFallbackPath(), (String) opt.getDefaultValue()) : (String) opt.getDefaultValue());
                    String next = (current != null && current.equalsIgnoreCase("RESPAWN")) ? "FAIL" : "RESPAWN";
                    session.getConfig().set(opt.getLocalPath(), next);
                    p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                    gui.openSettingsMenu(p, session, page);
                }
                case "STRING" -> {
                    String prompt = "edit_" + opt.name().toLowerCase(Locale.ROOT);
                    session.awaitInput(EditorSession.InputType.EDIT_STRING, prompt, val -> {
                        session.getConfig().set(opt.getLocalPath(), val);
                        gui.sendMessage(p, "update_val", "<key>", gui.getMsg("items." + opt.getLangKey(), opt.getLangKey()), "<val>", val);
                        gui.openSettingsMenu(p, session, page);
                    });
                    plugin.getEditorListener().startListening(p, session);
                }
                case "INT" -> {
                    String prompt = "edit_" + opt.name().toLowerCase(Locale.ROOT);
                    session.awaitInput(EditorSession.InputType.EDIT_NUMBER, prompt, val -> {
                        try {
                            int newVal = Integer.parseInt(val);
                            if (opt != EditorSession.SettingOption.MAX_PLAYERS) newVal = Math.max(0, newVal);
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
                case "LIST" -> gui.openStringListEditor(p, session, opt.getLocalPath(), "SETTINGS", 0);
            }
        }
    }

    private void handleConditionsMenu(InventoryClickEvent e, Player p, EditorSession session, EditorGUI gui, int page, int slot, ItemStack cur) {
        if (slot == 48 && cur.getType() == gui.getNavMaterial()) {
            gui.openConditionList(p, session, page - 1);
            return;
        }
        if (slot == 50 && cur.getType() == gui.getNavMaterial()) {
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
                    final String key = keys.get(actualIdx);
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

    private void handleRewardsMain(Player p, EditorSession session, EditorGUI gui, int slot) {
        if (slot == 11) {
            session.setCurrentTierType("SOLO");
            gui.openRewardTiers(p, session, session.getPage("REWARD_TIERS"));
        } else if (slot == 12) {
            session.setCurrentTierType("PARTY");
            gui.openRewardTiers(p, session, session.getPage("REWARD_TIERS"));
        } else if (slot == 15) {
            gui.openRewardPool(p, session, session.getPage("REWARD_POOL"));
        } else if (slot == 18) {
            gui.openDungeonMenu(p, session);
        }
    }

    private void handleRewardTiers(InventoryClickEvent e, Player p, EditorSession session, EditorGUI gui, int page, int slot, ItemStack cur) {
        if (slot == 48 && cur.getType() == gui.getNavMaterial()) {
            gui.openRewardTiers(p, session, page - 1);
            return;
        }
        if (slot == 50 && cur.getType() == gui.getNavMaterial()) {
            gui.openRewardTiers(p, session, page + 1);
            return;
        }

        final String pathPrefix = session.getCurrentTierType().equalsIgnoreCase("PARTY") ? "rewards.party-tiers" : "rewards.solo-tiers";

        if (slot == 49) {
            session.awaitInput(EditorSession.InputType.EDIT_TIER, "edit_tier", val -> {
                try {
                    String[] parts = val.split(" ");
                    if (parts.length < 2) throw new IllegalArgumentException();
                    int time = Math.max(1, Integer.parseInt(parts[0]));
                    int amt = Math.max(1, Integer.parseInt(parts[1]));
                    session.getConfig().set(pathPrefix + "." + time, amt);
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
            ConfigurationSection tiers = session.getConfig().getConfigurationSection(pathPrefix);
            if (tiers != null) {
                List<String> keys = new ArrayList<>(tiers.getKeys(false));
                keys.sort(Comparator.comparingInt(Integer::parseInt));
                int actualIdx = slot + page * 45;

                if (actualIdx < keys.size()) {
                    final String timeStr = keys.get(actualIdx);
                    if (e.getClick() == ClickType.RIGHT) {
                        session.getConfig().set(pathPrefix + "." + timeStr, null);
                        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                        gui.openRewardTiers(p, session, page);
                    } else if (e.getClick() == ClickType.LEFT) {
                        session.awaitInput(EditorSession.InputType.EDIT_NUMBER, "edit_number", val -> {
                            try {
                                int newAmount = Math.max(0, Integer.parseInt(val));
                                session.getConfig().set(pathPrefix + "." + timeStr, newAmount);
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

    private void handleRewardPool(InventoryClickEvent e, Player p, EditorSession session, EditorGUI gui, int page, int slot, ItemStack cur) {
        if (slot == 48 && cur.getType() == gui.getNavMaterial()) {
            gui.openRewardPool(p, session, page - 1);
            return;
        }
        if (slot == 50 && cur.getType() == gui.getNavMaterial()) {
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

    private void handleEditReward(InventoryClickEvent e, Player p, EditorSession session, EditorGUI gui, int slot) {
        if (slot == 18) {
            gui.openRewardPool(p, session, session.getPage("REWARD_POOL"));
            return;
        }

        String currentKey = session.getCurrentRewardKey();
        final String path = "rewards.pool." + currentKey;
        String currentType = session.getConfig().getString(path + ".type", "ITEM");

        if (slot == 10 && e.getClick() == ClickType.LEFT) {
            String nextType = switch (currentType) {
                case "ITEM" -> "COMMAND";
                case "COMMAND" -> "MMOITEM";
                case "MMOITEM" -> "LIFE_ITEM";
                case "LIFE_ITEM" -> "COOLDOWN_RESET";
                case "COOLDOWN_RESET" -> "COOLDOWN_REDUCE";
                case "COOLDOWN_REDUCE" -> "MYTHIC_ITEM";
                default -> "ITEM";
            };
            session.getConfig().set(path + ".type", nextType);
            gui.sendMessage(p, "type_changed", "<type>", nextType);
            gui.openRewardEditor(p, session);
        } else if (slot == 12) {
            if (e.getClick() == ClickType.RIGHT) {
                ItemStack hand = p.getInventory().getItemInMainHand();
                if (!hand.getType().isAir()) {
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
                        if (mmoParts.length >= 4) {
                            val = mmoParts[1] + ":" + mmoParts[2] + ":" + mmoParts[3];
                        } else {
                            val = hand.getType().name() + ":" + hand.getAmount();
                        }
                        session.getConfig().set(path + ".type", "MMOITEM");
                    } else {
                        val = hand.getType().name() + ":" + hand.getAmount();
                        session.getConfig().set(path + ".type", "ITEM");
                    }

                    session.getConfig().set(path + ".value", val);
                    gui.sendMessage(p, "item_set_hand", "<item>", val);
                    gui.openRewardEditor(p, session);
                } else {
                    gui.sendMessage(p, "hand_empty");
                }
            } else if (e.getClick() == ClickType.LEFT) {
                session.awaitInput(EditorSession.InputType.EDIT_STRING, "edit_reward_value_" + currentType.toLowerCase(Locale.ROOT), val -> {
                    session.getConfig().set(path + ".value", val);
                    gui.sendMessage(p, "update_val", "<key>", gui.getWord("value", "Value"), "<val>", val);
                    gui.openRewardEditor(p, session);
                });
                plugin.getEditorListener().startListening(p, session);
            }
        } else if (slot == 14 && e.getClick() == ClickType.LEFT) {
            session.awaitInput(EditorSession.InputType.EDIT_NUMBER, "edit_reward_chance", val -> {
                try {
                    double chance = Math.clamp(Double.parseDouble(val), 0.0, 100.0);
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

    private void handleStagesMenu(InventoryClickEvent e, Player p, EditorSession session, EditorGUI gui, int page, int slot, ItemStack cur) {
        if (slot == 48 && cur.getType() == gui.getNavMaterial()) {
            gui.openStageList(p, session, page - 1);
            return;
        }
        if (slot == 50 && cur.getType() == gui.getNavMaterial()) {
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
        } else if (slot == 51) {
            session.awaitInput(EditorSession.InputType.EDIT_NUMBER, "edit_insert_stage", val -> {
                try {
                    int pos = Integer.parseInt(val);
                    if (pos < 1) throw new NumberFormatException();

                    ConfigurationSection sec = session.getConfig().getConfigurationSection("stages");
                    if (sec != null) {
                        List<Integer> keys = new ArrayList<>();
                        for (String k : sec.getKeys(false)) {
                            try {
                                keys.add(Integer.parseInt(k));
                            } catch (Exception ignored) {
                            }
                        }
                        keys.sort(Collections.reverseOrder());
                        for (int k : keys) {
                            if (k >= pos) {
                                session.getConfig().set("stages." + (k + 1), session.getConfig().get("stages." + k));
                                session.getConfig().set("stages." + k, null);
                            }
                        }
                    }
                    session.getConfig().createSection("stages." + pos + ".actions");
                    session.getConfig().set("stages." + pos + ".chance", 100.0);
                    session.getConfig().set("stages." + pos + ".commands", new ArrayList<String>());

                    gui.sendMessage(p, "stage_inserted", "<pos>", String.valueOf(pos));
                    gui.openStageList(p, session, page);
                } catch (Exception ex) {
                    gui.sendMessage(p, "number_error");
                    gui.openStageList(p, session, page);
                }
            });
            plugin.getEditorListener().startListening(p, session);

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
                    final String stage = keys.get(actualIdx);
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
                                double c = Math.clamp(Double.parseDouble(val), 0.0, 100.0);
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

    private void handleActionsMenu(InventoryClickEvent e, Player p, EditorSession session, EditorGUI gui, int page, int slot, ItemStack cur) {
        if (slot == 48 && cur.getType() == gui.getNavMaterial()) {
            gui.openActionList(p, session, page - 1);
            return;
        }
        if (slot == 50 && cur.getType() == gui.getNavMaterial()) {
            gui.openActionList(p, session, page + 1);
            return;
        }

        if (slot == 49) {
            gui.openActionTypeSelector(p, session, session.getPage("SELECT_TYPE"));
        } else if (slot == 45) {
            gui.openStageList(p, session, session.getPage("STAGES"));
        } else if (slot < 45) {
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

    private void handleEditAction(InventoryClickEvent e, Player p, EditorSession session, EditorGUI gui, int slot, ItemStack cur) {
        if (slot == 45) {
            gui.openActionList(p, session, session.getPage("ACTIONS"));
            return;
        }
        if (cur.getType() == Material.BARRIER) return;

        final String key;
        if (cur.getItemMeta() != null && cur.getItemMeta().hasDisplayName()) {
            key = PlainTextComponentSerializer.plainText().serialize(cur.getItemMeta().displayName());
        } else {
            key = "";
        }

        if (key.isEmpty() || key.equalsIgnoreCase("type")) return;

        if (key.equalsIgnoreCase("items")) {
            gui.openActionChestEditor(p, session);
            return;
        }

        if (key.equalsIgnoreCase("notifications")) {
            gui.openNotificationEditor(p, session);
            return;
        }

        if (key.equalsIgnoreCase("phases")) {
            gui.openPhaseList(p, session, 0);
            return;
        }

        final String fullPath = "stages." + session.getCurrentStage() + ".actions." + session.getCurrentActionKey() + "." + key;
        Object rawValue = session.getConfig().get(fullPath);

        EditorGUI.FieldProperties props = EditorGUI.FieldProperties.resolve(key, rawValue, plugin);

        if (e.getClick() == ClickType.SHIFT_RIGHT) {
            if (props.isList) {
                session.getConfig().set(fullPath, new ArrayList<>());
            } else {
                session.getConfig().set(fullPath, null);
            }
            gui.sendMessage(p, "val_cleared");
            gui.openActionEditor(p, session);
            return;
        }

        if (props.isLocation) {
            if (e.getClick() == ClickType.RIGHT) {
                String locStr = String.format(Locale.US, "%.1f,%.1f,%.1f", p.getLocation().getX(), p.getLocation().getY(), p.getLocation().getZ());
                if (props.isList) {
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
            if (props.isList && !props.isLocation) {
                gui.openStringListEditor(p, session, fullPath, "EDIT_ACTION", 0);
                return;
            }

            String promptKey = key.equalsIgnoreCase("random_mobs") ? "edit_random_mobs" : "edit_action_" + key.toLowerCase(Locale.ROOT);

            session.awaitInput(props.inputType, promptKey, val -> {
                if (props.inputType == EditorSession.InputType.EDIT_NUMBER) {
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

                if (props.isList) {
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

    private void handleNotifications(Player p, EditorSession session, EditorGUI gui, int slot, ItemStack cur) {
        if (slot == 18) {
            gui.openActionEditor(p, session);
            return;
        }

        if (cur.getItemMeta() == null || !cur.getItemMeta().hasDisplayName()) return;

        String key = PlainTextComponentSerializer.plainText().serialize(cur.getItemMeta().displayName());
        if (!List.of("custom_start", "init", "progress", "complete", "warning").contains(key)) return;

        String path = "stages." + session.getCurrentStage() + ".actions." + session.getCurrentActionKey() + ".notifications." + key;
        boolean current = session.getConfig().getBoolean(path, true);
        session.getConfig().set(path, !current);
        gui.sendMessage(p, "notification_toggled", "<key>", key, "<state>", String.valueOf(!current));
        gui.openNotificationEditor(p, session);
    }

    private void handlePhaseList(InventoryClickEvent e, Player p, EditorSession session, EditorGUI gui, int page, int slot, ItemStack cur) {
        if (slot == 48 && cur.getType() == gui.getNavMaterial()) {
            gui.openPhaseList(p, session, page - 1);
            return;
        }
        if (slot == 50 && cur.getType() == gui.getNavMaterial()) {
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
                    int threshold = Math.clamp(Integer.parseInt(val), 1, 100);
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
            if (cur.getItemMeta() == null || !cur.getItemMeta().hasDisplayName()) return;
            final String threshold = PlainTextComponentSerializer.plainText().serialize(cur.getItemMeta().displayName()).replaceAll("[^0-9]", "");
            if (e.getClick() == ClickType.SHIFT_RIGHT) {
                session.getConfig().set("stages." + session.getCurrentStage() + ".actions." + session.getCurrentActionKey() + ".phases." + threshold, null);
                gui.openPhaseList(p, session, page);
            } else if (e.getClick() == ClickType.LEFT) {
                session.setCurrentPhaseThreshold(threshold);
                gui.openPhaseEditor(p, session);
            }
        }
    }

    private void handleEditPhase(InventoryClickEvent e, Player p, EditorSession session, EditorGUI gui, int slot) {
        if (slot == 18) {
            gui.openPhaseList(p, session, session.getPage("PHASE_LIST"));
            return;
        }
        final String basePath = "stages." + session.getCurrentStage() + ".actions." + session.getCurrentActionKey() + ".phases." + session.getCurrentPhaseThreshold();

        if (slot == 11 && e.getClick() == ClickType.LEFT) {
            session.awaitInput(EditorSession.InputType.EDIT_STRING, "edit_phase_message", val -> {
                String clearKw = plugin.getLanguageManager().getString("editor.words.clear", "clear");
                String finalVal = val.equalsIgnoreCase(clearKw) ? "" : val;
                session.getConfig().set(basePath + ".message", finalVal);
                gui.openPhaseEditor(p, session);
            });
            plugin.getEditorListener().startListening(p, session);
        } else if (slot == 13 && e.getClick() == ClickType.LEFT) {
            gui.openStringListEditor(p, session, basePath + ".attributes", "EDIT_PHASE", 0);
        } else if (slot == 15 && e.getClick() == ClickType.LEFT) {
            gui.openReinforcementEditor(p, session);
        }
    }

    private void handleEditReinforcements(InventoryClickEvent e, Player p, EditorSession session, EditorGUI gui, int slot) {
        if (slot == 18) {
            gui.openPhaseEditor(p, session);
            return;
        }
        final String basePath = "stages." + session.getCurrentStage() + ".actions." + session.getCurrentActionKey() + ".phases." + session.getCurrentPhaseThreshold() + ".reinforcements";

        if (slot == 10 && e.getClick() == ClickType.LEFT) {
            session.awaitInput(EditorSession.InputType.EDIT_STRING, "edit_reinforcement_mob", val -> {
                session.getConfig().set(basePath + ".mob", val.toUpperCase(Locale.ROOT));
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
                String finalVal = val.equalsIgnoreCase(clearKw) ? "" : val;
                session.getConfig().set(basePath + ".custom_name", finalVal);
                gui.openReinforcementEditor(p, session);
            });
            plugin.getEditorListener().startListening(p, session);
        } else if (slot == 14 && e.getClick() == ClickType.LEFT) {
            gui.openStringListEditor(p, session, basePath + ".attributes", "EDIT_REINFORCEMENTS", 0);
        } else if (slot == 16 && e.getClick() == ClickType.LEFT) {
            gui.openStringListEditor(p, session, basePath + ".equipment", "EDIT_REINFORCEMENTS", 0);
        }
    }

    private void handleStringList(InventoryClickEvent e, Player p, EditorSession session, EditorGUI gui, int page, int slot, ItemStack cur) {
        if (slot == 48 && cur.getType() == gui.getNavMaterial()) {
            gui.openStringListEditor(p, session, session.getCurrentListPath(), session.getCurrentListReturnMenu(), page - 1);
            return;
        }
        if (slot == 50 && cur.getType() == gui.getNavMaterial()) {
            gui.openStringListEditor(p, session, session.getCurrentListPath(), session.getCurrentListReturnMenu(), page + 1);
            return;
        }
        if (slot == 45) {
            switch (session.getCurrentListReturnMenu()) {
                case "SETTINGS" -> gui.openSettingsMenu(p, session, session.getPage("SETTINGS"));
                case "EDIT_ACTION" -> gui.openActionEditor(p, session);
                case "EDIT_PHASE" -> gui.openPhaseEditor(p, session);
                case "EDIT_REINFORCEMENTS" -> gui.openReinforcementEditor(p, session);
                default -> gui.openDungeonMenu(p, session);
            }
            return;
        }

        if (slot == 51 && cur.getType() == Material.TNT) {
            session.getConfig().set(session.getCurrentListPath(), new ArrayList<>());
            p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 1f, 1f);
            gui.sendMessage(p, "val_cleared");
            gui.openStringListEditor(p, session, session.getCurrentListPath(), session.getCurrentListReturnMenu(), page);
            return;
        }

        if (slot == 49) {
            String prompt = determinePrompt(session.getCurrentListPath());

            session.awaitInput(EditorSession.InputType.EDIT_LIST, prompt, val -> {
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

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof EditorHolder(
                EditorSession session, String menuType, int page
        ) && e.getPlayer() instanceof Player p) {
            if (session != null && "EDIT_ACTION_ITEMS".equals(menuType)) {
                String path = "stages." + session.getCurrentStage() + ".actions." + session.getCurrentActionKey() + ".items";
                session.getConfig().set(path, null);

                Inventory inv = e.getView().getTopInventory();

                NamespacedKey keyTag = new NamespacedKey(plugin, "dungeon_key_id");
                NamespacedKey lifeKey = new NamespacedKey(plugin, "life_amount");
                NamespacedKey resetKey = new NamespacedKey(plugin, "cooldown_reset");
                NamespacedKey reduceKey = new NamespacedKey(plugin, "cooldown_reduce");

                for (int i = 0; i < inv.getSize(); i++) {
                    ItemStack item = inv.getItem(i);
                    if (item != null && !item.getType().isAir()) {
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
