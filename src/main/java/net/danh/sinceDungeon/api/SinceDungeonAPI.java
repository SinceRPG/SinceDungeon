package net.danh.sinceDungeon.api;

import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.actions.ActionParser;
import net.danh.sinceDungeon.manager.DungeonGame;
import net.danh.sinceDungeon.manager.DungeonManager;
import net.danh.sinceDungeon.manager.DungeonTemplate;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class SinceDungeonAPI {

    private static SinceDungeonAPI instance;
    private final SinceDungeon plugin;

    private SinceDungeonAPI(SinceDungeon plugin) {
        this.plugin = plugin;
    }

    /**
     * Khởi tạo API (Chỉ gọi 1 lần trong onEnable của SinceDungeon)
     */
    public static void init(SinceDungeon plugin) {
        instance = new SinceDungeonAPI(plugin);
    }

    /**
     * Lấy instance của API để sử dụng.
     * Cách dùng: SinceDungeonAPI.get()....
     */
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

    /**
     * Đăng ký một loại Action hoàn toàn mới bằng Code.
     */
    public void registerCustomAction(String key, ActionParser parser, Material icon, String description, Map<String, Object> defaultParams) {
        plugin.getDungeonManager().registerAction(key, parser, icon, description, defaultParams);
        plugin.getLogger().info("[API] Đã đăng ký Custom Action: " + key);
    }

    /**
     * Đăng ký một Dungeon Template (Map) trực tiếp thông qua Code thay vì file YAML.
     * Hữu ích cho các mini-game tự gen map.
     */
    public void registerTemplate(DungeonTemplate template) {
        plugin.getDungeonManager().registerTemplate(template);
    }

    /**
     * Xóa một Template khỏi hệ thống bộ nhớ.
     */
    public void unregisterTemplate(String dungeonId) {
        plugin.getDungeonManager().unregisterTemplate(dungeonId);
    }

    public DungeonTemplate getTemplate(String dungeonId) {
        return plugin.getDungeonManager().getTemplates().get(dungeonId);
    }

    /**
     * Lấy toàn bộ danh sách Template ID đang có.
     */
    public Set<String> getAvailableTemplates() {
        return plugin.getDungeonManager().getTemplates().keySet();
    }

    /**
     * Trả về Instance của DungeonManager để lập trình viên tự do can thiệp cực sâu.
     */
    public DungeonManager getManager() {
        return plugin.getDungeonManager();
    }
}