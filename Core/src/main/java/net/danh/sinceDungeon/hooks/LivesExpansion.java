package net.danh.sinceDungeon.hooks;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.managers.LivesManager;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Exposes SinceDungeon data to PlaceholderAPI.
 * Handles parsing for player lives, regeneration timers, and dungeon cooldowns.
 */
public class LivesExpansion extends PlaceholderExpansion {

    private final SinceDungeon plugin;

    public LivesExpansion(SinceDungeon plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "sincedungeon";
    }

    @Override
    public @NotNull String getAuthor() {
        return String.join(", ", plugin.getPluginMeta().getAuthors());
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Nullable
    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) return "";

        // --- Handle Cooldown Placeholders ---
        if (params.toLowerCase().startsWith("cooldown_")) {
            String map = params.substring(9);
            if (plugin.getCooldownManager().isOnCooldown(player.getUniqueId(), map)) {
                return plugin.getCooldownManager().getRemainingTimeFormatted(player.getUniqueId(), map);
            } else {
                return plugin.getLanguageManager().getString("papi.cooldown_ready", "Ready");
            }
        }

        // --- Handle Lives Placeholders ---
        LivesManager.PlayerLives lives = plugin.getLivesManager().getLives(player.getUniqueId());

        if (lives == null) {
            if (params.equalsIgnoreCase("lives") || params.equalsIgnoreCase("max_lives")) return "0";
            return "";
        }

        int customInterval = lives.getCustomRegenInterval() != -1 ? lives.getCustomRegenInterval() : plugin.getConfigFile().getInt("lives.regen-interval-seconds", 3600);
        int customAmount = lives.getCustomRegenAmount() != -1 ? lives.getCustomRegenAmount() : plugin.getConfigFile().getInt("lives.regen-amount", 1);

        switch (params.toLowerCase()) {
            case "lives":
                return String.valueOf(lives.getCurrentLives());

            case "max_lives":
                return String.valueOf(lives.getMaxLives());

            case "lives_regen_amount":
                return String.valueOf(customAmount);

            case "lives_regen_interval":
                return String.valueOf(customInterval);

            case "lives_time_to_regen":
                if (lives.getCurrentLives() >= lives.getMaxLives()) {
                    return plugin.getLanguageManager().getString("papi.time_full", "Full");
                }

                if (customInterval <= 0 || customAmount <= 0) {
                    return plugin.getLanguageManager().getString("papi.time_never", "Never");
                }

                long intervalMillis = customInterval * 1000L;
                long now = System.currentTimeMillis();
                long diff = now - lives.getLastRegen();

                long remainingMillis = intervalMillis - (diff % intervalMillis);
                long remainingSecs = remainingMillis / 1000L;

                long h = remainingSecs / 3600;
                long m = (remainingSecs % 3600) / 60;
                long s = remainingSecs % 60;

                if (h > 0) {
                    return String.format("%02d:%02d:%02d", h, m, s);
                } else {
                    return String.format("%02d:%02d", m, s);
                }
        }

        return null;
    }
}
