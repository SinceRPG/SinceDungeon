package net.danh.sinceDungeon.editor;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.utils.ColorUtils;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.time.Duration;
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
        String s = plugin.getMessagesFile().getString("editor.chat." + key);
        if (s != null) p.sendMessage(ColorUtils.parseWithPrefix(s));
    }

    public void startListening(Player p, EditorSession session) {
        activeInputs.put(p.getUniqueId(), session);
        p.closeInventory();

        // [UX]: Đánh thức sự chú ý của Admin bằng Title và Sound
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 2f);
        Title.Times times = Title.Times.times(Duration.ofMillis(100), Duration.ofSeconds(2), Duration.ofMillis(500));
        Title title = Title.title(ColorUtils.parse("<gold><bold>NHẬP LIỆU"), ColorUtils.parse("<white>Hãy xem hướng dẫn ở khung Chat!"), times);
        p.showTitle(title);

        String prefix = plugin.getMessagesFile().getString("prefix", "");
        p.sendMessage(ColorUtils.parse(prefix + "<yellow>=== CHẾ ĐỘ NHẬP LIỆU ==="));

        switch (session.getInputType()) {
            case CREATE_FILENAME -> {
                p.sendMessage(ColorUtils.parse("<gray>Nhập tên cho Hầm ngục mới."));
                p.sendMessage(ColorUtils.parse("<gray>Chỉ cho phép: <white>Chữ cái (a-z), Số (0-9), gạch ngang/dưới."));
                p.sendMessage(ColorUtils.parse("<gray>Ví dụ: <green>ham_nguc_so_1"));
            }
            case EDIT_LOCATION -> {
                p.sendMessage(ColorUtils.parse("<gray>Nhập tọa độ theo định dạng: <white>X,Y,Z"));
                p.sendMessage(ColorUtils.parse("<gray>Mẹo: Gõ <green>here <gray>để lấy tọa độ bạn đang đứng."));
            }
            case EDIT_LOCATION_LIST -> {
                p.sendMessage(ColorUtils.parse("<gray>Nhập tọa độ <white>X,Y,Z <gray>để thêm vào danh sách."));
                p.sendMessage(ColorUtils.parse("<gray>Mẹo: Gõ <green>here <gray>để lấy tọa độ bạn đang đứng."));
                p.sendMessage(ColorUtils.parse("<gray>Mẹo: Gõ <red>clear <gray>để xóa sạch danh sách tọa độ này."));
            }
            case EDIT_NUMBER -> p.sendMessage(ColorUtils.parse("<gray>Vui lòng nhập một <green>con số<gray>."));
            case EDIT_BOOLEAN ->
                    p.sendMessage(ColorUtils.parse("<gray>Vui lòng nhập <green>true <gray>hoặc <red>false<gray>."));
            case EDIT_TIER -> {
                p.sendMessage(ColorUtils.parse("<gray>Nhập Thời gian và Số rương, cách nhau bởi khoảng trắng."));
                p.sendMessage(ColorUtils.parse("<gray>Ví dụ: <green>300 3 <gray>(Hoàn thành trước 300s được 3 rương)"));
            }
            case EDIT_CONDITION_CHECK -> {
                p.sendMessage(ColorUtils.parse("<gray>Nhập điều kiện theo định dạng: <white>%placeholder%;toán_tử;giá_trị"));
                p.sendMessage(ColorUtils.parse("<gray>Ví dụ: <green>%vault_eco_balance%;>=;500"));
            }
            case EDIT_LIST -> {
                p.sendMessage(ColorUtils.parse("<gray>Nhập văn bản/giá trị để thêm vào danh sách."));
                p.sendMessage(ColorUtils.parse("<gray>Mẹo: Gõ <red>clear <gray>để xóa sạch danh sách này."));
            }
            default -> p.sendMessage(ColorUtils.parse("<gray>Vui lòng nhập giá trị mới vào chat."));
        }

        p.sendMessage(ColorUtils.parse("<gray>Gõ <red>cancel <gray>để hủy thao tác."));
        p.sendMessage(ColorUtils.parse(prefix + "<yellow>====================="));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent e) {
        Player p = e.getPlayer();
        if (!activeInputs.containsKey(p.getUniqueId())) return;

        e.setCancelled(true);
        EditorSession session = activeInputs.remove(p.getUniqueId());
        String msg = PlainTextComponentSerializer.plainText().serialize(e.message());

        if (msg.equalsIgnoreCase("cancel")) {
            sendMsg(p, "input_cancel");
            if (session != null) reopenSessionMenu(session);
            return;
        }

        if (msg.equalsIgnoreCase("here")) {
            if (session.getInputType() == EditorSession.InputType.EDIT_LOCATION || session.getInputType() == EditorSession.InputType.EDIT_LOCATION_LIST) {
                org.bukkit.Location l = p.getLocation();
                msg = String.format(Locale.US, "%.1f,%.1f,%.1f", l.getX(), l.getY(), l.getZ());

                String m = plugin.getMessagesFile().getString("editor.chat.input_here");
                String prefix = plugin.getMessagesFile().getString("prefix", "");
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
                    String msg_error = plugin.getMessagesFile().getString("editor.chat.input_error");
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
            String msg = plugin.getMessagesFile().getString("prefix", "") + "<red>Bạn đang trong chế độ chỉnh sửa! Vui lòng gõ <yellow>cancel<red> vào chat để hủy trước khi dùng lệnh.";
            p.sendMessage(ColorUtils.parse(msg));
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
}