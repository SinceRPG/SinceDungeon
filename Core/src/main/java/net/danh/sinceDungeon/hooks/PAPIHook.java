package net.danh.sinceDungeon.hooks;

import me.clip.placeholderapi.PlaceholderAPI;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.danh.sinceDungeon.SinceDungeon;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles interactions with the PlaceholderAPI plugin.
 */
public class PAPIHook {
    private static final List<PlaceholderExpansion> REGISTERED_EXPANSIONS = new ArrayList<>();

    /**
     * Parses a string containing PAPI placeholders into its true values.
     *
     * @param p    The player context.
     * @param text The text containing placeholders.
     * @return The evaluated string.
     */
    public static String setPlaceholders(Player p, String text) {
        if (text == null) return "";
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return PlaceholderAPI.setPlaceholders(p, text);
        }
        return text;
    }

    /**
     * Registers all internal expansions to the PlaceholderAPI instance.
     */
    public static void register(SinceDungeon plugin) {
        unregister();

        LivesExpansion livesExpansion = new LivesExpansion(plugin);
        TopExpansion topExpansion = new TopExpansion(plugin);

        livesExpansion.register();
        topExpansion.register();
        REGISTERED_EXPANSIONS.add(livesExpansion);
        REGISTERED_EXPANSIONS.add(topExpansion);

        plugin.getLogger().info(plugin.getLanguageManager().getString("admin.log.papi_registered", "Successfully registered PlaceholderAPI integration for SinceDungeon."));
    }

    public static void unregister() {
        for (PlaceholderExpansion expansion : REGISTERED_EXPANSIONS) {
            if (expansion instanceof TopExpansion topExpansion) {
                topExpansion.cleanup();
            }
            expansion.unregister();
        }
        REGISTERED_EXPANSIONS.clear();
        TopExpansion.cancelCacheTask();
    }

    /**
     * Evaluates a specific PAPI conditional string expression.
     * Evaluates format: '%placeholder%;operator;value'
     *
     * @param p         The player context.
     * @param condition The raw condition string to parse and check.
     * @return True if the condition is successfully evaluated to true.
     */
    public static boolean checkCondition(Player p, String condition) {
        if (condition == null || !condition.contains(";")) return false;

        String[] parts = condition.split(";");
        if (parts.length < 3) return false;

        String leftRaw = parts[0];
        String operator = parts[1];
        String rightRaw = parts[2];

        String left = setPlaceholders(p, leftRaw).trim();
        String right = setPlaceholders(p, rightRaw).trim();

        try {
            String cleanLeft = left.replace(",", "").replace(" ", "");
            String cleanRight = right.replace(",", "").replace(" ", "");

            double v1 = Double.parseDouble(cleanLeft);
            double v2 = Double.parseDouble(cleanRight);
            switch (operator) {
                case ">=":
                    return v1 >= v2;
                case "<=":
                    return v1 <= v2;
                case ">":
                    return v1 > v2;
                case "<":
                    return v1 < v2;
                case "==":
                    return v1 == v2;
                case "!=":
                    return v1 != v2;
            }
        } catch (NumberFormatException e) {
            switch (operator) {
                case "==":
                    return left.equals(right);
                case "!=":
                    return !left.equals(right);
                case "equalsIgnoreCase":
                    return left.equalsIgnoreCase(right);
            }
        }
        return false;
    }
}
