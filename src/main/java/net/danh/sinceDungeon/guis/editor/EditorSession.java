package net.danh.sinceDungeon.guis.editor;

import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.managers.DungeonLoader;
import net.danh.sinceDungeon.models.DungeonTemplate;
import net.danh.sinceDungeon.utils.ColorUtils;
import net.danh.sinceDungeon.utils.SoundUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Manages the data state of a player currently using the editor.
 * Maintains context variables such as current stage, action, list paths, and configuration settings.
 */
public class EditorSession {
    private final SinceDungeon plugin;
    private final Player player;
    private final File file;
    private final YamlConfiguration config;
    private final Map<String, Integer> pageCache = new HashMap<>();

    private String currentStage = null;
    private String currentActionKey = null;
    private String currentRewardKey = null;
    private String currentConditionKey = null;

    private String currentListPath = null;
    private String currentListReturnMenu = null;

    private InputType currentInput = InputType.NONE;
    private String promptKey = null;
    private EditorCallback inputCallback = null;
    private Consumer<Player> lastMenuOpener = null;

    public EditorSession(SinceDungeon plugin, Player player, File file) {
        this.plugin = plugin;
        this.player = player;
        this.file = file;
        if (file != null && file.exists()) {
            this.config = YamlConfiguration.loadConfiguration(file);
        } else {
            this.config = new YamlConfiguration();
        }
    }

