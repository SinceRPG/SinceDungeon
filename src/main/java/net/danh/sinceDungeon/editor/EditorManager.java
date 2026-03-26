package net.danh.sinceDungeon.editor;

import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.utils.ColorUtils;
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
    private final Map<UUID, EditorSession> sessions = new HashMap<>();

    public EditorManager(SinceDungeon plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void openEditor(Player p) {
        new EditorGUI(plugin).openMainMenu(p, 0); // Mở trang đầu tiên
    }

    public void startEditing(Player p, String filename) {
        if (!filename.matches("^[a-zA-Z0-9_\\-]+$")) {
            String msg = plugin.getMessagesFile().getString("editor.chat.invalid_filename");
            if (msg != null) p.sendMessage(ColorUtils.parseWithPrefix(msg));
            return;
        }
        if (!filename.endsWith(".yml")) filename += ".yml";
        File file = new File(plugin.getDataFolder(), "dungeons/" + filename);

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

        EditorSession current = sessions.get(p.getUniqueId());

        if (current == null || !current.getFile().getName().equals(file.getName())) {
            current = new EditorSession(plugin, p, file);
            sessions.put(p.getUniqueId(), current);
        }

        new EditorGUI(plugin).openDungeonMenu(p, current);
    }

    public EditorSession getSession(Player p) {
        return sessions.get(p.getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        sessions.remove(e.getPlayer().getUniqueId());
    }
}