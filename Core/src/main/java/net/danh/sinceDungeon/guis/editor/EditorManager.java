package net.danh.sinceDungeon.guis.editor;

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
        new EditorGUI(plugin).openMainMenu(p, 0);
    }

    public void startEditing(Player p, String filename) {
        if (!filename.matches("^[a-zA-Z0-9_\\-]+$")) {
            String msg = plugin.getLanguageManager().getString("editor.chat.invalid_filename");
            if (msg != null) p.sendMessage(ColorUtils.parseWithPrefix(msg));
            return;
        }
        if (!filename.endsWith(".yml")) filename += ".yml";
        File file = new File(plugin.getDataFolder(), "dungeons/" + filename);

        if (!file.exists()) {
            try {
                file.getParentFile().mkdirs();
                file.createNewFile();
                String msg = plugin.getLanguageManager().getString("editor.chat.created");
                if (msg != null) p.sendMessage(msg.replace("<file>", filename));
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to create dungeon editor file " + filename + ": " + e.getMessage());
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

    public void clearAll() {
        for (UUID uuid : sessions.keySet()) {
            Player p = plugin.getServer().getPlayer(uuid);
            if (p != null && p.isOnline()) {
                p.closeInventory();
            }
        }
        sessions.clear();
    }
}
