package net.danh.sinceDungeon.utils;

import net.danh.sinceDungeon.SinceDungeon;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ColorUtils {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([a-fA-F0-9]{6})");

    public static @NotNull Component parse(@NotNull String input) {
        String safeInput = convertLegacyToMiniMessage(input);
        try {
            return MINI_MESSAGE.deserialize(safeInput);
        } catch (Exception e) {
            SinceDungeon.getPlugin().getLogger().warning("Lỗi cú pháp màu sắc/MiniMessage ở đoạn text: '" + input + "'. Vui lòng kiểm tra lại file messages.yml!");
            return Component.text(input);
        }
    }

    public static @NotNull Component parseWithPrefix(@NotNull String input) {
        String prefix = SinceDungeon.getPlugin().getMessagesFile().getString("prefix", "");
        return parse(prefix + input);
    }

    public static @NotNull String toPlainText(@NotNull Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    public static String convertLegacyToMiniMessage(String text) {
        if (text == null) return "";

        text = text.replace("§", "&");

        Matcher matcher = HEX_PATTERN.matcher(text);
        StringBuilder buffer = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(buffer, "<#" + matcher.group(1) + ">");
        }
        matcher.appendTail(buffer);
        text = buffer.toString();

        if (!text.contains("&")) return text;

        text = text.replace("&0", "<black>");
        text = text.replace("&1", "<dark_blue>");
        text = text.replace("&2", "<dark_green>");
        text = text.replace("&3", "<dark_aqua>");
        text = text.replace("&4", "<dark_red>");
        text = text.replace("&5", "<dark_purple>");
        text = text.replace("&6", "<gold>");
        text = text.replace("&7", "<gray>");
        text = text.replace("&8", "<dark_gray>");
        text = text.replace("&9", "<blue>");
        text = text.replace("&a", "<green>");
        text = text.replace("&b", "<aqua>");
        text = text.replace("&c", "<red>");
        text = text.replace("&d", "<light_purple>");
        text = text.replace("&e", "<yellow>");
        text = text.replace("&f", "<white>");

        text = text.replace("&k", "<obfuscated>");
        text = text.replace("&l", "<bold>");
        text = text.replace("&m", "<strikethrough>");
        text = text.replace("&n", "<underlined>");
        text = text.replace("&o", "<italic>");
        text = text.replace("&r", "<reset>");

        return text;
    }
}