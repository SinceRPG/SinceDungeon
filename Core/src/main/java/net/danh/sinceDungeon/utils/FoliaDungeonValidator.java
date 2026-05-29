package net.danh.sinceDungeon.utils;

import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.api.interfaces.InstanceProvider;
import net.danh.sinceDungeon.models.DungeonTemplate;
import org.bukkit.entity.Player;

/**
 * Central Folia guard for dungeon configs that require runtime world creation.
 */
public final class FoliaDungeonValidator {
    private FoliaDungeonValidator() {
    }

    public static boolean isWorldCopyUnsupported(SinceDungeon plugin) {
        if (!SchedulerCompat.isFolia()) return false;

        InstanceProvider provider = plugin.getInstanceManager() != null ? plugin.getInstanceManager().getProvider() : null;
        return provider == null || !provider.isSharedWorld();
    }

    public static boolean canStart(SinceDungeon plugin, DungeonTemplate template) {
        return template != null && !isWorldCopyUnsupported(plugin);
    }

    public static void warnUnsupportedTemplateLoad(SinceDungeon plugin, String dungeonId, String templateWorld) {
        if (!isWorldCopyUnsupported(plugin)) return;

        String msg = plugin.getLanguageManager().getString(
                "admin.log.folia_template_world_blocked",
                "[Instancing] Dungeon '<dungeon>' uses template-world '<world>', but Core world-copy dungeons cannot run on Folia. Use Premium SCHEMATIC shared-world mode with a preloaded shared world."
        );
        plugin.getLogger().warning(msg
                .replace("<dungeon>", dungeonId)
                .replace("<world>", templateWorld == null ? "unknown" : templateWorld));
    }

    public static void notifyJoinBlocked(SinceDungeon plugin, Player player, DungeonTemplate template) {
        if (player == null) return;

        String msg = plugin.getLanguageManager().getString(
                "error.folia_world_mode_unsupported",
                "&cThis server cannot start world-copy dungeons on Folia. Ask an admin to use Premium SCHEMATIC shared-world mode with a preloaded shared world."
        );
        player.sendMessage(ColorUtils.parseWithPrefix(msg));
        warnUnsupportedTemplateLoad(plugin, template != null ? template.id() : "unknown", template != null ? template.templateWorld() : "unknown");
    }
}
