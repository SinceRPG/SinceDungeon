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

            // Register Missing Item Settings Prompts
            if (!editorCfg.getConfig().contains("editor.items.setting_req_item")) {
                editorCfg.set("editor.items.setting_req_item", "&eRequired Entry Item");
                editorCfg.set("editor.items.setting_req_item_lore", Arrays.asList("&7Current: &f<val>", "&eLeft Click to edit"));
                editorCfg.set("editor.items.setting_consume_req_item", "&eConsume Required Item");
                editorCfg.set("editor.items.setting_consume_req_item_lore", Arrays.asList("&7Current: <val>", "&eLeft Click to toggle"));
                editorChanged = true;
            }

            // Standardize Prompts
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

            // Defend Core
            if (!editorCfg.getConfig().contains("editor.actions_name.defend_core")) {
                editorCfg.set("editor.actions_name.defend_core", "&3&lPremium: Defend Core");
                editorCfg.set("editor.actions.defend_core", "Protect an entity from waves of enemies.");
                editorCfg.set("editor.input.prompts.edit_action_core_type", Arrays.asList("&7Enter the EntityType of the Core to defend.", "&7Example: &aIRON_GOLEM &7or &aENDER_CRYSTAL"));
                editorCfg.set("editor.input.prompts.edit_action_core_name", Arrays.asList("&7Enter the Hologram name of the Core.", "&7Example: &b&lSacred Crystal", "&7Tip: Type &cclear &7to remove name."));
                editorCfg.set("editor.input.prompts.edit_action_core_health", Arrays.asList("&7Enter the total health of the Core.", "&7Example: &a1000.0"));
                editorCfg.set("editor.input.prompts.edit_action_duration", Arrays.asList("&7Enter the defense duration in ticks (20 ticks = 1 second).", "&7Example: &a600 &7(For 30 seconds)"));
                editorChanged = true;
            }

            // Give Item Action
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

            // Play Sound Action
            if (!editorCfg.getConfig().contains("editor.actions_name.play_sound")) {
                editorCfg.set("editor.actions_name.play_sound", "&d&lPremium: Play Sound");
                editorCfg.set("editor.actions.play_sound", "Plays a global sound effect for the party.");
                editorCfg.set("editor.input.prompts.edit_action_sound_name", Arrays.asList("&7Enter the sound event name.", "&7Example: &aentity.ender_dragon.growl"));
                editorCfg.set("editor.input.prompts.edit_action_volume", Arrays.asList("&7Enter the sound volume (float).", "&7Example: &a1.0"));
                editorCfg.set("editor.input.prompts.edit_action_pitch", Arrays.asList("&7Enter the sound pitch (float).", "&7Example: &a1.0"));
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
                errorChanged = true;
            }
            if (errorChanged) errorCfg.save();
        }

        ConfigUtils gameCfg = lang.getConfigUtilsForPath("objective.test");
        if (gameCfg != null) {
            boolean gameChanged = false;

            if (!gameCfg.getConfig().contains("objective.defend_core")) {
                gameCfg.set("objective.defend_core", "&bProtect the Core Entity!");
                gameCfg.set("action.defend_failed", "&cThe core has been destroyed! Mission failed.");
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

            if (gameChanged) gameCfg.save();
        }
    }
}