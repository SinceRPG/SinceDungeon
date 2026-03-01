package net.danh.sinceDungeon.editor;

import net.danh.sinceDungeon.SinceDungeon;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class EditorManager implements Listener {
    private final SinceDungeon plugin;
    // Map lưu trữ session. Dữ liệu sẽ tồn tại ở đây dù tắt GUI.
    private final Map<UUID, EditorSession> sessions = new HashMap<>();

    public EditorManager(SinceDungeon plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void openEditor(Player p) {
        new EditorGUI(plugin).openMainMenu(p);
    }

    public void startEditing(Player p, String filename) {
        if (!filename.matches("^[a-zA-Z0-9_\\-]+$")) {
            p.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize("<red>Tên file không hợp lệ! Vui lòng không dùng ký tự đặc biệt."));
            return;
        }
        if (!filename.endsWith(".yml")) filename += ".yml";
        File file = new File(plugin.getDataFolder(), "dungeons/" + filename);

        // Tạo file vật lý nếu chưa có (để tránh lỗi FileNotFound)
        if (!file.exists()) {
            try {
                file.getParentFile().mkdirs();
                file.createNewFile();
                String msg = plugin.getMessagesFile().getString("editor.chat.created");
                if (msg != null) p.sendMessage(msg.replace("<file>", filename));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // Kiểm tra xem đã có session của file này chưa
        EditorSession current = sessions.get(p.getUniqueId());

        // Nếu chưa có session, hoặc session cũ là của file khác -> Tạo mới
        if (current == null || !current.getFile().getName().equals(file.getName())) {
            current = new EditorSession(plugin, p, file);
            sessions.put(p.getUniqueId(), current);
        }

        // Mở GUI từ session (cũ hoặc mới)
        new EditorGUI(plugin).openDungeonMenu(p, current);
    }

    public EditorSession getSession(Player p) {
        return sessions.get(p.getUniqueId());
    }

    // Chỉ xóa session khi người chơi thoát server để giải phóng bộ nhớ
    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        sessions.remove(e.getPlayer().getUniqueId());
    }
}