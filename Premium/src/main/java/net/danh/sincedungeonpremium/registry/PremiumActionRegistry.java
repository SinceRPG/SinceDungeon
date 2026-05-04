package net.danh.sincedungeonpremium.registry;

import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.api.SinceDungeonAPI;
import net.danh.sinceDungeon.managers.LanguageManager;
import net.danh.sincedungeonpremium.SinceDungeonPremium;
import net.danh.sincedungeonpremium.actions.BranchingPathAction;
import net.danh.sincedungeonpremium.actions.BuffAction;
import net.danh.sincedungeonpremium.actions.CheckpointAction;
import net.danh.sincedungeonpremium.actions.DamageZoneAction;
import net.danh.sincedungeonpremium.actions.EscortAction;
import net.danh.sincedungeonpremium.actions.LeverPuzzleAction;
import org.bukkit.Material;

import java.util.*;

/**
 * Handles the registration of all Premium custom actions.
 * Directly reads GUI prompts and language from the Core's LanguageManager.
 */
public class PremiumActionRegistry {

    private static List<String> parseList(Object obj) {
        List<String> list = new ArrayList<>();
        if (obj instanceof List<?> l) {
            for (Object o : l) list.add(o.toString());
        }
        return list;
    }

    private static int parseSafeInt(Object obj, int fallback) {
        if (obj == null) return fallback;
        if (obj instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(obj.toString().trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static double parseSafeDouble(Object obj, double fallback) {
        if (obj == null) return fallback;
        if (obj instanceof Number number) return number.doubleValue();
        try {
            return Double.parseDouble(obj.toString().trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public static void registerAll(SinceDungeonPremium plugin) {
        SinceDungeonAPI api = SinceDungeonAPI.get();
        LanguageManager coreLang = SinceDungeon.getPlugin().getLanguageManager();

        // 1. APPLY BUFF ACTION
        Map<String, Object> buffDefaults = new HashMap<>();
        buffDefaults.put("effect", plugin.getFileManager().getConfig().getString("action-defaults.apply_buff.default-effect"));
        buffDefaults.put("duration", plugin.getFileManager().getConfig().getInt("action-defaults.apply_buff.default-duration"));
        buffDefaults.put("amplifier", plugin.getFileManager().getConfig().getInt("action-defaults.apply_buff.default-amplifier"));

        Map<String, List<String>> buffPrompts = new HashMap<>();
        buffPrompts.put("effect", coreLang.getStringList("editor.input.prompts.edit_action_effect"));
        buffPrompts.put("duration", coreLang.getStringList("editor.input.prompts.edit_action_duration"));
        buffPrompts.put("amplifier", coreLang.getStringList("editor.input.prompts.edit_action_amplifier"));

        api.registerCustomAction(
                "APPLY_BUFF",
                map -> new BuffAction(
                        String.valueOf(map.getOrDefault("effect", buffDefaults.get("effect"))),
                        parseSafeInt(map.get("duration"), (int) buffDefaults.get("duration")),
                        parseSafeInt(map.get("amplifier"), (int) buffDefaults.get("amplifier"))
                ),
                coreLang.getString("editor.actions_name.apply_buff"),
                Material.POTION,
                coreLang.getString("editor.actions.apply_buff"),
                buffDefaults,
                buffPrompts
        );

        // 2. ESCORT NPC ACTION
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

        escortDefaults.put("attacker_mob", plugin.getFileManager().getConfig().getString("action-defaults.escort.attacker_mob"));
        escortDefaults.put("attacker_amount", plugin.getFileManager().getConfig().getInt("action-defaults.escort.attacker_amount"));
        escortDefaults.put("attacker_interval", plugin.getFileManager().getConfig().getInt("action-defaults.escort.attacker_interval"));
        escortDefaults.put("attacker_name", plugin.getFileManager().getConfig().getString("action-defaults.escort.attacker_name"));
        escortDefaults.put("attacker_is_baby", plugin.getFileManager().getConfig().getBoolean("action-defaults.escort.attacker_is_baby"));
        escortDefaults.put("attacker_attributes", plugin.getFileManager().getConfig().getStringList("action-defaults.escort.attacker_attributes"));
        escortDefaults.put("attacker_equipment", plugin.getFileManager().getConfig().getStringList("action-defaults.escort.attacker_equipment"));

        Map<String, List<String>> escortPrompts = new HashMap<>();
        escortPrompts.put("start_location", coreLang.getStringList("editor.input.prompts.edit_action_start_location"));
        escortPrompts.put("target_location", coreLang.getStringList("editor.input.prompts.edit_action_target_location"));
        escortPrompts.put("mob", coreLang.getStringList("editor.input.prompts.edit_action_mob"));
        escortPrompts.put("vip_is_baby", coreLang.getStringList("editor.input.prompts.edit_action_vip_is_baby"));
        escortPrompts.put("vip_attributes", coreLang.getStringList("editor.input.prompts.edit_action_vip_attributes"));
        escortPrompts.put("vip_equipment", coreLang.getStringList("editor.input.prompts.edit_action_vip_equipment"));
        escortPrompts.put("attacker_mob", coreLang.getStringList("editor.input.prompts.edit_action_attacker_mob"));
        escortPrompts.put("attacker_interval", coreLang.getStringList("editor.input.prompts.edit_action_attacker_interval"));
        escortPrompts.put("attacker_name", coreLang.getStringList("editor.input.prompts.edit_action_attacker_name"));
        escortPrompts.put("attacker_is_baby", coreLang.getStringList("editor.input.prompts.edit_action_attacker_is_baby"));
        escortPrompts.put("attacker_attributes", coreLang.getStringList("editor.input.prompts.edit_action_attacker_attributes"));
        escortPrompts.put("attacker_equipment", coreLang.getStringList("editor.input.prompts.edit_action_attacker_equipment"));

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
                        parseList(map.getOrDefault("attacker_equipment", escortDefaults.get("attacker_equipment")))
                ),
                coreLang.getString("editor.actions_name.escort_npc"),
                Material.MINECART,
                coreLang.getString("editor.actions.escort_npc"),
                escortDefaults,
                escortPrompts
        );

        // 3. BRANCHING PATHS ACTION
        Map<String, Object> branchDefaults = new HashMap<>();
        branchDefaults.put("path_a_loc", "0,64,0");
        branchDefaults.put("path_b_loc", "10,64,10");
        branchDefaults.put("stage_a", 3);
        branchDefaults.put("stage_b", 4);
        branchDefaults.put("radius", 3.0);

        Map<String, List<String>> branchPrompts = new HashMap<>();
        branchPrompts.put("path_a_loc", coreLang.getStringList("editor.input.prompts.edit_action_path_a_loc"));
        branchPrompts.put("path_b_loc", coreLang.getStringList("editor.input.prompts.edit_action_path_b_loc"));
        branchPrompts.put("stage_a", coreLang.getStringList("editor.input.prompts.edit_action_stage_a"));
        branchPrompts.put("stage_b", coreLang.getStringList("editor.input.prompts.edit_action_stage_b"));

        api.registerCustomAction(
                "BRANCHING_PATH",
                map -> new BranchingPathAction(
                        String.valueOf(map.getOrDefault("path_a_loc", branchDefaults.get("path_a_loc"))),
                        String.valueOf(map.getOrDefault("path_b_loc", branchDefaults.get("path_b_loc"))),
                        parseSafeInt(map.get("stage_a"), (int) branchDefaults.get("stage_a")),
                        parseSafeInt(map.get("stage_b"), (int) branchDefaults.get("stage_b")),
                        parseSafeDouble(map.get("radius"), (double) branchDefaults.get("radius"))
                ),
                coreLang.getString("editor.actions_name.branching_path"),
                Material.OAK_SIGN,
                coreLang.getString("editor.actions.branching_path"),
                branchDefaults,
                branchPrompts
        );

        // 4. LEVER PUZZLE ACTION
        Map<String, Object> puzzleDefaults = new HashMap<>();
        puzzleDefaults.put("levers", new ArrayList<>(Arrays.asList("0,64,0", "2,64,0", "4,64,0")));

        Map<String, List<String>> puzzlePrompts = new HashMap<>();
        puzzlePrompts.put("levers", coreLang.getStringList("editor.input.prompts.edit_action_levers"));

        api.registerCustomAction(
                "LEVER_PUZZLE",
                map -> {
                    List<String> levers = new ArrayList<>();
                    Object obj = map.get("levers");
                    if (obj instanceof List<?> l) {
                        for (Object o : l) levers.add(o.toString());
                    }
                    return new LeverPuzzleAction(levers);
                },
                coreLang.getString("editor.actions_name.lever_puzzle"),
                Material.LEVER,
                coreLang.getString("editor.actions.lever_puzzle"),
                puzzleDefaults,
                puzzlePrompts
        );

        // 5. SAVE CHECKPOINT ACTION
        Map<String, Object> checkpointDefaults = new HashMap<>();
        checkpointDefaults.put("location", plugin.getFileManager().getConfig().getString("action-defaults.checkpoint.location", "0,64,0"));
        checkpointDefaults.put("sound", plugin.getFileManager().getConfig().getString("action-defaults.checkpoint.sound", "entity.player.levelup"));
        checkpointDefaults.put("particle", plugin.getFileManager().getConfig().getString("action-defaults.checkpoint.particle", "TOTEM_OF_UNDYING"));

        Map<String, List<String>> checkpointPrompts = new HashMap<>();
        checkpointPrompts.put("location", coreLang.getStringList("editor.input.prompts.edit_action_loc_single"));
        checkpointPrompts.put("sound", coreLang.getStringList("editor.input.prompts.edit_action_sound"));
        checkpointPrompts.put("particle", coreLang.getStringList("editor.input.prompts.edit_action_particle"));

        api.registerCustomAction(
                "SAVE_CHECKPOINT",
                map -> new CheckpointAction(
                        String.valueOf(map.getOrDefault("location", checkpointDefaults.get("location"))),
                        String.valueOf(map.getOrDefault("sound", checkpointDefaults.get("sound"))),
                        String.valueOf(map.getOrDefault("particle", checkpointDefaults.get("particle")))
                ),
                coreLang.getString("editor.actions_name.save_checkpoint"),
                Material.BEACON,
                coreLang.getString("editor.actions.save_checkpoint"),
                checkpointDefaults,
                checkpointPrompts
        );

        // 6. DAMAGE ZONE ACTION
        Map<String, Object> damageZoneDefaults = new HashMap<>();
        damageZoneDefaults.put("center", plugin.getFileManager().getConfig().getString("action-defaults.damage_zone.center", "0,64,0"));
        damageZoneDefaults.put("radius", plugin.getFileManager().getConfig().getDouble("action-defaults.damage_zone.radius", 5.0));
        damageZoneDefaults.put("damage", plugin.getFileManager().getConfig().getDouble("action-defaults.damage_zone.damage", 4.0));
        damageZoneDefaults.put("interval", plugin.getFileManager().getConfig().getInt("action-defaults.damage_zone.interval", 20));
        damageZoneDefaults.put("duration", plugin.getFileManager().getConfig().getInt("action-defaults.damage_zone.duration", 200));
        damageZoneDefaults.put("particle", plugin.getFileManager().getConfig().getString("action-defaults.damage_zone.particle", "CAMPFIRE_COSY_SMOKE"));

        Map<String, List<String>> damageZonePrompts = new HashMap<>();
        damageZonePrompts.put("center", coreLang.getStringList("editor.input.prompts.edit_action_loc_single"));
        damageZonePrompts.put("radius", coreLang.getStringList("editor.input.prompts.edit_action_radius"));
        damageZonePrompts.put("damage", coreLang.getStringList("editor.input.prompts.edit_action_damage"));
        damageZonePrompts.put("interval", coreLang.getStringList("editor.input.prompts.edit_action_damage_interval"));
        damageZonePrompts.put("duration", coreLang.getStringList("editor.input.prompts.edit_action_duration"));
        damageZonePrompts.put("particle", coreLang.getStringList("editor.input.prompts.edit_action_particle"));

        api.registerCustomAction(
                "DAMAGE_ZONE",
                map -> new DamageZoneAction(
                        String.valueOf(map.getOrDefault("center", damageZoneDefaults.get("center"))),
                        parseSafeDouble(map.get("radius"), (double) damageZoneDefaults.get("radius")),
                        parseSafeDouble(map.get("damage"), (double) damageZoneDefaults.get("damage")),
                        parseSafeInt(map.get("interval"), (int) damageZoneDefaults.get("interval")),
                        parseSafeInt(map.get("duration"), (int) damageZoneDefaults.get("duration")),
                        String.valueOf(map.getOrDefault("particle", damageZoneDefaults.get("particle")))
                ),
                coreLang.getString("editor.actions_name.damage_zone"),
                Material.CAMPFIRE,
                coreLang.getString("editor.actions.damage_zone"),
                damageZoneDefaults,
                damageZonePrompts
        );
    }
}