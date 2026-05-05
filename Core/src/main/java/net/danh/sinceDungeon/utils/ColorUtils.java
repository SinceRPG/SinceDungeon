package net.danh.sinceDungeon.utils;

import net.danh.sinceDungeon.SinceDungeon;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles text coloring logic and MiniMessage serializations.
 */
public class ColorUtils {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([a-fA-F0-9]{6})");

    /**
     * Safely deserializes a legacy string into a Kyori Adventure Component.
     *
     * @param input The text containing color codes.
     * @return A parsed adventure Component.
     */
    public static @NotNull Component parse(@NotNull String input) {
        String safeInput = convertLegacyToMiniMessage(input);
        try {
            return MINI_MESSAGE.deserialize(safeInput);
        } catch (Exception e) {
            SinceDungeon.getPlugin().getLogger().warning("MiniMessage Syntax Error in string: '" + input + "'. Please verify your messages configuration!");
            return Component.text(input);
        }
    }

    /**
     * Automatically prepends the plugin prefix and deserializes into an adventure Component.
     *
     * @param input The base text.
     * @return The parsed Component.
     */
    public static @NotNull Component parseWithPrefix(@NotNull String input) {
        String prefix = SinceDungeon.getPlugin().getLanguageManager().getString("prefix", "");
        return parse(prefix + input);
    }

    /**
     * Cleans up an adventure component into raw plaintext.
     *
     * @param component The formatted component.
     * @return Raw plaintext data.
     */
    public static @NotNull String toPlainText(@NotNull Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    /**
     * Normalizes old bukkit ampersand-based and hex tags into pure MiniMessage tags.
     *
     * @param text The legacy text.
     * @return Clean MiniMessage string.
     */
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

    /**
     * Formats raw Enum names (e.g. IRON_NUGGET) into Human Readable titles (Iron Nugget).
     *
     * @param name The raw enum string.
     * @return Formatted Title-Case string.
     */
    public static String formatEnumName(String name) {
        if (name == null || name.isEmpty()) return "";
        String[] words = name.toLowerCase(Locale.ROOT).split("_");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(" ");
        }
        return sb.toString().trim();
    }
}