package net.danh.sinceDungeon.system;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.entity.Player;

public class PAPIHook {
    public static boolean checkCondition(Player p, String condition) {
        if (condition == null || !condition.contains(";")) return false;

        // [CHANGED] Format: %placeholder%;operator;value
        String[] parts = condition.split(";");
        if (parts.length < 3) return false;

        String leftRaw = parts[0];
        String operator = parts[1];
        String rightRaw = parts[2];

        String left = PlaceholderAPI.setPlaceholders(p, leftRaw).trim();
        String right = PlaceholderAPI.setPlaceholders(p, rightRaw).trim();

        try {
            double v1 = Double.parseDouble(left);
            double v2 = Double.parseDouble(right);
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
            // String comparison
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