    /**
     * Asynchronously saves the current YAML configuration to the disk.
     * Triggers a live hot-reload into the DungeonManager memory map upon success.
     */
    public void save() {
        if (file == null) return;
        String yamlData = config.saveToString();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                file.getParentFile().mkdirs();
                Files.writeString(file.toPath(), yamlData);

                Bukkit.getScheduler().runTask(plugin, () -> {
                    String msg = plugin.getMessagesFile().getString("editor.chat.saved");
                    if (msg != null && player.isOnline()) player.sendMessage(ColorUtils.parseWithPrefix(msg));

                    String soundName = plugin.getConfigFile().getString("sounds.editor_save");
                    Sound sound = SoundUtils.getSound(soundName);
                    if (sound != null && player.isOnline()) {
                        player.playSound(player.getLocation(), sound, 1f, 1f);
                    }

                    try {
                        String id = file.getName().replace(".yml", "");
                        DungeonTemplate updatedTemplate = DungeonLoader.loadTemplate(plugin, id);
                        if (updatedTemplate != null) {
                            plugin.getDungeonManager().registerTemplate(updatedTemplate);
                        }
                    } catch (Exception ex) {
                        plugin.getLogger().warning("Failed to hot-reload template after saving: " + file.getName());
                    }
                });
            } catch (IOException e) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    String errorMsg = plugin.getMessagesFile().getString("editor.chat.save_error");
                    if (errorMsg != null && player.isOnline())
                        player.sendMessage(ColorUtils.parse(errorMsg.replace("<error>", e.getMessage())));
                });
                plugin.getLogger().severe("Error saving Dungeon: " + e.getMessage());
            }
        });
    }

    public void awaitInput(InputType type, String promptKey, EditorCallback callback) {
        this.currentInput = type;
        this.promptKey = promptKey;
        this.inputCallback = callback;
    }

    public void completeInput(String value) {
        if (inputCallback != null) inputCallback.onInput(value);
        this.currentInput = InputType.NONE;
        this.promptKey = null;
    }

    public void reopenLastMenu() {
        if (lastMenuOpener != null && player.isOnline()) lastMenuOpener.accept(player);
    }

    public Player getPlayer() {
        return player;
    }

    public YamlConfiguration getConfig() {
        return config;
    }

    public File getFile() {
        return file;
    }

    public String getCurrentStage() {
        return currentStage;
    }

    public void setCurrentStage(String stage) {
        this.currentStage = stage;
    }

    public String getCurrentActionKey() {
        return currentActionKey;
    }

    public void setCurrentActionKey(String key) {
        this.currentActionKey = key;
    }

    public String getCurrentRewardKey() {
        return currentRewardKey;
    }

    public void setCurrentRewardKey(String key) {
        this.currentRewardKey = key;
    }

    public String getCurrentConditionKey() {
        return currentConditionKey;
    }

    public void setCurrentConditionKey(String key) {
        this.currentConditionKey = key;
    }

    public String getCurrentListPath() {
        return currentListPath;
    }

    public void setCurrentListPath(String path) {
        this.currentListPath = path;
    }

    public String getCurrentListReturnMenu() {
        return currentListReturnMenu;
    }

    public void setCurrentListReturnMenu(String menu) {
        this.currentListReturnMenu = menu;
    }

    public int getPage(String menuType) {
        return pageCache.getOrDefault(menuType, 0);
    }

    public void setPage(String menuType, int page) {
        pageCache.put(menuType, page);
    }

    public InputType getInputType() {
        return currentInput;
    }

    public String getPromptKey() {
        return promptKey;
    }

    public void setLastMenuOpener(Consumer<Player> opener) {
        this.lastMenuOpener = opener;
    }

    public void cancelInput() {
        this.currentInput = InputType.NONE;
        this.promptKey = null;
        this.inputCallback = null;
    }

    public enum InputType {
        NONE, CREATE_FILENAME, EDIT_STRING, EDIT_NUMBER, EDIT_BOOLEAN, EDIT_LOCATION, EDIT_LOCATION_LIST, EDIT_LIST, EDIT_TIER, EDIT_CONDITION_CHECK, EDIT_KICK_DELAY
    }

    /**
     * Enumerates all configurable map-specific settings.
     * Contains definitions required to dynamically construct the Settings Editor GUI.
     */
    public enum SettingOption {
        KEEP_INV("settings.keep-inventory-on-death", "dungeon.gameplay.keep-inventory-on-death", Material.TOTEM_OF_UNDYING, "setting_keep_inv", "BOOL", true),
        PREVENT_DROP("settings.prevent-item-dropping", "dungeon.gameplay.prevent-item-dropping", Material.BARRIER, "setting_prevent_drop", "BOOL", true),
        BLOCK_PEARLS("settings.block-ender-pearls", "dungeon.gameplay.block-ender-pearls", Material.ENDER_PEARL, "setting_block_pearls", "BOOL", true),
        KICK_DELAY("settings.kick-delay-after-finish", "dungeon.gameplay.kick-delay-after-finish", Material.CLOCK, "setting_kick_delay", "INT", 10),
        FORCE_WEATHER("settings.force-daylight-and-clear-weather", "dungeon.gameplay.force-daylight-and-clear-weather", Material.SUNFLOWER, "setting_force_weather", "BOOL", true),
        SAVE_STATS("settings.save-and-restore-stats", "dungeon.save-and-restore-stats", Material.GOLDEN_APPLE, "setting_save_stats", "BOOL", false),
        DEATH_ACTION("settings.death-action", "dungeon.death-action", Material.SKELETON_SKULL, "setting_death_action", "DEATH_ENUM", "RESPAWN"),
        CLEAR_DROPS("settings.clear-mob-drops", "dungeon.clear-mob-drops", Material.ROTTEN_FLESH, "setting_clear_drops", "BOOL", true),
        REQ_LIVES("settings.required-lives-to-join", null, Material.RED_BED, "setting_req_lives", "INT", 1),
        DEDUCT_LIVES("settings.lives-deducted-per-death", null, Material.WITHER_ROSE, "setting_deduct_lives", "INT", 1),
        RANDOMIZE_STAGES("settings.randomize-stages", "dungeon.gameplay.randomize-stages", Material.ENDER_EYE, "setting_randomize_stages", "BOOL", false),
        MAX_PLAYERS("settings.max-players", null, Material.PLAYER_HEAD, "setting_max_players", "INT", -1),
        COOLDOWN("settings.cooldown-seconds", null, Material.CAMPFIRE, "setting_cooldown", "INT", 0),
        CMD_START("settings.commands.on-start", null, Material.COMMAND_BLOCK, "setting_cmd_start", "LIST", null),
        CMD_FINISH("settings.commands.on-finish", null, Material.REPEATING_COMMAND_BLOCK, "setting_cmd_finish", "LIST", null),
        CMD_FIRST_FINISH("settings.commands.on-first-finish", null, Material.COMMAND_BLOCK_MINECART, "setting_cmd_first_finish", "LIST", null);

        private final String localPath;
        private final String globalFallbackPath;
        private final Material icon;
        private final String langKey;
        private final String dataType;
        private final Object defaultValue;

        SettingOption(String localPath, String globalFallbackPath, Material icon, String langKey, String dataType, Object defaultValue) {
            this.localPath = localPath;
            this.globalFallbackPath = globalFallbackPath;
            this.icon = icon;
            this.langKey = langKey;
            this.dataType = dataType;
            this.defaultValue = defaultValue;
        }

        public String getLocalPath() {
            return localPath;
        }

        public String getGlobalFallbackPath() {
            return globalFallbackPath;
        }

        public Material getIcon() {
            return icon;
        }

        public String getLangKey() {
            return langKey;
        }

        public String getDataType() {
            return dataType;
        }

        public Object getDefaultValue() {
            return defaultValue;
        }
    }

    public interface EditorCallback {
        void onInput(String value);
    }
}