package net.danh.sinceDungeon.editor;

import net.danh.sinceDungeon.SinceDungeon;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class EditorListener implements Listener {
    private final SinceDungeon plugin;
    private final Set<UUID> chatLock = new HashSet<>();

    public EditorListener(SinceDungeon plugin) {
        this.plugin = plugin;
    }

    private void sendMsg(Player p, String key) {
        String s = plugin.getMessagesFile().getString("editor.chat." + key);
        if (s != null) p.sendMessage(MiniMessage.miniMessage().deserialize(s));
    }

    public void startListening(Player p) {
        chatLock.add(p.getUniqueId());
        p.closeInventory();

        List<String> lines = plugin.getMessagesFile().getStringList("editor.chat.input_start");
        for (String line : lines) {
            p.sendMessage(MiniMessage.miniMessage().deserialize(line));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        if (!chatLock.contains(p.getUniqueId())) return;

        e.setCancelled(true);
        chatLock.remove(p.getUniqueId());

        String msg = e.getMessage();
        EditorSession session = plugin.getEditorManager().getSession(p);

        // Trường hợp hủy hoặc chat sai: Quay về menu cũ
        if (msg.equalsIgnoreCase("cancel")) {
            sendMsg(p, "input_cancel");
            if (session != null) reopenSessionMenu(session);
            return;
        }

        // Lấy tọa độ
        if (msg.equalsIgnoreCase("here")) {
            org.bukkit.Location l = p.getLocation();
            msg = String.format("%.1f,%.1f,%.1f", l.getX(), l.getY(), l.getZ());
            String m = plugin.getMessagesFile().getString("editor.chat.input_here");
            if (m != null) p.sendMessage(MiniMessage.miniMessage().deserialize(m.replace("<loc>", msg)));
        }

        if (session == null) {
            sendMsg(p, "session_expired");
            return;
        }

        String finalValue = msg;
        Bukkit.getScheduler().runTask(plugin, () -> {
            EditorSession.InputType type = session.getInputType();

            if (type == EditorSession.InputType.CREATE_FILENAME) {
                plugin.getEditorManager().startEditing(p, finalValue);
            } else if (type == EditorSession.InputType.EDIT_VALUE) {
                // Try-catch ở đây để nếu input sai logic (vd nhập chữ vào chỗ số) thì không lỗi
                try {
                    session.completeInput(finalValue);
                } catch (Exception ex) {
                    // Nếu lỗi trong quá trình xử lý input (vd parse int), báo lỗi và mở lại menu
                    p.sendMessage(MiniMessage.miniMessage().deserialize("<red>Lỗi nhập liệu: " + ex.getMessage()));
                    session.reopenLastMenu();
                }
            }
        });
    }

    private void reopenSessionMenu(EditorSession session) {
        Bukkit.getScheduler().runTask(plugin, () -> session.reopenLastMenu());
    }
}