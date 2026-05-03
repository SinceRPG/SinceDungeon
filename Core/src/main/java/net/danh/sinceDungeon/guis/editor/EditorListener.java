package net.danh.sinceDungeon.guis.editor;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.managers.DungeonManager;
import net.danh.sinceDungeon.utils.ColorUtils;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class EditorListener implements Listener {
    private final SinceDungeon plugin;
    private final Map<UUID, EditorSession> activeInputs = new ConcurrentHashMap<>();

    public EditorListener(SinceDungeon plugin) {
        this.plugin = plugin;
    }

    private void sendMsg(Player p, String key) {
        String s = plugin.getLanguageManager().getString("editor.chat." + key);
        if (s != null) p.sendMessage(ColorUtils.parseWithPrefix(s));
    }

    public void startListening(Player p, EditorSession session) {
        activeInputs.put(p.getUniqueId(), session);
        p.closeInventory();

        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 2f);

        String titleMain = plugin.getLanguageManager().getString("editor.input.title_main", "<gold><bold>INPUT MODE");
        String titleSub = plugin.getLanguageManager().getString("editor.input.title_sub", "<white>Check the chat for instructions!");
        Title.Times times = Title.Times.times(Duration.ofMillis(100), Duration.ofSeconds(2), Duration.ofMillis(500));
        Title title = Title.title(ColorUtils.parse(titleMain), ColorUtils.parse(titleSub), times);
        p.showTitle(title);

        String prefix = plugin.getLanguageManager().getString("prefix", "");
        String header = plugin.getLanguageManager().getString("editor.input.header", "<yellow>=== INPUT MODE ===");
        p.sendMessage(ColorUtils.parse(prefix + header));

        List<String> prompts = null;

        if (session.getPromptKey() != null && session.getPromptKey().startsWith("edit_action_")) {
            String fieldName = session.getPromptKey().substring("edit_action_".length());

            if (session.getCurrentActionKey() != null && session.getCurrentStage() != null) {
                String actionType = session.getConfig().getString("stages." + session.getCurrentStage() + ".actions." + session.getCurrentActionKey() + ".type");
                if (actionType != null) {
                    DungeonManager.ActionMeta meta = plugin.getDungeonManager().getActionMeta(actionType);
                    if (meta != null && meta.customPrompts() != null && meta.customPrompts().containsKey(fieldName)) {
                        prompts = meta.customPrompts().get(fieldName);
                    }
                }
            }
        }

        if (prompts == null && session.getPromptKey() != null) {
            prompts = plugin.getLanguageManager().getStringList("editor.input.prompts." + session.getPromptKey());
        }

        if (prompts == null || prompts.isEmpty()) {
            String defaultTypeKey = session.getInputType().name().toLowerCase();
            prompts = plugin.getLanguageManager().getStringList("editor.input.prompts." + defaultTypeKey);
        }

        if (prompts == null || prompts.isEmpty()) {
            prompts = plugin.getLanguageManager().getStringList("editor.input.prompts.default");
        }

        for (String line : prompts) {
            p.sendMessage(ColorUtils.parse(line));
        }

        if (session.getInputType() == EditorSession.InputType.EDIT_LOCATION || session.getInputType() == EditorSession.InputType.EDIT_LOCATION_LIST) {
            String rightClickHint = plugin.getLanguageManager().getString("editor.input.right_click_hint", "<aqua>or RIGHT-CLICK on any Block to get its coordinates.");
            p.sendMessage(ColorUtils.parse(rightClickHint));
        }

        String cancelHint = plugin.getLanguageManager().getString("editor.input.cancel_hint", "<gray>Type <red>cancel <gray>to abort.");
        p.sendMessage(ColorUtils.parse(cancelHint));

        String footer = plugin.getLanguageManager().getString("editor.input.footer", "<yellow>=====================");
        p.sendMessage(ColorUtils.parse(prefix + footer));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (!activeInputs.containsKey(p.getUniqueId())) return;

        EditorSession session = activeInputs.get(p.getUniqueId());
        if (session.getInputType() == EditorSession.InputType.EDIT_LOCATION || session.getInputType() == EditorSession.InputType.EDIT_LOCATION_LIST) {
            if (e.getAction() == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock() != null) {
                e.setCancelled(true);
                Location l = e.getClickedBlock().getLocation();
                String msg = String.format(Locale.US, "%d,%d,%d", l.getBlockX(), l.getBlockY(), l.getBlockZ());

                activeInputs.remove(p.getUniqueId());

                Bukkit.getScheduler().runTask(plugin, () -> {
                    try {
                        session.completeInput(msg);
                    } catch (Exception ex) {
                        String msg_error = plugin.getLanguageManager().getString("editor.chat.input_error");
                        if (msg_error != null)
                            p.sendMessage(ColorUtils.parseWithPrefix(msg_error.replace("<error>", ex.getMessage())));
                        session.reopenLastMenu();
                    }
                });

                String m = plugin.getLanguageManager().getString("editor.chat.input_here");
                String prefix = plugin.getLanguageManager().getString("prefix", "");
                if (m != null) p.sendMessage(ColorUtils.parseWithPrefix(prefix + m.replace("<loc>", msg)));
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent e) {
        Player p = e.getPlayer();
        if (!activeInputs.containsKey(p.getUniqueId())) return;

        e.setCancelled(true);
        EditorSession session = activeInputs.remove(p.getUniqueId());

        String msg = PlainTextComponentSerializer.plainText().serialize(e.message()).trim();

        String cancelKw = plugin.getLanguageManager().getString("editor.words.cancel", "cancel").trim();
        String hereKw = plugin.getLanguageManager().getString("editor.words.here", "here").trim();

        if (msg.equalsIgnoreCase(cancelKw)) {
            sendMsg(p, "input_cancel");
            if (session != null) reopenSessionMenu(session);
            return;
        }

        if (msg.equalsIgnoreCase(hereKw)) {
            if (session.getInputType() == EditorSession.InputType.EDIT_LOCATION || session.getInputType() == EditorSession.InputType.EDIT_LOCATION_LIST) {
                Location l = p.getLocation();
                msg = String.format(Locale.US, "%.1f,%.1f,%.1f", l.getX(), l.getY(), l.getZ());

                String m = plugin.getLanguageManager().getString("editor.chat.input_here");
                String prefix = plugin.getLanguageManager().getString("prefix", "");
                if (m != null) p.sendMessage(ColorUtils.parseWithPrefix(prefix + m.replace("<loc>", msg)));
            }
        }

        String finalValue = msg;
        Bukkit.getScheduler().runTask(plugin, () -> {
            EditorSession.InputType type = session.getInputType();

            if (type == EditorSession.InputType.CREATE_FILENAME) {
                plugin.getEditorManager().startEditing(p, finalValue);
            } else {
                try {
                    session.completeInput(finalValue);
                } catch (Exception ex) {
                    String msg_error = plugin.getLanguageManager().getString("editor.chat.input_error");
                    if (msg_error != null)
                        p.sendMessage(ColorUtils.parseWithPrefix(msg_error.replace("<error>", ex.getMessage())));
                    session.reopenLastMenu();
                }
            }
        });
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (activeInputs.containsKey(p.getUniqueId())) {
            e.setCancelled(true);

            String cmd = e.getMessage().toLowerCase().trim();

            String cancelKwStr = plugin.getLanguageManager().getString("editor.words.cancel", "cancel").toLowerCase().trim();
            String cancelKw = cancelKwStr.startsWith("/") ? cancelKwStr : "/" + cancelKwStr;

            if (cmd.equals(cancelKw) || cmd.equals("/cancel") || cmd.startsWith(cancelKw + " ") || cmd.startsWith("/cancel ")) {
                EditorSession session = activeInputs.remove(p.getUniqueId());
                sendMsg(p, "input_cancel");
                if (session != null) reopenSessionMenu(session);
                return;
            }

            String msg = plugin.getLanguageManager().getString("admin.in_editor", "<red>You are in editor mode! Please type cancel.");
            p.sendMessage(ColorUtils.parseWithPrefix(msg));
        }
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

    public void clearAll() {
        activeInputs.clear();
    }
}