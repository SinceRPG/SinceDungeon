package net.danh.sinceDungeon.api;

import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.actions.ActionParser;
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
     * Đăng ký một loại Action hoàn toàn mới bằng Code với đầy đủ tùy chỉnh hiển thị và gợi ý chat.
     *
     * @param type          ID của Action (VD: "MY_CUSTOM_ACTION")
     * @param parser        Lớp xử lý logic phân tích Action
     * @param displayName   Tên hiển thị trong GUI (VD: "<green>Hành Động Tùy Chỉnh")
     * @param icon          Icon hiển thị trong GUI
     * @param description   Mô tả ngắn hiển thị dưới Icon
     * @param defaultParams Các thông số mặc định khi tạo mới
     * @param customPrompts Map chứa các tin nhắn gợi ý khi nhập liệu ở Chat (Key là tên Field, Value là List tin nhắn)
     */
    public void registerCustomAction(String type, ActionParser parser, String displayName, Material icon, String description, Map<String, Object> defaultParams, Map<String, List<String>> customPrompts) {
        plugin.getDungeonManager().registerAction(type, parser, displayName, icon, description, defaultParams, customPrompts);
        plugin.getLogger().info("[API] Đã đăng ký Custom Action: " + type);
    }

    /**
     * Đăng ký một Dungeon Template (Map) trực tiếp thông qua Code thay vì file YAML.
     */
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