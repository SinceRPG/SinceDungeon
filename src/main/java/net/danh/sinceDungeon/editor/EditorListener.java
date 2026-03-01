package net.danh.sinceDungeon.editor;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.danh.sinceDungeon.SinceDungeon;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class EditorListener implements Listener {
    private final SinceDungeon plugin;
    // Đổi Set thành Map để giữ Session hiện tại
    private final Map<UUID, EditorSession> activeInputs = new ConcurrentHashMap<>();

    public EditorListener(SinceDungeon plugin) {
        this.plugin = plugin;
    }

    private void sendMsg(Player p, String key) {
        String s = plugin.getMessagesFile().getString("editor.chat." + key);
        if (s != null) p.sendMessage(MiniMessage.miniMessage().deserialize(s));
    }

    // Yêu cầu truyền trực tiếp EditorSession vào đây
    public void startListening(Player p, EditorSession session) {
        activeInputs.put(p.getUniqueId(), session);
        p.closeInventory();

        List<String> lines = plugin.getMessagesFile().getStringList("editor.chat.input_start");
        for (String line : lines) {
            p.sendMessage(MiniMessage.miniMessage().deserialize(line));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent e) {
        Player p = e.getPlayer();
        if (!activeInputs.containsKey(p.getUniqueId())) return;

        e.setCancelled(true);
        // Lấy session ra và xóa khỏi map chờ
        EditorSession session = activeInputs.remove(p.getUniqueId());
        String msg = PlainTextComponentSerializer.plainText().serialize(e.message());

        if (msg.equalsIgnoreCase("cancel")) {
            sendMsg(p, "input_cancel");
            if (session != null) reopenSessionMenu(session);
            return;
        }

        if (msg.equalsIgnoreCase("here")) {
            org.bukkit.Location l = p.getLocation();
            msg = String.format("%.1f,%.1f,%.1f", l.getX(), l.getY(), l.getZ());
            String m = plugin.getMessagesFile().getString("editor.chat.input_here");
            if (m != null) p.sendMessage(MiniMessage.miniMessage().deserialize(m.replace("<loc>", msg)));
        }

        String finalValue = msg;
        Bukkit.getScheduler().runTask(plugin, () -> {
            EditorSession.InputType type = session.getInputType();

            if (type == EditorSession.InputType.CREATE_FILENAME) {
                plugin.getEditorManager().startEditing(p, finalValue);
            } else if (type == EditorSession.InputType.EDIT_VALUE) {
                try {
                    session.completeInput(finalValue);
                } catch (Exception ex) {
                    p.sendMessage(MiniMessage.miniMessage().deserialize("<red>Lỗi nhập liệu: " + ex.getMessage()));
                    session.reopenLastMenu();
                }
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        EditorSession session = activeInputs.remove(p.getUniqueId());
        if (session != null) {
            session.cancelInput();
        }
    }

    private void reopenSessionMenu(EditorSession session) {
        Bukkit.getScheduler().runTask(plugin, session::reopenLastMenu);
    }
}