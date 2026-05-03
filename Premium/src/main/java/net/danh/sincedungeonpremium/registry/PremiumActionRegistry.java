package net.danh.sincedungeonpremium.registry;

import net.danh.sinceDungeon.api.SinceDungeonAPI;
import net.danh.sincedungeonpremium.SinceDungeonPremium;
import net.danh.sincedungeonpremium.actions.BranchingPathAction;
import net.danh.sincedungeonpremium.actions.BuffAction;
import net.danh.sincedungeonpremium.actions.EscortAction;
import net.danh.sincedungeonpremium.actions.LeverPuzzleAction;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Handles the registration of all Premium custom actions.
 * Extracted from the main class to reduce file size and improve modular management.
 */
public class PremiumActionRegistry {

    /**
     * Helper method to parse lists from unknown generic objects securely.
     */
    private static List<String> parseList(Object obj) {
        List<String> list = new ArrayList<>();
        if (obj instanceof List<?> l) {
            for (Object o : l) list.add(o.toString());
        }
        return list;
    }

    /**
     * Safely parses an integer from a generic object, returning a fallback on failure.
     */
    private static int parseSafeInt(Object obj, int fallback) {
        if (obj == null) return fallback;
        if (obj instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(obj.toString().trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /**
     * Safely parses a double from a generic object, returning a fallback on failure.
     */
    private static double parseSafeDouble(Object obj, double fallback) {
        if (obj == null) return fallback;
        if (obj instanceof Number number) return number.doubleValue();
        try {
            return Double.parseDouble(obj.toString().trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /**
     * Primary execution method to register all premium actions into the Core API.
     *
     * @param plugin The SinceDungeonPremium instance handling the registration.
     */
    public static void registerAll(SinceDungeonPremium plugin) {
        SinceDungeonAPI api = SinceDungeonAPI.get();

        // ---------------------------------------------------------
        // 1. APPLY BUFF ACTION
        // ---------------------------------------------------------
        Map<String, Object> buffDefaults = new HashMap<>();
        buffDefaults.put("effect", plugin.getFileManager().getConfig().getString("action-defaults.apply_buff.default-effect"));
        buffDefaults.put("duration", plugin.getFileManager().getConfig().getInt("action-defaults.apply_buff.default-duration"));
        buffDefaults.put("amplifier", plugin.getFileManager().getConfig().getInt("action-defaults.apply_buff.default-amplifier"));
        buffDefaults.put("objective_text", plugin.getFileManager().getConfig().getString("action-defaults.apply_buff.objective_text"));

        Map<String, List<String>> buffPrompts = new HashMap<>();
        buffPrompts.put("effect", plugin.getFileManager().getMessages().getStringList("prompts.buff_effect"));
        buffPrompts.put("duration", plugin.getFileManager().getMessages().getStringList("prompts.buff_duration"));
        buffPrompts.put("amplifier", plugin.getFileManager().getMessages().getStringList("prompts.buff_amplifier"));

        api.registerCustomAction(
                "APPLY_BUFF",
                map -> new BuffAction(
                        String.valueOf(map.getOrDefault("effect", buffDefaults.get("effect"))),
                        parseSafeInt(map.get("duration"), (int) buffDefaults.get("duration")),
                        parseSafeInt(map.get("amplifier"), (int) buffDefaults.get("amplifier")),
                        String.valueOf(map.getOrDefault("objective_text", buffDefaults.get("objective_text")))
                ),
                plugin.getFileManager().getConfig().getString("gui.actions.apply_buff.name"),
                Material.POTION,
                plugin.getFileManager().getConfig().getString("gui.actions.apply_buff.desc"),
                buffDefaults,
                buffPrompts
        );

        // ---------------------------------------------------------
        // 2. ESCORT NPC ACTION
        // ---------------------------------------------------------
        Map<String, Object> escortDefaults = new HashMap<>();
        escortDefaults.put("mob", plugin.getFileManager().getConfig().getString("action-defaults.escort.default-mob"));
        escortDefaults.put("name", plugin.getFileManager().getConfig().getString("action-defaults.escort.default-name"));
        escortDefaults.put("health", plugin.getFileManager().getConfig().getDouble("action-defaults.escort.default-health"));
        escortDefaults.put("start_location", "0,64,0");
        escortDefaults.put("target_location", "10,64,10");
        escortDefaults.put("speed", plugin.getFileManager().getConfig().getDouble("action-defaults.escort.default-speed"));
        escortDefaults.put("radius", plugin.getFileManager().getConfig().getDouble("action-defaults.escort.default-radius"));
        escortDefaults.put("vip_is_baby", plugin.getFileManager().getConfig().getBoolean("action-defaults.escort.vip_is_baby"));
        escortDefaults.put("vip_attributes", plugin.getFileManager().getConfig().getStringList("action-defaults.escort.vip_attributes"));
        escortDefaults.put("vip_equipment", plugin.getFileManager().getConfig().getStringList("action-defaults.escort.vip_equipment"));

        escortDefaults.put("attacker_mob", plugin.getFileManager().getConfig().getString("action-defaults.escort.attacker-mob"));
        escortDefaults.put("attacker_amount", plugin.getFileManager().getConfig().getInt("action-defaults.escort.attacker-amount"));
        escortDefaults.put("attacker_interval", plugin.getFileManager().getConfig().getInt("action-defaults.escort.attacker-interval"));
        escortDefaults.put("attacker_name", plugin.getFileManager().getConfig().getString("action-defaults.escort.attacker_name"));
        escortDefaults.put("attacker_is_baby", plugin.getFileManager().getConfig().getBoolean("action-defaults.escort.attacker_is_baby"));
        escortDefaults.put("attacker_attributes", plugin.getFileManager().getConfig().getStringList("action-defaults.escort.attacker_attributes"));
        escortDefaults.put("attacker_equipment", plugin.getFileManager().getConfig().getStringList("action-defaults.escort.attacker_equipment"));

        escortDefaults.put("objective_text", plugin.getFileManager().getConfig().getString("action-defaults.escort.objective_text"));

        Map<String, List<String>> escortPrompts = new HashMap<>();
        escortPrompts.put("start_location", plugin.getFileManager().getMessages().getStringList("prompts.escort_start"));
        escortPrompts.put("target_location", plugin.getFileManager().getMessages().getStringList("prompts.escort_target"));
        escortPrompts.put("mob", plugin.getFileManager().getMessages().getStringList("prompts.escort_mob"));
        escortPrompts.put("vip_is_baby", plugin.getFileManager().getMessages().getStringList("prompts.vip_is_baby"));
        escortPrompts.put("vip_attributes", plugin.getFileManager().getMessages().getStringList("prompts.vip_attributes"));
        escortPrompts.put("vip_equipment", plugin.getFileManager().getMessages().getStringList("prompts.vip_equipment"));

        escortPrompts.put("attacker_mob", plugin.getFileManager().getMessages().getStringList("prompts.escort_attacker_mob"));
        escortPrompts.put("attacker_interval", plugin.getFileManager().getMessages().getStringList("prompts.escort_attacker_interval"));
        escortPrompts.put("attacker_name", plugin.getFileManager().getMessages().getStringList("prompts.escort_attacker_name"));
        escortPrompts.put("attacker_is_baby", plugin.getFileManager().getMessages().getStringList("prompts.escort_attacker_is_baby"));
        escortPrompts.put("attacker_attributes", plugin.getFileManager().getMessages().getStringList("prompts.escort_attacker_attributes"));
        escortPrompts.put("attacker_equipment", plugin.getFileManager().getMessages().getStringList("prompts.escort_attacker_equipment"));

        api.registerCustomAction(
                "ESCORT_NPC",
                map -> new EscortAction(
                        String.valueOf(map.getOrDefault("mob", escortDefaults.get("mob"))),
                        String.valueOf(map.getOrDefault("name", escortDefaults.get("name"))),
                        parseSafeDouble(map.get("health"), (double) escortDefaults.get("health")),
                        String.valueOf(map.getOrDefault("start_location", escortDefaults.get("start_location"))),
                        String.valueOf(map.getOrDefault("target_location", escortDefaults.get("target_location"))),
                        parseSafeDouble(map.get("speed"), (double) escortDefaults.get("speed")),
                        parseSafeDouble(map.get("radius"), (double) escortDefaults.get("radius")),
                        Boolean.parseBoolean(String.valueOf(map.getOrDefault("vip_is_baby", escortDefaults.get("vip_is_baby")))),
                        parseList(map.getOrDefault("vip_attributes", escortDefaults.get("vip_attributes"))),
                        parseList(map.getOrDefault("vip_equipment", escortDefaults.get("vip_equipment"))),
                        String.valueOf(map.getOrDefault("attacker_mob", escortDefaults.get("attacker_mob"))),
                        parseSafeInt(map.get("attacker_amount"), (int) escortDefaults.get("attacker_amount")),
                        parseSafeInt(map.get("attacker_interval"), (int) escortDefaults.get("attacker_interval")),
                        String.valueOf(map.getOrDefault("attacker_name", escortDefaults.get("attacker_name"))),
                        Boolean.parseBoolean(String.valueOf(map.getOrDefault("attacker_is_baby", escortDefaults.get("attacker_is_baby")))),
                        parseList(map.getOrDefault("attacker_attributes", escortDefaults.get("attacker_attributes"))),
                        parseList(map.getOrDefault("attacker_equipment", escortDefaults.get("attacker_equipment"))),
                        String.valueOf(map.getOrDefault("objective_text", escortDefaults.get("objective_text")))
                ),
                plugin.getFileManager().getConfig().getString("gui.actions.escort.name"),
                Material.MINECART,
                plugin.getFileManager().getConfig().getString("gui.actions.escort.desc"),
                escortDefaults,
                escortPrompts
        );

        // ---------------------------------------------------------
        // 3. BRANCHING PATHS ACTION
        // ---------------------------------------------------------
        Map<String, Object> branchDefaults = new HashMap<>();
        branchDefaults.put("path_a_loc", "0,64,0");
        branchDefaults.put("path_b_loc", "10,64,10");
        branchDefaults.put("stage_a", 3);
        branchDefaults.put("stage_b", 4);
        branchDefaults.put("radius", 3.0);
        branchDefaults.put("objective_text", plugin.getFileManager().getConfig().getString("action-defaults.branch.objective_text"));

        Map<String, List<String>> branchPrompts = new HashMap<>();
        branchPrompts.put("path_a_loc", plugin.getFileManager().getMessages().getStringList("prompts.branch_path_a"));
        branchPrompts.put("path_b_loc", plugin.getFileManager().getMessages().getStringList("prompts.branch_path_b"));
        branchPrompts.put("stage_a", plugin.getFileManager().getMessages().getStringList("prompts.branch_stage_a"));
        branchPrompts.put("stage_b", plugin.getFileManager().getMessages().getStringList("prompts.branch_stage_b"));

        api.registerCustomAction(
                "BRANCHING_PATH",
                map -> new BranchingPathAction(
                        String.valueOf(map.getOrDefault("path_a_loc", branchDefaults.get("path_a_loc"))),
                        String.valueOf(map.getOrDefault("path_b_loc", branchDefaults.get("path_b_loc"))),
                        parseSafeInt(map.get("stage_a"), (int) branchDefaults.get("stage_a")),
                        parseSafeInt(map.get("stage_b"), (int) branchDefaults.get("stage_b")),
                        parseSafeDouble(map.get("radius"), (double) branchDefaults.get("radius")),
                        String.valueOf(map.getOrDefault("objective_text", branchDefaults.get("objective_text")))
                ),
                plugin.getFileManager().getConfig().getString("gui.actions.branch.name"),
                Material.OAK_SIGN,
                plugin.getFileManager().getConfig().getString("gui.actions.branch.desc"),
                branchDefaults,
                branchPrompts
        );

        // ---------------------------------------------------------
        // 4. LEVER PUZZLE ACTION
        // ---------------------------------------------------------
        Map<String, Object> puzzleDefaults = new HashMap<>();
        puzzleDefaults.put("levers", new ArrayList<>(Arrays.asList("0,64,0", "2,64,0", "4,64,0")));
        puzzleDefaults.put("objective_text", plugin.getFileManager().getConfig().getString("action-defaults.puzzle.objective_text"));

        Map<String, List<String>> puzzlePrompts = new HashMap<>();
        puzzlePrompts.put("levers", plugin.getFileManager().getMessages().getStringList("prompts.levers"));

        api.registerCustomAction(
                "LEVER_PUZZLE",
                map -> {
                    List<String> levers = new ArrayList<>();
                    Object obj = map.get("levers");
                    if (obj instanceof List<?> l) {
                        for (Object o : l) levers.add(o.toString());
                    }
                    String objText = String.valueOf(map.getOrDefault("objective_text", puzzleDefaults.get("objective_text")));
                    return new LeverPuzzleAction(levers, objText);
                },
                plugin.getFileManager().getConfig().getString("gui.actions.puzzle.name"),
                Material.LEVER,
                plugin.getFileManager().getConfig().getString("gui.actions.puzzle.desc"),
                puzzleDefaults,
                puzzlePrompts
        );
    }
}