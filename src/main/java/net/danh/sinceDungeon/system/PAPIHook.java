package net.danh.sinceDungeon.system;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class PAPIHook {

    public static String setPlaceholders(Player p, String text) {
        if (text == null) return "";
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return PlaceholderAPI.setPlaceholders(p, text);
        }
        return text;
    }

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