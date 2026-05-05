package net.danh.sincedungeonpremium.listeners;

import net.danh.sinceDungeon.api.events.DungeonFinishEvent;
import net.danh.sincedungeonpremium.SinceDungeonPremium;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

/**
 * Premium-Exclusive Listener: Discord Webhooks
 * Responsibilities:
 * - Detects successful dungeon completions.
 * - Formats a lightweight JSON payload manually to bypass heavy library dependencies.
 * - Dispatches HTTP POST requests asynchronously to Discord, using the modern URI-to-URL conversion.
 */
public class WebhookListener implements Listener {

    private final SinceDungeonPremium plugin;

    public WebhookListener(SinceDungeonPremium plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDungeonFinish(DungeonFinishEvent e) {
        boolean enabled = plugin.getFileManager().getConfig().getBoolean("webhooks.enabled", false);
        if (!enabled) return;

        String webhookUrl = plugin.getFileManager().getConfig().getString("webhooks.url");
        if (webhookUrl == null || webhookUrl.isEmpty()) return;

        String dungeonName = e.getGame().getTemplate().id();
        int time = e.getCompletionTimeSeconds();

        StringBuilder playersBuilder = new StringBuilder();
        for (Player p : e.getGame().getParticipants()) {
            if (p.isOnline()) {
                playersBuilder.append(p.getName()).append(", ");
            }
        }
        String fallback = plugin.getFileManager().getConfig().getString("webhooks.unknown_player", "Unknown");
        String players = playersBuilder.length() > 0 ? playersBuilder.substring(0, playersBuilder.length() - 2) : fallback;

        String title = plugin.getFileManager().getConfig().getString("webhooks.embed-title", "Dungeon Cleared!");
        String color = plugin.getFileManager().getConfig().getString("webhooks.embed-color", "5814783");

        String description = plugin.getFileManager().getConfig().getString("webhooks.embed-description", "Map: **%dungeon%**\\nTime: **%time%s**\\nPlayers: **%players%**")
                .replace("%dungeon%", dungeonName)
                .replace("%time%", String.valueOf(time))
                .replace("%players%", players);

        sendWebhookAsync(webhookUrl, title, description, color);
    }

    private void sendWebhookAsync(String urlString, String title, String description, String colorDec) {
        CompletableFuture.runAsync(() -> {
            try {
                // Replaces deprecated new URL(String) from Java 20
                URL url = URI.create(urlString).toURL();
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json; utf-8");
                connection.setRequestProperty("User-Agent", "SinceDungeonPremium");
                connection.setDoOutput(true);

                String jsonPayload = String.format(
                        "{\"embeds\":[{\"title\":\"%s\",\"description\":\"%s\",\"color\":%s}]}",
                        escapeJson(title),
                        escapeJson(description),
                        colorDec
                );

                try (OutputStream os = connection.getOutputStream()) {
                    byte[] input = jsonPayload.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                int responseCode = connection.getResponseCode();
                if (responseCode < 200 || responseCode >= 300) {
                    plugin.getLogger().warning("Discord Webhook failed with HTTP code: " + responseCode);
                }

            } catch (Exception e) {
                plugin.getLogger().warning("Failed to send Discord Webhook: " + e.getMessage());
            }
        });
    }

    private String escapeJson(String input) {
        if (input == null) return "";
        return input.replace("\"", "\\\"").replace("\n", "\\n");
    }
}