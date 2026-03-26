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

/**
 * Manages editor sessions for players.
 */
public class EditorManager implements Listener {
    private final SinceDungeon plugin;
    private final Map<UUID, EditorSession> sessions = new HashMap<>();

    /**
     * Constructs the EditorManager.
     *
     * @param plugin The main plugin instance.
     */
    public EditorManager(SinceDungeon plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Opens the main editor menu for a player.
     *
     * @param p The player.
     */
    public void openEditor(Player p) {
        new EditorGUI(plugin).openMainMenu(p);
    }

    /**
     * Initiates the editing of a specific dungeon file.
     *
     * @param p        The player editing the dungeon.
     * @param filename The name of the dungeon file.
     */
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

    /**
     * Gets the active editor session for a player.
     *
     * @param p The player.
     * @return The active EditorSession, or null if none exists.
     */
    public EditorSession getSession(Player p) {
        return sessions.get(p.getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        sessions.remove(e.getPlayer().getUniqueId());
    }
}