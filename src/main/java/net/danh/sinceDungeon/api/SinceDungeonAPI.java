package net.danh.sinceDungeon.api;

import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.actions.ActionParser;
import net.danh.sinceDungeon.api.interfaces.ConditionProcessor;
import net.danh.sinceDungeon.api.interfaces.RewardProcessor;
import net.danh.sinceDungeon.manager.DungeonGame;
import net.danh.sinceDungeon.manager.DungeonManager;
import net.danh.sinceDungeon.manager.DungeonTemplate;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class SinceDungeonAPI {

    private static SinceDungeonAPI instance;
    private final SinceDungeon plugin;

    private SinceDungeonAPI(SinceDungeon plugin) {
        this.plugin = plugin;
    }

    public static void init(SinceDungeon plugin) {
        instance = new SinceDungeonAPI(plugin);
    }

    public static SinceDungeonAPI get() {
        if (instance == null) {
            throw new IllegalStateException("SinceDungeon chưa được enable hoặc chưa khởi tạo API!");
        }
        return instance;
    }

    // ========================== QUẢN LÝ GAMEPLAY CƠ BẢN ==========================

    public void joinDungeon(Player player, String dungeonId) {
        plugin.getDungeonManager().joinDungeon(player, dungeonId);
    }

    public void quitDungeon(Player player) {
        plugin.getDungeonManager().quitDungeon(player);
    }

    public boolean isPlaying(Player player) {
        return plugin.getDungeonManager().getActiveGames().containsKey(player.getUniqueId());
    }

    public DungeonGame getGame(Player player) {
        return plugin.getDungeonManager().getGame(player.getUniqueId());
    }

    public DungeonGame getGame(UUID uuid) {
        return plugin.getDungeonManager().getGame(uuid);
    }

    // ========================== MỞ RỘNG (EXTENSION API) ==========================

    public void registerCustomAction(String type, ActionParser parser, String displayName, Material icon, String description, Map<String, Object> defaultParams, Map<String, List<String>> customPrompts) {
        plugin.getDungeonManager().registerAction(type, parser, displayName, icon, description, defaultParams, customPrompts);
        plugin.getLogger().info("[API] Đã đăng ký Custom Action: " + type);
    }

    /**
     * Đăng ký hệ thống trả thưởng tùy chỉnh (VD: TOKEN_ENCHANT, MYTHIC_DROP)
     */
    public void registerRewardProcessor(String type, RewardProcessor processor) {
        plugin.getDungeonManager().registerRewardProcessor(type, processor);
        plugin.getLogger().info("[API] Đã đăng ký Reward Processor: " + type.toUpperCase());
    }

    /**
     * Đăng ký hệ thống kiểm tra điều kiện tùy chỉnh (VD: QUEST_REQUIREMENT)
     */
    public void registerConditionProcessor(String type, ConditionProcessor processor) {
        plugin.getDungeonManager().registerConditionProcessor(type, processor);
        plugin.getLogger().info("[API] Đã đăng ký Condition Processor: " + type.toUpperCase());
    }

    public void registerTemplate(DungeonTemplate template) {
        plugin.getDungeonManager().registerTemplate(template);
    }

    public void unregisterTemplate(String dungeonId) {
        plugin.getDungeonManager().unregisterTemplate(dungeonId);
    }

    public DungeonTemplate getTemplate(String dungeonId) {
        return plugin.getDungeonManager().getTemplates().get(dungeonId);
    }

    public Set<String> getAvailableTemplates() {
        return plugin.getDungeonManager().getTemplates().keySet();
    }

    public DungeonManager getManager() {
        return plugin.getDungeonManager();
    }
}