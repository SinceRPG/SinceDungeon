package net.danh.sincedungeonpremium.registry;

import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.api.SinceDungeonAPI;
import net.danh.sinceDungeon.managers.LanguageManager;
import net.danh.sincedungeonpremium.SinceDungeonPremium;
import net.danh.sincedungeonpremium.actions.*;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Handles the registration of all Premium custom actions.
 * Extensively utilizes functional parsing to inject configuration defaults and dynamic GUI Prompts.
 * All texts and defaults are fetched dynamically from LanguageManager to prevent hardcoding.
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

    private static float parseSafeFloat(Object obj, float fallback) {
        if (obj == null) return fallback;
        if (obj instanceof Number number) return number.floatValue();
        try {
            return Float.parseFloat(obj.toString().trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public static void registerAll(SinceDungeonPremium plugin) {
        SinceDungeonAPI api = SinceDungeonAPI.get();
        LanguageManager lang = SinceDungeon.getPlugin().getLanguageManager();

        // 1. BUFF ACTION
        Map<String, Object> buffDefaults = new HashMap<>();
        buffDefaults.put("effect_type", "SPEED");
        buffDefaults.put("duration", 200);
        buffDefaults.put("amplifier", 1);

        Map<String, List<String>> buffPrompts = new HashMap<>();
        buffPrompts.put("effect_type", lang.getStringList("editor.input.prompts.edit_action_effect_type"));
        buffPrompts.put("duration", lang.getStringList("editor.input.prompts.edit_action_duration"));
        buffPrompts.put("amplifier", lang.getStringList("editor.input.prompts.edit_action_amplifier"));

        api.registerCustomAction(
                "BUFF",
                map -> new BuffAction(
                        String.valueOf(map.getOrDefault("effect_type", buffDefaults.get("effect_type"))),
                        parseSafeInt(map.get("duration"), (int) buffDefaults.get("duration")),
                        parseSafeInt(map.get("amplifier"), (int) buffDefaults.get("amplifier"))
                ),
                lang.getString("editor.actions_name.apply_buff", "&b&lPremium: Apply Buff"),
                Material.POTION,
                lang.getString("editor.actions.apply_buff", "Applies a potion effect to all participants."),
                buffDefaults,
                buffPrompts
        );

        // 2. ESCORT NPC ACTION
        Map<String, Object> escortDefaults = new HashMap<>();
        escortDefaults.put("entity_type", "VILLAGER");
        escortDefaults.put("custom_name", lang.getString("editor.defaults.escort.vip_name", "&aVIP Escort"));
        escortDefaults.put("max_health", 100.0);
        escortDefaults.put("start_location", "0,64,0");
        escortDefaults.put("target_location", "20,64,20");
        escortDefaults.put("speed", 1.0);
        escortDefaults.put("success_radius", 4.0);
        escortDefaults.put("vip_is_baby", false);
        escortDefaults.put("vip_attributes", new ArrayList<>(List.of("follow_range:128.0"))); // Injected natively
        escortDefaults.put("vip_equipment", new ArrayList<>());
        escortDefaults.put("attacker_mob", "ZOMBIE");
        escortDefaults.put("attacker_amount", 3);
        escortDefaults.put("attacker_interval", 100);
        escortDefaults.put("attacker_name", lang.getString("editor.defaults.escort.attacker_name", "&cAssassin"));
        escortDefaults.put("attacker_is_baby", false);
        escortDefaults.put("attacker_attributes", new ArrayList<>());
        escortDefaults.put("attacker_equipment", new ArrayList<>());

        api.registerCustomAction(
                "ESCORT_NPC",
                map -> new EscortAction(
                        String.valueOf(map.getOrDefault("entity_type", escortDefaults.get("entity_type"))),
                        String.valueOf(map.getOrDefault("custom_name", escortDefaults.get("custom_name"))),
                        parseSafeDouble(map.get("max_health"), (double) escortDefaults.get("max_health")),
                        String.valueOf(map.getOrDefault("start_location", escortDefaults.get("start_location"))),
                        String.valueOf(map.getOrDefault("target_location", escortDefaults.get("target_location"))),
                        parseSafeDouble(map.get("speed"), (double) escortDefaults.get("speed")),
                        parseSafeDouble(map.get("success_radius"), (double) escortDefaults.get("success_radius")),
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
                lang.getString("editor.actions_name.escort_npc", "&e&lPremium: Escort NPC"),
                Material.EMERALD,
                lang.getString("editor.actions.escort_npc", "Protect an NPC as they travel to a destination."),
                escortDefaults,
                new HashMap<>()
        );

        // 3. BRANCHING PATH ACTION
        Map<String, Object> branchDefaults = new HashMap<>();
        branchDefaults.put("path_a_loc", "0,64,0");
        branchDefaults.put("path_b_loc", "10,64,10");
        branchDefaults.put("stage_a", 3);
        branchDefaults.put("stage_b", 4);
        branchDefaults.put("radius", 3.0);

        api.registerCustomAction(
                "BRANCHING_PATH",
                map -> new BranchingPathAction(
                        String.valueOf(map.getOrDefault("path_a_loc", branchDefaults.get("path_a_loc"))),
                        String.valueOf(map.getOrDefault("path_b_loc", branchDefaults.get("path_b_loc"))),
                        parseSafeInt(map.get("stage_a"), (int) branchDefaults.get("stage_a")),
                        parseSafeInt(map.get("stage_b"), (int) branchDefaults.get("stage_b")),
                        parseSafeDouble(map.get("radius"), (double) branchDefaults.get("radius"))
                ),
                lang.getString("editor.actions_name.branching_path", "&a&lPremium: Branching Path"),
                Material.OAK_SIGN,
                lang.getString("editor.actions.branching_path", "Diverges the dungeon into two separate stage paths."),
                branchDefaults,
                new HashMap<>()
        );

        // 4. LEVER PUZZLE ACTION
        Map<String, Object> puzzleDefaults = new HashMap<>();
        puzzleDefaults.put("levers", new ArrayList<>(List.of("0,64,0", "2,64,0", "4,64,0")));
        puzzleDefaults.put("fail_time_penalty", 5);

        api.registerCustomAction(
                "LEVER_PUZZLE",
                map -> new LeverPuzzleAction(
                        parseList(map.getOrDefault("levers", puzzleDefaults.get("levers"))),
                        parseSafeInt(map.get("fail_time_penalty"), (int) puzzleDefaults.get("fail_time_penalty"))
                ),
                lang.getString("editor.actions_name.lever_puzzle", "&6&lPremium: Lever Puzzle"),
                Material.LEVER,
                lang.getString("editor.actions.lever_puzzle", "Requires players to hit levers in a specific order."),
                puzzleDefaults,
                new HashMap<>()
        );

        // 5. CHECKPOINT ACTION
        Map<String, Object> cpDefaults = new HashMap<>();
        cpDefaults.put("location", "0,64,0");
        cpDefaults.put("sound", "entity.player.levelup");
        cpDefaults.put("particle", "TOTEM_OF_UNDYING");

        api.registerCustomAction(
                "CHECKPOINT",
                map -> new CheckpointAction(
                        String.valueOf(map.getOrDefault("location", cpDefaults.get("location"))),
                        String.valueOf(map.getOrDefault("sound", cpDefaults.get("sound"))),
                        String.valueOf(map.getOrDefault("particle", cpDefaults.get("particle")))
                ),
                lang.getString("editor.actions_name.save_checkpoint", "&b&lPremium: Save Checkpoint"),
                Material.RED_BED,
                lang.getString("editor.actions.save_checkpoint", "Updates the dungeon respawn point."),
                cpDefaults,
                new HashMap<>()
        );

        // 6. DAMAGE ZONE ACTION
        Map<String, Object> dmgDefaults = new HashMap<>();
        dmgDefaults.put("location", "0,64,0");
        dmgDefaults.put("radius", 5.0);
        dmgDefaults.put("damage", 4.0);
        dmgDefaults.put("interval", 20);
        dmgDefaults.put("duration", 200);
        dmgDefaults.put("particle", "CAMPFIRE_COSY_SMOKE");

        api.registerCustomAction(
                "DAMAGE_ZONE",
                map -> new DamageZoneAction(
                        String.valueOf(map.getOrDefault("location", dmgDefaults.get("location"))),
                        parseSafeDouble(map.get("radius"), (double) dmgDefaults.get("radius")),
                        parseSafeDouble(map.get("damage"), (double) dmgDefaults.get("damage")),
                        parseSafeInt(map.get("interval"), (int) dmgDefaults.get("interval")),
                        parseSafeInt(map.get("duration"), (int) dmgDefaults.get("duration")),
                        String.valueOf(map.getOrDefault("particle", dmgDefaults.get("particle")))
                ),
                lang.getString("editor.actions_name.damage_zone", "&4&lPremium: Damage Zone"),
                Material.MAGMA_BLOCK,
                lang.getString("editor.actions.damage_zone", "Creates an AoE hazard that damages players."),
                dmgDefaults,
                new HashMap<>()
        );

        // 7. JUMP STAGE ACTION
        Map<String, Object> jumpDefaults = new HashMap<>();
        jumpDefaults.put("target_stage", 5);

        api.registerCustomAction(
                "JUMP_STAGE",
                map -> new JumpStageAction(parseSafeInt(map.get("target_stage"), (int) jumpDefaults.get("target_stage"))),
                lang.getString("editor.actions_name.jump_stage", "&c&lPremium: Jump Stage"),
                Material.RABBIT_FOOT,
                lang.getString("editor.actions.jump_stage", "Forcibly skips execution to a different stage."),
                jumpDefaults,
                new HashMap<>()
        );

        // 8. CINEMATIC DIALOGUE ACTION
        Map<String, Object> cineDefaults = new HashMap<>();
        cineDefaults.put("frames", new ArrayList<>(List.of("40;&e&lThe King;&fAh, heroes.;&e[King] Ah, heroes.;entity.villager.trade")));

        api.registerCustomAction(
                "CINEMATIC_DIALOGUE",
                map -> new CinematicDialogueAction(parseList(map.getOrDefault("frames", cineDefaults.get("frames")))),
                lang.getString("editor.actions_name.cinematic_dialogue", "&d&lPremium: Cinematic"),
                Material.WRITABLE_BOOK,
                lang.getString("editor.actions.cinematic_dialogue", "Plays a sequence of titles, text, and sounds."),
                cineDefaults,
                new HashMap<>()
        );

        // 9. PROJECTILE TRAP ACTION
        Map<String, Object> trapDefaults = new HashMap<>();
        trapDefaults.put("location", "0,64,0");
        trapDefaults.put("direction", "0,-1,0");
        trapDefaults.put("projectile_type", "ARROW");
        trapDefaults.put("interval", 20);
        trapDefaults.put("speed", 1.5);
        trapDefaults.put("duration", 200);

        api.registerCustomAction(
                "PROJECTILE_TRAP",
                map -> new ProjectileTrapAction(
                        String.valueOf(map.getOrDefault("location", trapDefaults.get("location"))),
                        String.valueOf(map.getOrDefault("direction", trapDefaults.get("direction"))),
                        String.valueOf(map.getOrDefault("projectile_type", trapDefaults.get("projectile_type"))),
                        parseSafeInt(map.get("interval"), (int) trapDefaults.get("interval")),
                        parseSafeDouble(map.get("speed"), (double) trapDefaults.get("speed")),
                        parseSafeInt(map.get("duration"), (int) trapDefaults.get("duration"))
                ),
                lang.getString("editor.actions_name.projectile_trap", "&8&lPremium: Projectile Trap"),
                Material.DISPENSER,
                lang.getString("editor.actions.projectile_trap", "Continuously fires projectiles in a direction."),
                trapDefaults,
                new HashMap<>()
        );

        // 10. DEFEND CORE ACTION
        Map<String, Object> defendDefaults = new HashMap<>();
        defendDefaults.put("location", "0,64,0");
        defendDefaults.put("core_type", "IRON_GOLEM");
        defendDefaults.put("core_name", lang.getString("editor.defaults.defend_core.core_name", "&b&lSacred Crystal"));
        defendDefaults.put("core_health", 1000.0);
        defendDefaults.put("duration", 600); // 30 seconds
        defendDefaults.put("attacker_mob", "ZOMBIE");
        defendDefaults.put("attacker_amount", 5);
        defendDefaults.put("attacker_interval", 100);
        defendDefaults.put("attacker_name", lang.getString("editor.defaults.defend_core.attacker_name", "&cInvader"));
        defendDefaults.put("attacker_is_baby", false);
        defendDefaults.put("attacker_attributes", new ArrayList<>());
        defendDefaults.put("attacker_equipment", new ArrayList<>());

        Map<String, List<String>> defendPrompts = new HashMap<>();
        defendPrompts.put("location", lang.getStringList("editor.input.prompts.edit_action_loc_single"));
        defendPrompts.put("core_type", lang.getStringList("editor.input.prompts.edit_action_core_type"));
        defendPrompts.put("core_name", lang.getStringList("editor.input.prompts.edit_action_core_name"));
        defendPrompts.put("core_health", lang.getStringList("editor.input.prompts.edit_action_core_health"));
        defendPrompts.put("duration", lang.getStringList("editor.input.prompts.edit_action_duration"));

        api.registerCustomAction(
                "DEFEND_CORE",
                map -> new DefendCoreAction(
                        String.valueOf(map.getOrDefault("location", defendDefaults.get("location"))),
                        String.valueOf(map.getOrDefault("core_type", defendDefaults.get("core_type"))),
                        String.valueOf(map.getOrDefault("core_name", defendDefaults.get("core_name"))),
                        parseSafeDouble(map.get("core_health"), (double) defendDefaults.get("core_health")),
                        parseSafeInt(map.get("duration"), (int) defendDefaults.get("duration")),
                        String.valueOf(map.getOrDefault("attacker_mob", defendDefaults.get("attacker_mob"))),
                        parseSafeInt(map.get("attacker_amount"), (int) defendDefaults.get("attacker_amount")),
                        parseSafeInt(map.get("attacker_interval"), (int) defendDefaults.get("attacker_interval")),
                        String.valueOf(map.getOrDefault("attacker_name", defendDefaults.get("attacker_name"))),
                        Boolean.parseBoolean(String.valueOf(map.getOrDefault("attacker_is_baby", defendDefaults.get("attacker_is_baby")))),
                        parseList(map.getOrDefault("attacker_attributes", defendDefaults.get("attacker_attributes"))),
                        parseList(map.getOrDefault("attacker_equipment", defendDefaults.get("attacker_equipment")))
                ),
                lang.getString("editor.actions_name.defend_core", "&3&lPremium: Defend Core"),
                Material.END_CRYSTAL,
                lang.getString("editor.actions.defend_core", "Protect an entity from waves of enemies."),
                defendDefaults,
                defendPrompts
        );

        // 11. GIVE ITEM ACTION
        Map<String, Object> giveDefaults = new HashMap<>();
        giveDefaults.put("item_data", "DIAMOND:1");
        giveDefaults.put("receive_message", lang.getString("editor.defaults.give_item.message", "&aYou received a mysterious item..."));

        Map<String, List<String>> givePrompts = new HashMap<>();
        givePrompts.put("item_data", lang.getStringList("editor.input.prompts.edit_action_item_data"));
        givePrompts.put("receive_message", lang.getStringList("editor.input.prompts.edit_action_receive_message"));

        api.registerCustomAction(
                "GIVE_ITEM",
                map -> new GiveItemAction(
                        String.valueOf(map.getOrDefault("item_data", giveDefaults.get("item_data"))),
                        String.valueOf(map.getOrDefault("receive_message", giveDefaults.get("receive_message")))
                ),
                lang.getString("editor.actions_name.give_item", "&6&lPremium: Give Item"),
                Material.BUNDLE,
                lang.getString("editor.actions.give_item", "Gives a specific item directly to the party."),
                giveDefaults,
                givePrompts
        );

        // 12. PLAY SOUND ACTION
        Map<String, Object> soundDefaults = new HashMap<>();
        soundDefaults.put("sound_name", "entity.ender_dragon.growl");
        soundDefaults.put("volume", 1.0f);
        soundDefaults.put("pitch", 1.0f);

        Map<String, List<String>> soundPrompts = new HashMap<>();
        soundPrompts.put("sound_name", lang.getStringList("editor.input.prompts.edit_action_sound_name"));
        soundPrompts.put("volume", lang.getStringList("editor.input.prompts.edit_action_volume"));
        soundPrompts.put("pitch", lang.getStringList("editor.input.prompts.edit_action_pitch"));

        api.registerCustomAction(
                "PLAY_SOUND",
                map -> new PlaySoundAction(
                        String.valueOf(map.getOrDefault("sound_name", soundDefaults.get("sound_name"))),
                        parseSafeFloat(map.get("volume"), (float) soundDefaults.get("volume")),
                        parseSafeFloat(map.get("pitch"), (float) soundDefaults.get("pitch"))
                ),
                lang.getString("editor.actions_name.play_sound", "&d&lPremium: Play Sound"),
                Material.JUKEBOX,
                lang.getString("editor.actions.play_sound", "Plays a global sound effect for the party."),
                soundDefaults,
                soundPrompts
        );
    }
}