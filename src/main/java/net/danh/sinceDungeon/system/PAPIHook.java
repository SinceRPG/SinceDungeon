package net.danh.sinceDungeon.system;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.entity.Player;

public class PAPIHook {
    public static boolean checkCondition(Player p, String condition) {
        String parsed = PlaceholderAPI.setPlaceholders(p, condition);
        String[] operators = {">=", "<=", "==", "!=", ">", "<"};
        String operator = null;

        for (String op : operators) {
            if (parsed.contains(op)) {
                operator = op;
                break;
            }
        }

        if (operator == null) return true;

        String[] parts = parsed.split(operator);
        String left = parts[0].trim();
        String right = parts[1].trim();

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
            switch (operator) {
                case "==":
                    return left.equals(right);
                case "!=":
                    return !left.equals(right);
            }
        }
        return false;
    }
}