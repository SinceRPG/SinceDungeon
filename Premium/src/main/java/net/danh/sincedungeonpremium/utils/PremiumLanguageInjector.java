package net.danh.sincedungeonpremium.utils;

import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.managers.LanguageManager;
import net.danh.sinceDungeon.utils.ConfigUtils;

import java.util.Arrays;

/**
 * Injects Premium-exclusive translation keys directly into the Core Plugin's language files.
 * Provides exceptionally detailed Editor Prompts for the best UI/UX experience and ensures no hardcoding.
 */
public class PremiumLanguageInjector {

    public static void inject(SinceDungeon core) {
        LanguageManager lang = core.getLanguageManager();

        ConfigUtils editorCfg = lang.getConfigUtilsForPath("editor.test");
        if (editorCfg != null) {
            boolean editorChanged = false;

            if (!editorCfg.getConfig().contains("editor.items.setting_req_item")) {
                editorCfg.set("editor.items.setting_req_item", "&eRequired Entry Item");
                editorCfg.set("editor.items.setting_req_item_lore", Arrays.asList("&7Current: &f<val>", "&eLeft Click to edit"));
                editorCfg.set("editor.items.setting_consume_req_item", "&eConsume Required Item");
                editorCfg.set("editor.items.setting_consume_req_item_lore", Arrays.asList("&7Current: <val>", "&eLeft Click to toggle"));
                editorChanged = true;
            }

            if (!editorCfg.getConfig().contains("editor.input.prompts.edit_req_item")) {
                editorCfg.set("editor.input.prompts.edit_req_item", Arrays.asList(
                        "&7Enter the item format required to enter.",
                        "&7Vanilla: &aDIAMOND:5",
                        "&7MMOItems: &aMMOITEMS:SWORD:FIERY:1",
                        "&7Dungeon Key: &aKEY:door_1:1",
                        "&7Tip: Type &cNONE &7to disable this requirement."
                ));
                editorChanged = true;
            }

            // Core Fallback Fields Update
            if (!editorCfg.getConfig().contains("editor.input.prompts.edit_action_radius")) {
                editorCfg.set("editor.input.prompts.edit_action_radius", Arrays.asList(
                        "&7Enter the radius for the ring/area.",
                        "&7Example: &a3.0"
                ));
                editorChanged = true;
            }

            if (!editorCfg.getConfig().contains("editor.input.prompts.edit_action_location")) {
                editorCfg.set("editor.input.prompts.edit_action_location", Arrays.asList(
                        "&7Enter coordinates format: &fX,Y,Z",
                        "&7Tip: Type &ahere &7to use your current location."
                ));
                editorChanged = true;
            }

            // --- Premium Fields & Action Injections ---

            if (!editorCfg.getConfig().contains("editor.actions_name.defend_core")) {
                // Defend Core
                editorCfg.set("editor.actions_name.defend_core", "&3&lPremium: Defend Core");
                editorCfg.set("editor.actions.defend_core", "Protect an entity from waves of enemies.");
                editorCfg.set("editor.input.prompts.edit_action_core_type", Arrays.asList("&7Enter the EntityType of the Core to defend.", "&7Example: &aIRON_GOLEM &7or &aENDER_CRYSTAL"));
                editorCfg.set("editor.input.prompts.edit_action_core_name", Arrays.asList("&7Enter the Hologram name of the Core.", "&7Example: &b&lSacred Crystal", "&7Tip: Type &cclear &7to remove name."));
                editorCfg.set("editor.input.prompts.edit_action_core_health", Arrays.asList("&7Enter the total health of the Core.", "&7Example: &a1000.0"));

                // Jump Stage
                editorCfg.set("editor.actions_name.jump_stage", "&c&lPremium: Jump Stage");
                editorCfg.set("editor.actions.jump_stage", "Forcibly skips execution to a different stage.");
                editorCfg.set("editor.input.prompts.edit_action_target_stage", Arrays.asList("&7Enter the stage index to jump to."));

                // Damage Zone
                editorCfg.set("editor.actions_name.damage_zone", "&4&lPremium: Damage Zone");
                editorCfg.set("editor.actions.damage_zone", "Creates an AoE hazard that damages players.");
                editorCfg.set("editor.input.prompts.edit_action_damage", Arrays.asList("&7Enter the damage dealt per tick interval."));
                editorCfg.set("editor.input.prompts.edit_action_interval", Arrays.asList("&7Enter the tick interval (20 ticks = 1s).", "&7Example: &a20"));

                // Checkpoint
                editorCfg.set("editor.actions_name.save_checkpoint", "&b&lPremium: Save Checkpoint");
                editorCfg.set("editor.actions.save_checkpoint", "Updates the dungeon respawn point when touched.");
                editorCfg.set("editor.input.prompts.edit_action_particle", Arrays.asList("&7Enter the Particle type.", "&7Example: &aFLAME"));

                // Lever Puzzle
                editorCfg.set("editor.actions_name.lever_puzzle", "&6&lPremium: Lever Puzzle");
                editorCfg.set("editor.actions.lever_puzzle", "Requires players to hit levers in a specific order.");
                editorCfg.set("editor.input.prompts.edit_action_fail_time_penalty", Arrays.asList("&7Enter the time penalty (seconds) on failure."));

                // Branching Path
                editorCfg.set("editor.actions_name.branching_path", "&a&lPremium: Branching Path");
                editorCfg.set("editor.actions.branching_path", "Diverges the dungeon into two separate stage paths.");
                editorCfg.set("editor.input.prompts.edit_action_path_a_loc", Arrays.asList("&7Enter the coordinates for Path A."));
                editorCfg.set("editor.input.prompts.edit_action_path_b_loc", Arrays.asList("&7Enter the coordinates for Path B."));
                editorCfg.set("editor.input.prompts.edit_action_stage_a", Arrays.asList("&7Enter the Stage Index for Path A."));
                editorCfg.set("editor.input.prompts.edit_action_stage_b", Arrays.asList("&7Enter the Stage Index for Path B."));

                // Projectile Trap
                editorCfg.set("editor.actions_name.projectile_trap", "&8&lPremium: Projectile Trap");
                editorCfg.set("editor.actions.projectile_trap", "Continuously fires projectiles in a direction.");
                editorCfg.set("editor.input.prompts.edit_action_direction", Arrays.asList("&7Enter the Vector direction X,Y,Z.", "&7Example: &a0,-1,0"));
                editorCfg.set("editor.input.prompts.edit_action_projectile_type", Arrays.asList("&7Enter the Projectile EntityType.", "&7Example: &aARROW"));

                // Cinematic Dialogue
                editorCfg.set("editor.actions_name.cinematic_dialogue", "&d&lPremium: Cinematic");
                editorCfg.set("editor.actions.cinematic_dialogue", "Plays a sequence of titles, text, and sounds.");
                editorCfg.set("editor.input.prompts.edit_action_frames", Arrays.asList("&7Enter Cinematic Frame data.", "&7Format: &adelay;title;subtitle;chat;sound", "&7Tip: Type &cclear &7to empty."));

                // Buff Action
                editorCfg.set("editor.actions_name.apply_buff", "&b&lPremium: Apply Buff");
                editorCfg.set("editor.actions.apply_buff", "Applies a potion effect to all participants.");
                editorCfg.set("editor.input.prompts.edit_action_effect_type", Arrays.asList("&7Enter the Potion Effect type.", "&7Example: &aSPEED"));
                editorCfg.set("editor.input.prompts.edit_action_duration", Arrays.asList("&7Enter duration in ticks (20 = 1s).", "&7Example: &a200"));
                editorCfg.set("editor.input.prompts.edit_action_amplifier", Arrays.asList("&7Enter effect amplifier (0 = level 1).", "&7Example: &a1"));

                // Escort NPC
                editorCfg.set("editor.actions_name.escort_npc", "&e&lPremium: Escort NPC");
                editorCfg.set("editor.actions.escort_npc", "Protect an NPC as they travel to a destination.");
                editorCfg.set("editor.input.prompts.edit_action_entity_type", Arrays.asList("&7Enter the EntityType for the VIP.", "&7Example: &aVILLAGER"));
                editorCfg.set("editor.input.prompts.edit_action_start_location", Arrays.asList("&7Enter the starting coordinates X,Y,Z.", "&7Tip: Type &ahere &7to use your location."));
                editorCfg.set("editor.input.prompts.edit_action_target_location", Arrays.asList("&7Enter the destination coordinates X,Y,Z."));
                editorCfg.set("editor.input.prompts.edit_action_speed", Arrays.asList("&7Enter the movement speed multiplier.", "&7Example: &a1.0"));
                editorCfg.set("editor.input.prompts.edit_action_success_radius", Arrays.asList("&7Enter the completion radius.", "&7Example: &a4.0"));
                editorCfg.set("editor.input.prompts.edit_action_vip_is_baby", Arrays.asList("&7Is the VIP a baby? &atrue &7or &cfalse"));
                editorCfg.set("editor.input.prompts.edit_action_attacker_mob", Arrays.asList("&7Enter the EntityType of the attackers.", "&7Example: &aZOMBIE"));
                editorCfg.set("editor.input.prompts.edit_action_attacker_amount", Arrays.asList("&7Enter the amount of attackers per wave.", "&7Example: &a3"));
                editorCfg.set("editor.input.prompts.edit_action_attacker_interval", Arrays.asList("&7Enter the interval (ticks) between waves.", "&7Example: &a100"));
                editorCfg.set("editor.input.prompts.edit_action_attacker_name", Arrays.asList("&7Enter the custom name for attackers.", "&7Tip: Type &cclear &7to reset."));
                editorCfg.set("editor.input.prompts.edit_action_attacker_is_baby", Arrays.asList("&7Are attackers babies? &atrue &7or &cfalse"));

                editorChanged = true;
            }

            if (!editorCfg.getConfig().contains("editor.actions_name.give_item")) {
                editorCfg.set("editor.actions_name.give_item", "&6&lPremium: Give Item");
                editorCfg.set("editor.actions.give_item", "Gives a specific item directly to the party.");
                editorCfg.set("editor.input.prompts.edit_action_item_data", Arrays.asList(
                        "&7Enter the Item format to give.",
                        "&7Vanilla: &aDIAMOND:5",
                        "&7MMOItems: &aMMOITEMS:SWORD:FIERY:1",
                        "&7Dungeon Key: &aKEY:door_1:1"
                ));
                editorCfg.set("editor.input.prompts.edit_action_receive_message", Arrays.asList("&7Enter the message to display when the item is received.", "&7Tip: Type &cclear &7to disable."));
                editorChanged = true;
            }

            if (!editorCfg.getConfig().contains("editor.actions_name.play_sound")) {
                editorCfg.set("editor.actions_name.play_sound", "&d&lPremium: Play Sound");
                editorCfg.set("editor.actions.play_sound", "Plays a global sound effect for the party.");
                editorCfg.set("editor.input.prompts.edit_action_sound_name", Arrays.asList("&7Enter the sound event name.", "&7Example: &aentity.ender_dragon.growl"));
                editorCfg.set("editor.input.prompts.edit_action_volume", Arrays.asList("&7Enter the sound volume (float).", "&7Example: &a1.0"));
                editorCfg.set("editor.input.prompts.edit_action_pitch", Arrays.asList("&7Enter the sound pitch (float).", "&7Example: &a1.0"));
                editorChanged = true;
            }

            if (!editorCfg.getConfig().contains("editor.defaults.escort.vip_name")) {
                editorCfg.set("editor.defaults.escort.vip_name", "&aVIP Escort");
                editorCfg.set("editor.defaults.escort.attacker_name", "&cAssassin");
                editorCfg.set("editor.defaults.defend_core.core_name", "&b&lSacred Crystal");
                editorCfg.set("editor.defaults.defend_core.attacker_name", "&cInvader");
                editorCfg.set("editor.defaults.give_item.message", "&aYou received a mysterious item...");
                editorChanged = true;
            }

            if (editorChanged) editorCfg.save();
        }

        ConfigUtils errorCfg = lang.getConfigUtilsForPath("error.test");
        if (errorCfg != null) {
            boolean errorChanged = false;
            if (!errorCfg.getConfig().contains("error.missing_required_item")) {
                errorCfg.set("error.missing_required_item", "&cYou lack the required item to enter: <item>");
                errorCfg.set("error.party_member_missing_item", "&cMember <player> lacks the required entry item.");
                errorCfg.set("error.target_not_in_dungeon", "&cThat player is not in your dungeon!");
                errorCfg.set("error.target_not_spectator", "&cThat player is not knocked out!");
                errorCfg.set("error.no_life_item", "&cYou need a Soul Crystal in your inventory to revive someone!");
                errorChanged = true;
            }
            if (errorChanged) errorCfg.save();
        }

        ConfigUtils gameCfg = lang.getConfigUtilsForPath("objective.test");
        if (gameCfg != null) {
            boolean gameChanged = false;

            if (!gameCfg.getConfig().contains("game.revived_target")) {
                gameCfg.set("game.revived_target", "&aYou have been revived by <player>!");
                gameCfg.set("game.revived_sender", "&aYou revived <player>!");
                gameCfg.set("game.revived_broadcast", "&e<sender> revived <target> using a Soul Crystal!");
                gameChanged = true;
            }

            if (!gameCfg.getConfig().contains("objective.save_checkpoint")) {
                gameCfg.set("objective.save_checkpoint", "&bReach the Checkpoint!");
                gameCfg.set("action.checkpoint_start", "&eA checkpoint ring has appeared. Stand inside it to secure your respawn point!");
                gameCfg.set("action.checkpoint_complete", "&aCheckpoint secured! You will respawn here if you die.");
                gameChanged = true;
            }

            if (!gameCfg.getConfig().contains("objective.defend_core")) {
                gameCfg.set("objective.defend_core", "&bProtect the Core Entity!");
                gameCfg.set("action.defend_failed", "&cThe core has been destroyed! Mission failed.");
                gameChanged = true;
            }
            if (!gameCfg.getConfig().contains("objective.escort_npc")) {
                gameCfg.set("objective.escort_npc", "&aEscort the VIP safely!");
                gameCfg.set("action.escort_failed", "&cThe VIP has been killed! Mission Failed.");
                gameChanged = true;
            }
            if (!gameCfg.getConfig().contains("objective.branching_path")) {
                gameCfg.set("objective.branching_path", "&eChoose your path to proceed.");
                gameCfg.set("action.branch_path_chosen", "&aPath chosen! Proceeding to Stage <stage>...");
                gameChanged = true;
            }
            if (!gameCfg.getConfig().contains("objective.damage_zone")) {
                gameCfg.set("objective.damage_zone", "&4Survive the hazard!");
                gameChanged = true;
            }
            if (!gameCfg.getConfig().contains("objective.lever_puzzle")) {
                gameCfg.set("objective.lever_puzzle", "&6Activate the levers in the correct order.");
                gameCfg.set("action.puzzle_solved", "&aPuzzle solved! The mechanism clicks open.");
                gameCfg.set("action.puzzle_failed", "&cIncorrect sequence! The mechanism resets.");
                gameCfg.set("action.puzzle_failed_penalty", "&cIncorrect sequence! The mechanism resets. Lost <time>s!");
                gameChanged = true;
            }
            if (!gameCfg.getConfig().contains("objective.give_item")) {
                gameCfg.set("objective.give_item", "&aReceiving Items...");
                gameChanged = true;
            }
            if (!gameCfg.getConfig().contains("objective.play_sound")) {
                gameCfg.set("objective.play_sound", "&dListening...");
                gameChanged = true;
            }
            if (!gameCfg.getConfig().contains("objective.projectile_trap")) {
                gameCfg.set("objective.projectile_trap", "&8Dodge the incoming projectiles!");
                gameChanged = true;
            }

            if (gameChanged) gameCfg.save();
        }
    }
}