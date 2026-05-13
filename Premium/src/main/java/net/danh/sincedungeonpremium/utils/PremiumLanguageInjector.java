package net.danh.sincedungeonpremium.utils;

import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.managers.LanguageManager;
import net.danh.sinceDungeon.utils.ConfigUtils;

import java.util.Arrays;
import java.util.List;

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

            if (!editorCfg.getConfig().contains("editor.actions_name.npc_interaction")) {
                editorCfg.set("editor.actions_name.npc_interaction", "&e&lPremium: NPC Interaction");
                editorCfg.set("editor.actions.npc_interaction", "Spawns an interactive NPC for dialogue, guide movement, hand-ins, and teleporting.");
                editorCfg.set("editor.input.prompts.edit_action_npc_location", Arrays.asList("&7Enter the NPC spawn coordinates X,Y,Z.", "&7Tip: Type &ahere &7to use your location."));
                editorCfg.set("editor.input.prompts.edit_action_interaction_mode", Arrays.asList("&7Enter the mode: &aTALK&7, &aGUIDE&7, &aGIVE_ITEM&7, or &aTELEPORT&7."));
                editorCfg.set("editor.input.prompts.edit_action_message_scope", Arrays.asList("&7Who receives dialogue? &aPLAYER &7or &aPARTY&7."));
                editorCfg.set("editor.input.prompts.edit_action_teleport_scope", Arrays.asList("&7Who should be teleported? &aPLAYER &7or &aPARTY&7."));
                editorCfg.set("editor.input.prompts.edit_action_move_speed", Arrays.asList("&7Enter the NPC movement speed multiplier.", "&7Example: &a1.0"));
                editorCfg.set("editor.input.prompts.edit_action_interaction_radius", Arrays.asList("&7Enter the max click distance from the NPC.", "&7Example: &a4.0"));
                editorCfg.set("editor.input.prompts.edit_action_start_on_click", Arrays.asList("&7Should GUIDE mode start only after clicking? &atrue &7or &cfalse&7."));
                editorCfg.set("editor.input.prompts.edit_action_npc_is_baby", Arrays.asList("&7Should the NPC be a baby? &atrue &7or &cfalse&7."));
                editorCfg.set("editor.input.prompts.edit_action_consume_required_item", Arrays.asList("&7Should GIVE_ITEM mode consume the required item? &atrue &7or &cfalse&7."));
                editorCfg.set("editor.input.prompts.edit_action_fail_on_npc_death", Arrays.asList("&7Should the dungeon fail if this NPC dies? &atrue &7or &cfalse&7."));
                editorCfg.set("editor.input.prompts.edit_action_click_cooldown_ticks", Arrays.asList("&7Enter click cooldown in ticks.", "&7Example: &a20 &7= 1 second"));
                editorCfg.set("editor.input.prompts.edit_action_dialogue_lines", Arrays.asList("&7Enter an NPC dialogue line.", "&7Placeholders: &a<player>&7, &a<npc>", "&7Tip: Type &cclear &7to remove all lines."));
                editorCfg.set("editor.input.prompts.edit_action_required_item", Arrays.asList("&7Enter the item required for GIVE_ITEM mode.", "&7Use &aNONE &7to disable.", "&7Examples: &aDIAMOND:1 &7or &aKEY:door_1:1"));
                editorCfg.set("editor.input.prompts.edit_action_reward_item", Arrays.asList("&7Enter the item given after successful interaction.", "&7Use &aNONE &7to disable.", "&7Examples: &aEMERALD:3 &7or &aMMOITEMS:SWORD:FIERY:1"));
                editorCfg.set("editor.input.prompts.edit_action_reward_display_name", Arrays.asList("&7Enter a display name for the reward message.", "&7Tip: Type &cclear &7to use the item name."));
                editorChanged = true;
            }

            if (!editorCfg.getConfig().contains("editor.defaults.escort.vip_name")) {
                editorCfg.set("editor.defaults.escort.vip_name", "&aVIP Escort");
                editorCfg.set("editor.defaults.escort.attacker_name", "&cAssassin");
                editorCfg.set("editor.defaults.defend_core.core_name", "&b&lSacred Crystal");
                editorCfg.set("editor.defaults.defend_core.attacker_name", "&cInvader");
                editorCfg.set("editor.defaults.give_item.message", "&aYou received a mysterious item...");
                editorCfg.set("editor.defaults.npc_interaction.name", "&eDungeon Guide");
                editorChanged = true;
            }

            editorChanged |= applyEditorLocaleOverrides(editorCfg, lang.getLocale());
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
            if (!errorCfg.getConfig().contains("error.npc_missing_item")) {
                errorCfg.set("error.npc_missing_item", "&cYou do not have the required item for this NPC.");
                errorChanged = true;
            }
            errorChanged |= applyErrorLocaleOverrides(errorCfg, lang.getLocale());
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
            if (!gameCfg.getConfig().contains("objective.npc_interaction")) {
                gameCfg.set("objective.npc_interaction", "&aInteract with the NPC &7(<mode>)");
                gameCfg.set("action.npc_spawned", "&eAn NPC is waiting for you.");
                gameCfg.set("action.npc_talk_complete", "&aThe conversation is complete.");
                gameCfg.set("action.npc_guide_start", "&eThe NPC starts guiding the party.");
                gameCfg.set("action.npc_already_moving", "&eThe NPC is already moving.");
                gameCfg.set("action.npc_guide_complete", "&aThe NPC reached the destination.");
                gameCfg.set("action.npc_item_complete", "&aThe NPC accepted your item.");
                gameCfg.set("action.npc_teleport_complete", "&aThe NPC opened the way forward.");
                gameCfg.set("action.npc_failed", "&cThe NPC was lost. Mission failed.");
                gameChanged = true;
            }

            gameChanged |= applyGameLocaleOverrides(gameCfg, lang.getLocale());
            if (gameChanged) gameCfg.save();
        }
    }

    private static boolean applyEditorLocaleOverrides(ConfigUtils cfg, String locale) {
        boolean changed = false;

        changed |= set(cfg, locale, "editor.actions_name.defend_core", "&3&lPremium: Defend Core", "&3&lPremium: Bảo Vệ Lõi", "&3&l高级版: 防守核心");
        changed |= set(cfg, locale, "editor.actions.defend_core", "Protect an entity from waves of enemies.", "Bảo vệ một thực thể trước các đợt kẻ địch.", "保护一个实体免受多波敌人攻击。");
        changed |= setList(cfg, locale, "editor.input.prompts.edit_action_core_type",
                Arrays.asList("&7Enter the EntityType of the Core to defend.", "&7Example: &aIRON_GOLEM &7or &aENDER_CRYSTAL"),
                Arrays.asList("&7Nhập EntityType của lõi cần bảo vệ.", "&7Ví dụ: &aIRON_GOLEM &7hoặc &aENDER_CRYSTAL"),
                Arrays.asList("&7输入需要防守的核心 EntityType。", "&7示例: &aIRON_GOLEM &7或 &aENDER_CRYSTAL"));
        changed |= setList(cfg, locale, "editor.input.prompts.edit_action_core_name",
                Arrays.asList("&7Enter the Hologram name of the Core.", "&7Example: &b&lSacred Crystal", "&7Tip: Type &cclear &7to remove name."),
                Arrays.asList("&7Nhập tên hiển thị của lõi.", "&7Ví dụ: &b&lPha Lê Thánh", "&7Mẹo: Gõ &cclear &7để xóa tên."),
                Arrays.asList("&7输入核心的显示名称。", "&7示例: &b&l神圣水晶", "&7提示: 输入 &cclear &7移除名称。"));
        changed |= setList(cfg, locale, "editor.input.prompts.edit_action_core_health",
                Arrays.asList("&7Enter the total health of the Core.", "&7Example: &a1000.0"),
                Arrays.asList("&7Nhập tổng máu của lõi.", "&7Ví dụ: &a1000.0"),
                Arrays.asList("&7输入核心总生命值。", "&7示例: &a1000.0"));

        changed |= set(cfg, locale, "editor.actions_name.jump_stage", "&c&lPremium: Jump Stage", "&c&lPremium: Nhảy Giai Đoạn", "&c&l高级版: 跳转阶段");
        changed |= set(cfg, locale, "editor.actions.jump_stage", "Forcibly skips execution to a different stage.", "Buộc hầm ngục chuyển sang một giai đoạn khác.", "强制跳转到另一个阶段。");
        changed |= setList(cfg, locale, "editor.input.prompts.edit_action_target_stage",
                Arrays.asList("&7Enter the stage index to jump to."),
                Arrays.asList("&7Nhập số thứ tự giai đoạn cần chuyển tới."),
                Arrays.asList("&7输入要跳转到的阶段编号。"));

        changed |= set(cfg, locale, "editor.actions_name.damage_zone", "&4&lPremium: Damage Zone", "&4&lPremium: Vùng Sát Thương", "&4&l高级版: 伤害区域");
        changed |= set(cfg, locale, "editor.actions.damage_zone", "Creates an AoE hazard that damages players.", "Tạo vùng nguy hiểm gây sát thương diện rộng.", "创建会伤害玩家的范围危险区域。");
        changed |= setList(cfg, locale, "editor.input.prompts.edit_action_damage",
                Arrays.asList("&7Enter the damage dealt per tick interval."),
                Arrays.asList("&7Nhập sát thương gây ra mỗi chu kỳ."),
                Arrays.asList("&7输入每个间隔造成的伤害。"));
        changed |= setList(cfg, locale, "editor.input.prompts.edit_action_interval",
                Arrays.asList("&7Enter the tick interval (20 ticks = 1s).", "&7Example: &a20"),
                Arrays.asList("&7Nhập chu kỳ theo tick (20 tick = 1 giây).", "&7Ví dụ: &a20"),
                Arrays.asList("&7输入 tick 间隔 (20 tick = 1 秒)。", "&7示例: &a20"));

        changed |= set(cfg, locale, "editor.actions_name.save_checkpoint", "&b&lPremium: Save Checkpoint", "&b&lPremium: Lưu Điểm Hồi Sinh", "&b&l高级版: 保存检查点");
        changed |= set(cfg, locale, "editor.actions.save_checkpoint", "Updates the dungeon respawn point when touched.", "Cập nhật điểm hồi sinh trong hầm ngục khi chạm vào.", "触碰后更新副本重生点。");
        changed |= setList(cfg, locale, "editor.input.prompts.edit_action_particle",
                Arrays.asList("&7Enter the Particle type.", "&7Example: &aFLAME"),
                Arrays.asList("&7Nhập loại Particle.", "&7Ví dụ: &aFLAME"),
                Arrays.asList("&7输入粒子类型。", "&7示例: &aFLAME"));

        changed |= set(cfg, locale, "editor.actions_name.lever_puzzle", "&6&lPremium: Lever Puzzle", "&6&lPremium: Câu Đố Cần Gạt", "&6&l高级版: 拉杆谜题");
        changed |= set(cfg, locale, "editor.actions.lever_puzzle", "Requires players to hit levers in a specific order.", "Yêu cầu người chơi gạt cần theo đúng thứ tự.", "要求玩家按指定顺序点击拉杆。");
        changed |= setList(cfg, locale, "editor.input.prompts.edit_action_fail_time_penalty",
                Arrays.asList("&7Enter the time penalty (seconds) on failure."),
                Arrays.asList("&7Nhập thời gian phạt khi thất bại (giây)."),
                Arrays.asList("&7输入失败时扣除的时间 (秒)。"));

        changed |= set(cfg, locale, "editor.actions_name.branching_path", "&a&lPremium: Branching Path", "&a&lPremium: Rẽ Nhánh", "&a&l高级版: 分支路线");
        changed |= set(cfg, locale, "editor.actions.branching_path", "Diverges the dungeon into two separate stage paths.", "Chia hầm ngục thành hai nhánh giai đoạn khác nhau.", "将副本分成两条不同阶段路线。");
        changed |= setList(cfg, locale, "editor.input.prompts.edit_action_path_a_loc", Arrays.asList("&7Enter the coordinates for Path A."), Arrays.asList("&7Nhập tọa độ cho Nhánh A."), Arrays.asList("&7输入路线 A 的坐标。"));
        changed |= setList(cfg, locale, "editor.input.prompts.edit_action_path_b_loc", Arrays.asList("&7Enter the coordinates for Path B."), Arrays.asList("&7Nhập tọa độ cho Nhánh B."), Arrays.asList("&7输入路线 B 的坐标。"));
        changed |= setList(cfg, locale, "editor.input.prompts.edit_action_stage_a", Arrays.asList("&7Enter the Stage Index for Path A."), Arrays.asList("&7Nhập số giai đoạn cho Nhánh A."), Arrays.asList("&7输入路线 A 的阶段编号。"));
        changed |= setList(cfg, locale, "editor.input.prompts.edit_action_stage_b", Arrays.asList("&7Enter the Stage Index for Path B."), Arrays.asList("&7Nhập số giai đoạn cho Nhánh B."), Arrays.asList("&7输入路线 B 的阶段编号。"));

        changed |= set(cfg, locale, "editor.actions_name.projectile_trap", "&8&lPremium: Projectile Trap", "&8&lPremium: Bẫy Đạn Bay", "&8&l高级版: 弹射物陷阱");
        changed |= set(cfg, locale, "editor.actions.projectile_trap", "Continuously fires projectiles in a direction.", "Liên tục bắn vật thể bay theo một hướng.", "持续向指定方向发射弹射物。");
        changed |= setList(cfg, locale, "editor.input.prompts.edit_action_direction", Arrays.asList("&7Enter the Vector direction X,Y,Z.", "&7Example: &a0,-1,0"), Arrays.asList("&7Nhập hướng Vector X,Y,Z.", "&7Ví dụ: &a0,-1,0"), Arrays.asList("&7输入方向向量 X,Y,Z。", "&7示例: &a0,-1,0"));
        changed |= setList(cfg, locale, "editor.input.prompts.edit_action_projectile_type", Arrays.asList("&7Enter the Projectile EntityType.", "&7Example: &aARROW"), Arrays.asList("&7Nhập EntityType của vật thể bay.", "&7Ví dụ: &aARROW"), Arrays.asList("&7输入弹射物 EntityType。", "&7示例: &aARROW"));

        changed |= set(cfg, locale, "editor.actions_name.cinematic_dialogue", "&d&lPremium: Cinematic", "&d&lPremium: Hội Thoại Điện Ảnh", "&d&l高级版: 过场对白");
        changed |= set(cfg, locale, "editor.actions.cinematic_dialogue", "Plays a sequence of titles, text, and sounds.", "Phát chuỗi tiêu đề, lời thoại và âm thanh.", "播放标题、文字和声音序列。");
        changed |= setList(cfg, locale, "editor.input.prompts.edit_action_frames", Arrays.asList("&7Enter Cinematic Frame data.", "&7Format: &adelay;title;subtitle;chat;sound", "&7Tip: Type &cclear &7to empty."), Arrays.asList("&7Nhập dữ liệu khung hội thoại.", "&7Định dạng: &adelay;title;subtitle;chat;sound", "&7Mẹo: Gõ &cclear &7để làm trống."), Arrays.asList("&7输入过场帧数据。", "&7格式: &adelay;title;subtitle;chat;sound", "&7提示: 输入 &cclear &7清空。"));

        changed |= set(cfg, locale, "editor.actions_name.apply_buff", "&b&lPremium: Apply Buff", "&b&lPremium: Áp Dụng Hiệu Ứng", "&b&l高级版: 施加增益");
        changed |= set(cfg, locale, "editor.actions.apply_buff", "Applies a potion effect to all participants.", "Áp dụng hiệu ứng thuốc cho toàn bộ người tham gia.", "为所有参与者施加药水效果。");
        changed |= setList(cfg, locale, "editor.input.prompts.edit_action_effect_type", Arrays.asList("&7Enter the Potion Effect type.", "&7Example: &aSPEED"), Arrays.asList("&7Nhập loại hiệu ứng thuốc.", "&7Ví dụ: &aSPEED"), Arrays.asList("&7输入药水效果类型。", "&7示例: &aSPEED"));
        changed |= setList(cfg, locale, "editor.input.prompts.edit_action_duration", Arrays.asList("&7Enter duration in ticks (20 = 1s).", "&7Example: &a200"), Arrays.asList("&7Nhập thời lượng theo tick (20 = 1 giây).", "&7Ví dụ: &a200"), Arrays.asList("&7输入持续时间 tick (20 = 1 秒)。", "&7示例: &a200"));
        changed |= setList(cfg, locale, "editor.input.prompts.edit_action_amplifier", Arrays.asList("&7Enter effect amplifier (0 = level 1).", "&7Example: &a1"), Arrays.asList("&7Nhập cấp khuếch đại hiệu ứng (0 = cấp 1).", "&7Ví dụ: &a1"), Arrays.asList("&7输入效果等级增幅 (0 = 1 级)。", "&7示例: &a1"));

        changed |= set(cfg, locale, "editor.actions_name.escort_npc", "&e&lPremium: Escort NPC", "&e&lPremium: Hộ Tống NPC", "&e&l高级版: 护送 NPC");
        changed |= set(cfg, locale, "editor.actions.escort_npc", "Protect an NPC as they travel to a destination.", "Bảo vệ NPC khi họ di chuyển tới điểm đích.", "保护 NPC 前往目标位置。");
        changed |= setList(cfg, locale, "editor.input.prompts.edit_action_entity_type", Arrays.asList("&7Enter the EntityType for the VIP.", "&7Example: &aVILLAGER"), Arrays.asList("&7Nhập EntityType cho NPC.", "&7Ví dụ: &aVILLAGER"), Arrays.asList("&7输入 NPC 的 EntityType。", "&7示例: &aVILLAGER"));
        changed |= setList(cfg, locale, "editor.input.prompts.edit_action_start_location", Arrays.asList("&7Enter the starting coordinates X,Y,Z.", "&7Tip: Type &ahere &7to use your location."), Arrays.asList("&7Nhập tọa độ bắt đầu X,Y,Z.", "&7Mẹo: Gõ &ahere &7để dùng vị trí hiện tại."), Arrays.asList("&7输入起点坐标 X,Y,Z。", "&7提示: 输入 &ahere &7使用当前位置。"));
        changed |= setList(cfg, locale, "editor.input.prompts.edit_action_target_location", Arrays.asList("&7Enter the destination coordinates X,Y,Z."), Arrays.asList("&7Nhập tọa độ đích X,Y,Z."), Arrays.asList("&7输入目标坐标 X,Y,Z。"));
        changed |= setList(cfg, locale, "editor.input.prompts.edit_action_speed", Arrays.asList("&7Enter the movement speed multiplier.", "&7Example: &a1.0"), Arrays.asList("&7Nhập hệ số tốc độ di chuyển.", "&7Ví dụ: &a1.0"), Arrays.asList("&7输入移动速度倍率。", "&7示例: &a1.0"));
        changed |= setList(cfg, locale, "editor.input.prompts.edit_action_success_radius", Arrays.asList("&7Enter the completion radius.", "&7Example: &a4.0"), Arrays.asList("&7Nhập bán kính hoàn thành.", "&7Ví dụ: &a4.0"), Arrays.asList("&7输入完成半径。", "&7示例: &a4.0"));
        changed |= setList(cfg, locale, "editor.input.prompts.edit_action_vip_is_baby", Arrays.asList("&7Is the VIP a baby? &atrue &7or &cfalse"), Arrays.asList("&7NPC hộ tống có phải dạng nhỏ không? &atrue &7hoặc &cfalse"), Arrays.asList("&7护送 NPC 是否为幼年? &atrue &7或 &cfalse"));
        changed |= setList(cfg, locale, "editor.input.prompts.edit_action_attacker_mob", Arrays.asList("&7Enter the EntityType of the attackers.", "&7Example: &aZOMBIE"), Arrays.asList("&7Nhập EntityType của kẻ tấn công.", "&7Ví dụ: &aZOMBIE"), Arrays.asList("&7输入攻击者 EntityType。", "&7示例: &aZOMBIE"));
        changed |= setList(cfg, locale, "editor.input.prompts.edit_action_attacker_amount", Arrays.asList("&7Enter the amount of attackers per wave.", "&7Example: &a3"), Arrays.asList("&7Nhập số kẻ tấn công mỗi đợt.", "&7Ví dụ: &a3"), Arrays.asList("&7输入每波攻击者数量。", "&7示例: &a3"));
        changed |= setList(cfg, locale, "editor.input.prompts.edit_action_attacker_interval", Arrays.asList("&7Enter the interval (ticks) between waves.", "&7Example: &a100"), Arrays.asList("&7Nhập khoảng cách giữa các đợt (tick).", "&7Ví dụ: &a100"), Arrays.asList("&7输入每波间隔 (tick)。", "&7示例: &a100"));
        changed |= setList(cfg, locale, "editor.input.prompts.edit_action_attacker_name", Arrays.asList("&7Enter the custom name for attackers.", "&7Tip: Type &cclear &7to reset."), Arrays.asList("&7Nhập tên tùy chỉnh cho kẻ tấn công.", "&7Mẹo: Gõ &cclear &7để đặt lại."), Arrays.asList("&7输入攻击者自定义名称。", "&7提示: 输入 &cclear &7重置。"));
        changed |= setList(cfg, locale, "editor.input.prompts.edit_action_attacker_is_baby", Arrays.asList("&7Are attackers babies? &atrue &7or &cfalse"), Arrays.asList("&7Kẻ tấn công có phải dạng nhỏ không? &atrue &7hoặc &cfalse"), Arrays.asList("&7攻击者是否为幼年? &atrue &7或 &cfalse"));

        changed |= set(cfg, locale, "editor.actions_name.give_item", "&6&lPremium: Give Item", "&6&lPremium: Trao Vật Phẩm", "&6&l高级版: 给予物品");
        changed |= set(cfg, locale, "editor.actions.give_item", "Gives a specific item directly to the party.", "Trao vật phẩm cụ thể trực tiếp cho tổ đội.", "直接给予队伍指定物品。");
        changed |= setList(cfg, locale, "editor.input.prompts.edit_action_item_data", Arrays.asList("&7Enter the Item format to give.", "&7Vanilla: &aDIAMOND:5", "&7MMOItems: &aMMOITEMS:SWORD:FIERY:1", "&7Dungeon Key: &aKEY:door_1:1"), Arrays.asList("&7Nhập định dạng vật phẩm cần trao.", "&7Vanilla: &aDIAMOND:5", "&7MMOItems: &aMMOITEMS:SWORD:FIERY:1", "&7Chìa khóa: &aKEY:door_1:1"), Arrays.asList("&7输入要给予的物品格式。", "&7原版: &aDIAMOND:5", "&7MMOItems: &aMMOITEMS:SWORD:FIERY:1", "&7副本钥匙: &aKEY:door_1:1"));
        changed |= setList(cfg, locale, "editor.input.prompts.edit_action_receive_message", Arrays.asList("&7Enter the message to display when the item is received.", "&7Tip: Type &cclear &7to disable."), Arrays.asList("&7Nhập tin nhắn hiển thị khi nhận vật phẩm.", "&7Mẹo: Gõ &cclear &7để tắt."), Arrays.asList("&7输入收到物品时显示的消息。", "&7提示: 输入 &cclear &7禁用。"));

        changed |= set(cfg, locale, "editor.actions_name.play_sound", "&d&lPremium: Play Sound", "&d&lPremium: Phát Âm Thanh", "&d&l高级版: 播放声音");
        changed |= set(cfg, locale, "editor.actions.play_sound", "Plays a global sound effect for the party.", "Phát hiệu ứng âm thanh cho toàn bộ tổ đội.", "为队伍播放全局音效。");
        changed |= setList(cfg, locale, "editor.input.prompts.edit_action_sound_name", Arrays.asList("&7Enter the sound event name.", "&7Example: &aentity.ender_dragon.growl"), Arrays.asList("&7Nhập tên sự kiện âm thanh.", "&7Ví dụ: &aentity.ender_dragon.growl"), Arrays.asList("&7输入声音事件名称。", "&7示例: &aentity.ender_dragon.growl"));
        changed |= setList(cfg, locale, "editor.input.prompts.edit_action_volume", Arrays.asList("&7Enter the sound volume (float).", "&7Example: &a1.0"), Arrays.asList("&7Nhập âm lượng (số thập phân).", "&7Ví dụ: &a1.0"), Arrays.asList("&7输入音量 (小数)。", "&7示例: &a1.0"));
        changed |= setList(cfg, locale, "editor.input.prompts.edit_action_pitch", Arrays.asList("&7Enter the sound pitch (float).", "&7Example: &a1.0"), Arrays.asList("&7Nhập cao độ âm thanh (số thập phân).", "&7Ví dụ: &a1.0"), Arrays.asList("&7输入音调 (小数)。", "&7示例: &a1.0"));

        changed |= set(cfg, locale, "editor.actions_name.npc_interaction", "&e&lPremium: NPC Interaction", "&e&lPremium: Tương Tác NPC", "&e&l高级版: NPC 交互");
        changed |= set(cfg, locale, "editor.actions.npc_interaction", "Spawns an interactive NPC for dialogue, guide movement, hand-ins, and teleporting.", "Tạo NPC có thể trò chuyện, dẫn đường, nhận/trao vật phẩm và dịch chuyển.", "生成可对话、引路、交付物品和传送的交互 NPC。");
        changed |= setList(cfg, locale, "editor.input.prompts.edit_action_npc_location", Arrays.asList("&7Enter the NPC spawn coordinates X,Y,Z.", "&7Tip: Type &ahere &7to use your location."), Arrays.asList("&7Nhập tọa độ sinh NPC X,Y,Z.", "&7Mẹo: Gõ &ahere &7để dùng vị trí hiện tại."), Arrays.asList("&7输入 NPC 生成坐标 X,Y,Z。", "&7提示: 输入 &ahere &7使用当前位置。"));
        changed |= setList(cfg, locale, "editor.input.prompts.edit_action_interaction_mode", Arrays.asList("&7Enter the mode: &aTALK&7, &aGUIDE&7, &aGIVE_ITEM&7, or &aTELEPORT&7."), Arrays.asList("&7Nhập chế độ: &aTALK&7, &aGUIDE&7, &aGIVE_ITEM&7, hoặc &aTELEPORT&7."), Arrays.asList("&7输入模式: &aTALK&7, &aGUIDE&7, &aGIVE_ITEM&7, 或 &aTELEPORT&7。"));
        changed |= setList(cfg, locale, "editor.input.prompts.edit_action_message_scope", Arrays.asList("&7Who receives dialogue? &aPLAYER &7or &aPARTY&7."), Arrays.asList("&7Ai nhận lời thoại? &aPLAYER &7hoặc &aPARTY&7."), Arrays.asList("&7谁接收对话? &aPLAYER &7或 &aPARTY&7。"));
        changed |= setList(cfg, locale, "editor.input.prompts.edit_action_teleport_scope", Arrays.asList("&7Who should be teleported? &aPLAYER &7or &aPARTY&7."), Arrays.asList("&7Ai sẽ được dịch chuyển? &aPLAYER &7hoặc &aPARTY&7."), Arrays.asList("&7谁会被传送? &aPLAYER &7或 &aPARTY&7。"));
        changed |= setList(cfg, locale, "editor.input.prompts.edit_action_move_speed", Arrays.asList("&7Enter the NPC movement speed multiplier.", "&7Example: &a1.0"), Arrays.asList("&7Nhập hệ số tốc độ di chuyển của NPC.", "&7Ví dụ: &a1.0"), Arrays.asList("&7输入 NPC 移动速度倍率。", "&7示例: &a1.0"));
        changed |= setList(cfg, locale, "editor.input.prompts.edit_action_interaction_radius", Arrays.asList("&7Enter the max click distance from the NPC.", "&7Example: &a4.0"), Arrays.asList("&7Nhập khoảng cách tối đa để nhấn NPC.", "&7Ví dụ: &a4.0"), Arrays.asList("&7输入可点击 NPC 的最大距离。", "&7示例: &a4.0"));
        changed |= setList(cfg, locale, "editor.input.prompts.edit_action_start_on_click", Arrays.asList("&7Should GUIDE mode start only after clicking? &atrue &7or &cfalse&7."), Arrays.asList("&7Chế độ GUIDE chỉ bắt đầu khi nhấn NPC? &atrue &7hoặc &cfalse&7."), Arrays.asList("&7GUIDE 模式是否仅在点击后开始? &atrue &7或 &cfalse&7。"));
        changed |= setList(cfg, locale, "editor.input.prompts.edit_action_npc_is_baby", Arrays.asList("&7Should the NPC be a baby? &atrue &7or &cfalse&7."), Arrays.asList("&7NPC có phải dạng nhỏ không? &atrue &7hoặc &cfalse&7."), Arrays.asList("&7NPC 是否为幼年? &atrue &7或 &cfalse&7。"));
        changed |= setList(cfg, locale, "editor.input.prompts.edit_action_consume_required_item", Arrays.asList("&7Should GIVE_ITEM mode consume the required item? &atrue &7or &cfalse&7."), Arrays.asList("&7Chế độ GIVE_ITEM có tiêu hao vật phẩm yêu cầu không? &atrue &7hoặc &cfalse&7."), Arrays.asList("&7GIVE_ITEM 模式是否消耗所需物品? &atrue &7或 &cfalse&7。"));
        changed |= setList(cfg, locale, "editor.input.prompts.edit_action_fail_on_npc_death", Arrays.asList("&7Should the dungeon fail if this NPC dies? &atrue &7or &cfalse&7."), Arrays.asList("&7Hầm ngục thất bại nếu NPC chết? &atrue &7hoặc &cfalse&7."), Arrays.asList("&7NPC 死亡时副本是否失败? &atrue &7或 &cfalse&7。"));
        changed |= setList(cfg, locale, "editor.input.prompts.edit_action_click_cooldown_ticks", Arrays.asList("&7Enter click cooldown in ticks.", "&7Example: &a20 &7= 1 second"), Arrays.asList("&7Nhập hồi chiêu nhấn theo tick.", "&7Ví dụ: &a20 &7= 1 giây"), Arrays.asList("&7输入点击冷却 tick。", "&7示例: &a20 &7= 1 秒"));
        changed |= setList(cfg, locale, "editor.input.prompts.edit_action_dialogue_lines", Arrays.asList("&7Enter an NPC dialogue line.", "&7Placeholders: &a<player>&7, &a<npc>", "&7Tip: Type &cclear &7to remove all lines."), Arrays.asList("&7Nhập một dòng lời thoại NPC.", "&7Placeholder: &a<player>&7, &a<npc>", "&7Mẹo: Gõ &cclear &7để xóa toàn bộ."), Arrays.asList("&7输入一行 NPC 对话。", "&7占位符: &a<player>&7, &a<npc>", "&7提示: 输入 &cclear &7移除所有行。"));
        changed |= setList(cfg, locale, "editor.input.prompts.edit_action_required_item", Arrays.asList("&7Enter the item required for GIVE_ITEM mode.", "&7Use &aNONE &7to disable.", "&7Examples: &aDIAMOND:1 &7or &aKEY:door_1:1"), Arrays.asList("&7Nhập vật phẩm yêu cầu cho chế độ GIVE_ITEM.", "&7Dùng &aNONE &7để tắt.", "&7Ví dụ: &aDIAMOND:1 &7hoặc &aKEY:door_1:1"), Arrays.asList("&7输入 GIVE_ITEM 模式需要的物品。", "&7使用 &aNONE &7禁用。", "&7示例: &aDIAMOND:1 &7或 &aKEY:door_1:1"));
        changed |= setList(cfg, locale, "editor.input.prompts.edit_action_reward_item", Arrays.asList("&7Enter the item given after successful interaction.", "&7Use &aNONE &7to disable.", "&7Examples: &aEMERALD:3 &7or &aMMOITEMS:SWORD:FIERY:1"), Arrays.asList("&7Nhập vật phẩm trao sau khi tương tác thành công.", "&7Dùng &aNONE &7để tắt.", "&7Ví dụ: &aEMERALD:3 &7hoặc &aMMOITEMS:SWORD:FIERY:1"), Arrays.asList("&7输入交互成功后给予的物品。", "&7使用 &aNONE &7禁用。", "&7示例: &aEMERALD:3 &7或 &aMMOITEMS:SWORD:FIERY:1"));
        changed |= setList(cfg, locale, "editor.input.prompts.edit_action_reward_display_name", Arrays.asList("&7Enter a display name for the reward message.", "&7Tip: Type &cclear &7to use the item name."), Arrays.asList("&7Nhập tên hiển thị cho tin nhắn phần thưởng.", "&7Mẹo: Gõ &cclear &7để dùng tên vật phẩm."), Arrays.asList("&7输入奖励消息显示名称。", "&7提示: 输入 &cclear &7使用物品名称。"));

        changed |= set(cfg, locale, "editor.defaults.escort.vip_name", "&aVIP Escort", "&aNPC Hộ Tống", "&a护送 NPC");
        changed |= set(cfg, locale, "editor.defaults.escort.attacker_name", "&cAssassin", "&cSát Thủ", "&c刺客");
        changed |= set(cfg, locale, "editor.defaults.defend_core.core_name", "&b&lSacred Crystal", "&b&lPha Lê Thánh", "&b&l神圣水晶");
        changed |= set(cfg, locale, "editor.defaults.defend_core.attacker_name", "&cInvader", "&cKẻ Xâm Lược", "&c入侵者");
        changed |= set(cfg, locale, "editor.defaults.give_item.message", "&aYou received a mysterious item...", "&aBạn nhận được một vật phẩm bí ẩn...", "&a你获得了一个神秘物品...");
        changed |= set(cfg, locale, "editor.defaults.npc_interaction.name", "&eDungeon Guide", "&eNgười Dẫn Đường", "&e副本向导");

        return changed;
    }

    private static boolean applyErrorLocaleOverrides(ConfigUtils cfg, String locale) {
        boolean changed = false;
        changed |= set(cfg, locale, "error.missing_required_item", "&cYou lack the required item to enter: <item>", "&cBạn thiếu vật phẩm yêu cầu để vào: <item>", "&c你缺少进入所需物品: <item>");
        changed |= set(cfg, locale, "error.party_member_missing_item", "&cMember <player> lacks the required entry item.", "&cThành viên <player> thiếu vật phẩm yêu cầu để vào.", "&c成员 <player> 缺少进入所需物品。");
        changed |= set(cfg, locale, "error.target_not_in_dungeon", "&cThat player is not in your dungeon!", "&cNgười chơi đó không ở trong hầm ngục của bạn!", "&c该玩家不在你的副本中!");
        changed |= set(cfg, locale, "error.target_not_spectator", "&cThat player is not knocked out!", "&cNgười chơi đó chưa bị hạ gục!", "&c该玩家并未被击倒!");
        changed |= set(cfg, locale, "error.no_life_item", "&cYou need a Soul Crystal in your inventory to revive someone!", "&cBạn cần có Soul Crystal trong túi đồ để hồi sinh người khác!", "&c你需要背包中有灵魂水晶才能复活他人!");
        changed |= set(cfg, locale, "error.npc_missing_item", "&cYou do not have the required item for this NPC.", "&cBạn không có vật phẩm mà NPC này yêu cầu.", "&c你没有此 NPC 所需的物品。");
        return changed;
    }

    private static boolean applyGameLocaleOverrides(ConfigUtils cfg, String locale) {
        boolean changed = false;
        changed |= set(cfg, locale, "game.revived_target", "&aYou have been revived by <player>!", "&aBạn đã được <player> hồi sinh!", "&a你已被 <player> 复活!");
        changed |= set(cfg, locale, "game.revived_sender", "&aYou revived <player>!", "&aBạn đã hồi sinh <player>!", "&a你复活了 <player>!");
        changed |= set(cfg, locale, "game.revived_broadcast", "&e<sender> revived <target> using a Soul Crystal!", "&e<sender> đã dùng Soul Crystal để hồi sinh <target>!", "&e<sender> 使用灵魂水晶复活了 <target>!");
        changed |= set(cfg, locale, "objective.save_checkpoint", "&bReach the Checkpoint!", "&bĐi tới điểm lưu!", "&b到达检查点!");
        changed |= set(cfg, locale, "action.checkpoint_start", "&eA checkpoint ring has appeared. Stand inside it to secure your respawn point!", "&eVòng điểm lưu đã xuất hiện. Đứng bên trong để lưu điểm hồi sinh!", "&e检查点光环已出现。站进去以保存重生点!");
        changed |= set(cfg, locale, "action.checkpoint_complete", "&aCheckpoint secured! You will respawn here if you die.", "&aĐã lưu điểm hồi sinh! Bạn sẽ hồi sinh tại đây nếu chết.", "&a检查点已保存! 死亡后你将在这里重生。");
        changed |= set(cfg, locale, "objective.defend_core", "&bProtect the Core Entity!", "&bBảo vệ lõi!", "&b保护核心实体!");
        changed |= set(cfg, locale, "action.defend_failed", "&cThe core has been destroyed! Mission failed.", "&cLõi đã bị phá hủy! Nhiệm vụ thất bại.", "&c核心已被摧毁! 任务失败。");
        changed |= set(cfg, locale, "objective.escort_npc", "&aEscort the VIP safely!", "&aHộ tống NPC an toàn!", "&a安全护送 VIP!");
        changed |= set(cfg, locale, "action.escort_failed", "&cThe VIP has been killed! Mission Failed.", "&cNPC hộ tống đã bị hạ! Nhiệm vụ thất bại.", "&cVIP 已被击杀! 任务失败。");
        changed |= set(cfg, locale, "objective.branching_path", "&eChoose your path to proceed.", "&eChọn nhánh đường để tiếp tục.", "&e选择路线以继续。");
        changed |= set(cfg, locale, "action.branch_path_chosen", "&aPath chosen! Proceeding to Stage <stage>...", "&aĐã chọn nhánh! Chuyển tới Giai đoạn <stage>...", "&a路线已选择! 前往阶段 <stage>...");
        changed |= set(cfg, locale, "objective.damage_zone", "&4Survive the hazard!", "&4Sống sót khỏi vùng nguy hiểm!", "&4在危险区域中生存!");
        changed |= set(cfg, locale, "objective.lever_puzzle", "&6Activate the levers in the correct order.", "&6Gạt các cần theo đúng thứ tự.", "&6按正确顺序启动拉杆。");
        changed |= set(cfg, locale, "action.puzzle_solved", "&aPuzzle solved! The mechanism clicks open.", "&aĐã giải câu đố! Cơ chế đã mở.", "&a谜题已解开! 机关已打开。");
        changed |= set(cfg, locale, "action.puzzle_failed", "&cIncorrect sequence! The mechanism resets.", "&cSai thứ tự! Cơ chế đã đặt lại.", "&c顺序错误! 机关已重置。");
        changed |= set(cfg, locale, "action.puzzle_failed_penalty", "&cIncorrect sequence! The mechanism resets. Lost <time>s!", "&cSai thứ tự! Cơ chế đã đặt lại. Mất <time> giây!", "&c顺序错误! 机关已重置。损失 <time> 秒!");
        changed |= set(cfg, locale, "objective.give_item", "&aReceiving Items...", "&aĐang nhận vật phẩm...", "&a正在接收物品...");
        changed |= set(cfg, locale, "objective.play_sound", "&dListening...", "&dĐang lắng nghe...", "&d正在聆听...");
        changed |= set(cfg, locale, "objective.projectile_trap", "&8Dodge the incoming projectiles!", "&8Né các vật thể đang bay tới!", "&8躲避来袭弹射物!");
        changed |= set(cfg, locale, "objective.npc_interaction", "&aInteract with the NPC &7(<mode>)", "&aTương tác với NPC &7(<mode>)", "&a与 NPC 交互 &7(<mode>)");
        changed |= set(cfg, locale, "action.npc_spawned", "&eAn NPC is waiting for you.", "&eMột NPC đang chờ bạn.", "&e有一个 NPC 正在等你。");
        changed |= set(cfg, locale, "action.npc_talk_complete", "&aThe conversation is complete.", "&aCuộc trò chuyện đã hoàn tất.", "&a对话已完成。");
        changed |= set(cfg, locale, "action.npc_guide_start", "&eThe NPC starts guiding the party.", "&eNPC bắt đầu dẫn đường cho tổ đội.", "&eNPC 开始为队伍引路。");
        changed |= set(cfg, locale, "action.npc_already_moving", "&eThe NPC is already moving.", "&eNPC đang di chuyển rồi.", "&eNPC 已经在移动。");
        changed |= set(cfg, locale, "action.npc_guide_complete", "&aThe NPC reached the destination.", "&aNPC đã tới điểm đích.", "&aNPC 已到达目的地。");
        changed |= set(cfg, locale, "action.npc_item_complete", "&aThe NPC accepted your item.", "&aNPC đã nhận vật phẩm của bạn.", "&aNPC 接受了你的物品。");
        changed |= set(cfg, locale, "action.npc_teleport_complete", "&aThe NPC opened the way forward.", "&aNPC đã mở đường đi tiếp.", "&aNPC 开启了前进的道路。");
        changed |= set(cfg, locale, "action.npc_failed", "&cThe NPC was lost. Mission failed.", "&cNPC đã mất. Nhiệm vụ thất bại.", "&cNPC 已丢失。任务失败。");
        return changed;
    }

    private static boolean set(ConfigUtils cfg, String locale, String path, String en, String vi, String zh) {
        String target = choose(locale, en, vi, zh);
        String current = cfg.getConfig().getString(path);
        if (current == null || current.isEmpty() || current.equals(en)) {
            cfg.set(path, target);
            return true;
        }
        return false;
    }

    private static boolean setList(ConfigUtils cfg, String locale, String path, List<String> en, List<String> vi, List<String> zh) {
        List<String> target = chooseList(locale, en, vi, zh);
        List<String> current = cfg.getConfig().getStringList(path);
        if (!cfg.getConfig().contains(path) || current.isEmpty() || current.equals(en)) {
            cfg.set(path, target);
            return true;
        }
        return false;
    }

    private static String choose(String locale, String en, String vi, String zh) {
        if (locale != null && locale.toLowerCase().startsWith("vi")) return vi;
        if (locale != null && locale.toLowerCase().startsWith("zh")) return zh;
        return en;
    }

    private static List<String> chooseList(String locale, List<String> en, List<String> vi, List<String> zh) {
        if (locale != null && locale.toLowerCase().startsWith("vi")) return vi;
        if (locale != null && locale.toLowerCase().startsWith("zh")) return zh;
        return en;
    }
}
