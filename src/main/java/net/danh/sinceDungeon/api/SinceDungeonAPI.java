package net.danh.sinceDungeon.api;

import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.actions.ActionParser;
import net.danh.sinceDungeon.manager.DungeonGame;
import net.danh.sinceDungeon.manager.DungeonTemplate;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.Map;

public class SinceDungeonAPI {

    private static SinceDungeonAPI instance;
    private final SinceDungeon plugin;

    // Constructor private để đảm bảo Singleton
    private SinceDungeonAPI(SinceDungeon plugin) {
        this.plugin = plugin;
    }

    /**
     * Khởi tạo API (Chỉ gọi 1 lần trong onEnable của SinceDungeon)
     */
    public static void init(SinceDungeon plugin) {
        if (instance == null) {
            instance = new SinceDungeonAPI(plugin);
        }
    }

    /**
     * Lấy instance của API để sử dụng.
     * Cách dùng: SinceDungeonAPI.get().joinDungeon(player, "map1");
     */
    public static SinceDungeonAPI get() {
        if (instance == null) {
            throw new IllegalStateException("SinceDungeon chưa được enable hoặc chưa khởi tạo API!");
        }
        return instance;
    }

    // ========================== QUẢN LÝ GAMEPLAY ==========================

    /**
     * Cho người chơi tham gia Dungeon.
     *
     * @param player    Người chơi.
     * @param dungeonId Tên file config dungeon (không cần đuôi .yml).
     */
    public void joinDungeon(Player player, String dungeonId) {
        plugin.getDungeonManager().joinDungeon(player, dungeonId);
    }

    /**
     * Buộc người chơi rời Dungeon hiện tại.
     */
    public void quitDungeon(Player player) {
        plugin.getDungeonManager().quitDungeon(player);
    }

    /**
     * Kiểm tra người chơi có đang trong Dungeon không.
     */
    public boolean isPlaying(Player player) {
        return plugin.getDungeonManager().getActiveGames().containsKey(player.getUniqueId());
    }

    /**
     * Lấy đối tượng DungeonGame của người chơi.
     * Cho phép can thiệp sâu (lấy world, stage hiện tại, force complete...).
     */
    public DungeonGame getGame(Player player) {
        return plugin.getDungeonManager().getGame(player.getUniqueId());
    }

    /**
     * Lấy Template của Dungeon dựa trên ID.
     */
    public DungeonTemplate getTemplate(String dungeonId) {
        return plugin.getDungeonManager().getTemplates().get(dungeonId);
    }

    // ========================== MỞ RỘNG (EXTENSION API) ==========================

    /**
     * [QUAN TRỌNG] Đăng ký một Action mới từ plugin bên ngoài.
     * Giúp mở rộng tính năng mà không cần sửa code gốc.
     *
     * @param key           Tên định danh Action trong config (VD: "GIVE_BUFF", "SEND_TITLE").
     * @param parser        Logic để đọc config và tạo Action.
     * @param icon          Icon hiển thị trong Editor.
     * @param description   Mô tả hiển thị trong Editor.
     * @param defaultParams Các tham số mặc định khi tạo mới trong Editor.
     */
    public void registerCustomAction(String key, ActionParser parser, Material icon, String description, Map<String, Object> defaultParams) {
        plugin.getDungeonManager().registerAction(key, parser, icon, description, defaultParams);
        plugin.getLogger().info("[API] Đã đăng ký Custom Action: " + key);
    }
}