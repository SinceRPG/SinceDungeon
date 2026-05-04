package net.danh.sincedungeonpremium.utils;

import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.managers.LanguageManager;
import net.danh.sinceDungeon.utils.ConfigUtils;

import java.util.Arrays;

/**
 * Injects Premium-exclusive translation keys directly into the Core Plugin's language files.
 * This allows server owners to translate Premium Actions and HUD elements in the same place
 * they translate the Core plugin, completely avoiding missing prompt errors.
 */
public class PremiumLanguageInjector {

    public static void inject(SinceDungeon core) {
        LanguageManager lang = core.getLanguageManager();

        // 1. Inject into editor.yml
        ConfigUtils editorCfg = lang.getConfigUtilsForPath("editor.test");
        if (editorCfg != null) {
            boolean editorChanged = false;

            // --- Checkpoint Action ---
            if (!editorCfg.getConfig().contains("editor.actions_name.save_checkpoint")) {
                editorCfg.set("editor.actions_name.save_checkpoint", "&b&lPremium: Save Checkpoint");
                editorCfg.set("editor.actions.save_checkpoint", "Saves a new respawn point for players in the dungeon.");
                editorCfg.set("editor.input.prompts.edit_action_loc_single", Arrays.asList("&7Set the respawn coordinates (X,Y,Z).", "&7Tip: Type &ahere &7to use your current location."));
                editorCfg.set("editor.input.prompts.edit_action_sound", Arrays.asList("&7Enter the sound to play on activation."));
                editorCfg.set("editor.input.prompts.edit_action_particle", Arrays.asList("&7Enter the particle to spawn on activation."));
                editorChanged = true;
            }

            // --- Damage Zone Action ---
            if (!editorCfg.getConfig().contains("editor.actions_name.damage_zone")) {
                editorCfg.set("editor.actions_name.damage_zone", "&c&lPremium: Damage Hazard Zone");
                editorCfg.set("editor.actions.damage_zone", "Creates a temporary hazardous zone dealing damage to players inside.");
                editorCfg.set("editor.input.prompts.edit_action_radius", Arrays.asList("&7Enter the radius of the hazard.", "&7Example: &a5.0"));
                editorCfg.set("editor.input.prompts.edit_action_damage", Arrays.asList("&7Enter the amount of damage dealt per interval.", "&7Example: &a4.0 &7(2 hearts)"));
                editorCfg.set("editor.input.prompts.edit_action_damage_interval", Arrays.asList("&7Enter the damage interval in ticks (20 ticks = 1s).", "&7Example: &a20"));
                editorChanged = true;
            }

            // --- Apply Buff Action ---
            if (!editorCfg.getConfig().contains("editor.actions_name.apply_buff")) {
                editorCfg.set("editor.actions_name.apply_buff", "&d&lPremium: Apply Buff");
                editorCfg.set("editor.actions.apply_buff", "Instantly applies a potion effect to all party members.");
                editorCfg.set("editor.input.prompts.edit_action_effect", Arrays.asList("&7Enter the PotionEffectType to apply.", "&7Example: &aSPEED &7or &aSTRENGTH"));
                editorCfg.set("editor.input.prompts.edit_action_duration", Arrays.asList("&7Enter the duration in ticks (20 ticks = 1s).", "&7Example: &a200"));
                editorCfg.set("editor.input.prompts.edit_action_amplifier", Arrays.asList("&7Enter the potion level (0 = Level 1).", "&7Example: &a1 &7(Applies Level 2 effect)"));
                editorChanged = true;
            }

            // --- Escort Action ---
            if (!editorCfg.getConfig().contains("editor.actions_name.escort_npc")) {
                editorCfg.set("editor.actions_name.escort_npc", "&6&lPremium: Escort NPC");
                editorCfg.set("editor.actions.escort_npc", "Spawns an NPC that walks to a target. Fails if it dies.");
                editorCfg.set("editor.input.prompts.edit_action_start_location", Arrays.asList("&7Set the spawn coordinates (X,Y,Z) for the NPC.", "&7Tip: Type &ahere &7to use your current location."));
                editorCfg.set("editor.input.prompts.edit_action_target_location", Arrays.asList("&7Set the destination coordinates (X,Y,Z) for the NPC.", "&7Tip: Type &ahere &7to use your current location."));
                editorCfg.set("editor.input.prompts.edit_action_mob", Arrays.asList("&7Enter the EntityType for the VIP NPC.", "&7Example: &aVILLAGER"));
                editorCfg.set("editor.input.prompts.edit_action_vip_is_baby", Arrays.asList("&7Is the VIP a baby? Enter &atrue &7or &cfalse&7."));
                editorCfg.set("editor.input.prompts.edit_action_vip_attributes", Arrays.asList("&7Set custom attributes for the VIP.", "&7Format: &a<attribute>:<value>"));
                editorCfg.set("editor.input.prompts.edit_action_vip_equipment", Arrays.asList("&7Set the equipment for the VIP.", "&7Format: &a<slot>:<item_data>"));
                editorCfg.set("editor.input.prompts.edit_action_attacker_mob", Arrays.asList("&7Enter the EntityType for the Attacker (or NONE to disable).", "&7Example: &aZOMBIE"));
                editorCfg.set("editor.input.prompts.edit_action_attacker_interval", Arrays.asList("&7Enter the delay in ticks between attacker spawns (20 ticks = 1s).", "&7Example: &a100"));
                editorCfg.set("editor.input.prompts.edit_action_attacker_name", Arrays.asList("&7Enter a custom name for the attacker (Color codes supported)."));
                editorCfg.set("editor.input.prompts.edit_action_attacker_is_baby", Arrays.asList("&7Is the attacker a baby? Enter &atrue &7or &cfalse&7."));
                editorCfg.set("editor.input.prompts.edit_action_attacker_attributes", Arrays.asList("&7Set custom attributes for the attackers.", "&7Format: &a<attribute>:<value>"));
                editorCfg.set("editor.input.prompts.edit_action_attacker_equipment", Arrays.asList("&7Set the equipment for the attackers.", "&7Format: &a<slot>:<item_data>"));
                editorChanged = true;
            }

            // --- Branching Path Action ---
            if (!editorCfg.getConfig().contains("editor.actions_name.branching_path")) {
                editorCfg.set("editor.actions_name.branching_path", "&e&lPremium: Branching Path");
                editorCfg.set("editor.actions.branching_path", "Allows players to choose between two different stages.");
                editorCfg.set("editor.input.prompts.edit_action_path_a_loc", Arrays.asList("&7Set the coordinates for Path A's decision zone."));
                editorCfg.set("editor.input.prompts.edit_action_path_b_loc", Arrays.asList("&7Set the coordinates for Path B's decision zone."));
                editorCfg.set("editor.input.prompts.edit_action_stage_a", Arrays.asList("&7Enter the Stage Number to jump to if players choose Path A."));
                editorCfg.set("editor.input.prompts.edit_action_stage_b", Arrays.asList("&7Enter the Stage Number to jump to if players choose Path B."));
                editorChanged = true;
            }

            // --- Jump Stage Action ---
            if (!editorCfg.getConfig().contains("editor.actions_name.jump_stage")) {
                editorCfg.set("editor.actions_name.jump_stage", "&d&lPremium: Jump Stage");
                editorCfg.set("editor.actions.jump_stage", "Instantly skips to a specific stage index.");
                editorCfg.set("editor.input.prompts.edit_action_target_stage", Arrays.asList("&7Enter the Stage Number to jump to."));
                editorChanged = true;
            }

            // --- Lever Puzzle Action ---
            // Inside Editor Config Injection:
            if (!editorCfg.getConfig().contains("editor.input.prompts.edit_action_fail_time_penalty")) {
                editorCfg.set("editor.input.prompts.edit_action_fail_time_penalty", Arrays.asList(
                        "&7Enter the amount of time (in seconds) to deduct when players pull the wrong lever.",
                        "&7Example: &a5 &7(Subtracts 5 seconds from the timer)"
                ));
                editorChanged = true;
            }
            if (!editorCfg.getConfig().contains("editor.actions_name.lever_puzzle")) {
                editorCfg.set("editor.actions_name.lever_puzzle", "&b&lPremium: Lever Puzzle");
                editorCfg.set("editor.actions.lever_puzzle", "Requires players to activate levers in a specific sequence.");
                editorCfg.set("editor.input.prompts.edit_action_levers", Arrays.asList(
                        "&7Add lever coordinates (X,Y,Z)",
                        "&7Format: &aX,Y,Z",
                        "&7The order you add them is the solution sequence!",
                        "&7Tip: Type &ahere &7to use your current block."
                ));
                editorChanged = true;
            }

            if (editorChanged) editorCfg.save();
        }

        // 2. Inject into game.yml
        ConfigUtils gameCfg = lang.getConfigUtilsForPath("objective.test");
        if (gameCfg != null) {
            boolean gameChanged = false;

            if (!gameCfg.getConfig().contains("objective.save_checkpoint")) {
                gameCfg.set("objective.save_checkpoint", "&aCheckpoint Reached!");
                gameChanged = true;
            }
            if (!gameCfg.getConfig().contains("objective.damage_zone")) {
                gameCfg.set("objective.damage_zone", "&cSurvive the Hazard!");
                gameChanged = true;
            }
            if (!gameCfg.getConfig().contains("objective.apply_buff")) {
                gameCfg.set("objective.apply_buff", "&aA mysterious power grants you strength!");
                gameChanged = true;
            }
            if (!gameCfg.getConfig().contains("objective.escort_npc")) {
                gameCfg.set("objective.escort_npc", "&eProtect the VIP and escort them to safety!");
                gameCfg.set("action.escort_failed", "&cThe VIP has been killed! The mission has failed.");
                gameChanged = true;
            }
            if (!gameCfg.getConfig().contains("objective.branching_path")) {
                gameCfg.set("objective.branching_path", "&aChoose your destiny!");
                gameCfg.set("action.branch_path_chosen", "&aYou have chosen to advance to stage <stage>!");
                gameChanged = true;
            }
            if (!gameCfg.getConfig().contains("objective.jump_stage")) {
                gameCfg.set("objective.jump_stage", "&dJumping to new stage...");
                gameChanged = true;
            }
            if (!gameCfg.getConfig().contains("objective.lever_puzzle")) {
                gameCfg.set("objective.lever_puzzle", "&ePull the levers in the correct order!");
                gameCfg.set("action.puzzle_failed", "&cWrong sequence! The puzzle has been reset.");
                gameCfg.set("action.puzzle_solved", "&aCorrect sequence! The mechanism unlocks.");
                gameChanged = true;
            }
            if (!gameCfg.getConfig().contains("action.puzzle_failed_penalty")) {
                gameCfg.set("action.puzzle_failed_penalty", "&cWrong lever sequence! The puzzle has reset and you lost <time> seconds.");
                gameChanged = true;
            }
            if (!gameCfg.getConfig().contains("action.affix_volcanic_hit")) {
                gameCfg.set("action.affix_volcanic_hit", "&cYou were burned by a Volcanic explosion!");
                gameChanged = true;
            }

            if (gameChanged) gameCfg.save();
        }
    }
}