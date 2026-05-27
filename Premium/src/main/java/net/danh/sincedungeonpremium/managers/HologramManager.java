package net.danh.sincedungeonpremium.managers;

import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.managers.TopManager.TopCategory;
import net.danh.sinceDungeon.managers.TopManager.TopEntry;
import net.danh.sinceDungeon.utils.ColorUtils;
import net.danh.sinceDungeon.utils.SchedulerCompat;
import net.danh.sincedungeonpremium.SinceDungeonPremium;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Premium-Exclusive Manager: native Display Entity leaderboards.
 */
public class HologramManager {

    private static final String HOLOGRAM_TAG = "sincedungeon_hologram";
    private static final double DEFAULT_LINE_SPACING = 0.28D;

    private final SinceDungeonPremium plugin;
    private final Map<String, List<UUID>> activeDisplays = new ConcurrentHashMap<>();
    private SchedulerCompat.TaskHandle updateTask;

    public HologramManager(SinceDungeonPremium plugin) {
        this.plugin = plugin;
    }

    public void createHologramInGame(Player player, String mapId, String category) {
        String holoId = "holo_" + System.currentTimeMillis();
        Location loc = player.getLocation();
        String locStr = serializeLocation(loc);

        plugin.getFileManager().getConfig().set("hologram-leaderboard.locations." + holoId + ".map", mapId);
        plugin.getFileManager().getConfig().set("hologram-leaderboard.locations." + holoId + ".category", category.toUpperCase());
        plugin.getFileManager().getConfig().set("hologram-leaderboard.locations." + holoId + ".location", locStr);

        try {
            plugin.getFileManager().getConfig().save(new File(plugin.getDataFolder(), "config.yml"));
            plugin.getFileManager().sendMessage(player, "admin.holo_created");
            updateAllHolograms();
        } catch (IOException e) {
            plugin.getLogger().warning(plugin.getFileManager().getMessageRaw("admin.holo_config_save_error"));
            plugin.getFileManager().sendMessage(player, "admin.holo_save_fail");
        }
    }

    public void moveHologramInGame(Player player, String holoId) {
        ConfigurationSection holos = plugin.getFileManager().getConfig().getConfigurationSection("hologram-leaderboard.locations");
        if (holos != null && holos.contains(holoId)) {
            Location oldLocation = parseLocation(holos.getString(holoId + ".location"));
            if (oldLocation != null) {
                SchedulerCompat.runAtLocation(plugin, oldLocation, () -> clearHologramEntities(holoId, oldLocation));
            } else {
                removeTrackedDisplays(holoId);
            }

            Location loc = player.getLocation();
            plugin.getFileManager().getConfig().set("hologram-leaderboard.locations." + holoId + ".location", serializeLocation(loc));
            try {
                plugin.getFileManager().getConfig().save(new File(plugin.getDataFolder(), "config.yml"));
                plugin.getFileManager().sendMessage(player, "admin.holo_moved");
                updateAllHolograms();
            } catch (IOException e) {
                plugin.getLogger().warning(plugin.getFileManager().getMessageRaw("admin.holo_config_save_error"));
                plugin.getFileManager().sendMessage(player, "admin.holo_save_fail");
            }
        } else {
            plugin.getFileManager().sendMessage(player, "admin.holo_not_found");
        }
    }

    public void deleteHologramInGame(Player player, String holoId) {
        ConfigurationSection holos = plugin.getFileManager().getConfig().getConfigurationSection("hologram-leaderboard.locations");
        if (holos != null && holos.contains(holoId)) {
            Location location = parseLocation(holos.getString(holoId + ".location"));
            plugin.getFileManager().getConfig().set("hologram-leaderboard.locations." + holoId, null);
            try {
                plugin.getFileManager().getConfig().save(new File(plugin.getDataFolder(), "config.yml"));
                if (location != null) {
                    SchedulerCompat.runAtLocation(plugin, location, () -> clearHologramEntities(holoId, location));
                } else {
                    removeTrackedDisplays(holoId);
                }
                plugin.getFileManager().sendMessage(player, "admin.holo_deleted");
            } catch (IOException e) {
                plugin.getLogger().warning(plugin.getFileManager().getMessageRaw("admin.holo_config_save_error"));
                plugin.getFileManager().sendMessage(player, "admin.holo_save_fail");
            }
        } else {
            plugin.getFileManager().sendMessage(player, "admin.holo_not_found");
        }
    }

    public void startUpdater() {
        int updateInterval = Math.max(20, plugin.getFileManager().getConfig().getInt("hologram-leaderboard.update-interval-seconds", 300) * 20);

        if (updateTask != null && !updateTask.isCancelled()) {
            updateTask.cancel();
        }

        updateTask = SchedulerCompat.runGlobalTimer(plugin, this::updateAllHolograms, 100L, updateInterval);
    }

    public void cleanup() {
        if (updateTask != null && !updateTask.isCancelled()) {
            updateTask.cancel();
        }
        updateTask = null;
        clearAllHolograms();
    }

    public void updateAllHolograms() {
        SchedulerCompat.runGlobal(plugin, () -> {
            ConfigurationSection holos = plugin.getFileManager().getConfig().getConfigurationSection("hologram-leaderboard.locations");
            if (holos == null) return;

            List<HologramSnapshot> snapshots = new ArrayList<>();
            for (String key : holos.getKeys(false)) {
                String mapId = holos.getString(key + ".map");
                String categoryStr = holos.getString(key + ".category");
                String locStr = holos.getString(key + ".location");

                if (mapId == null || categoryStr == null || locStr == null) continue;

                TopCategory category;
                try {
                    category = TopCategory.valueOf(categoryStr.toUpperCase());
                } catch (Exception e) {
                    continue;
                }

                snapshots.add(new HologramSnapshot(key, mapId, category, locStr));
            }

            SchedulerCompat.runAsync(plugin, () -> {
                SinceDungeon core = SinceDungeon.getPlugin();
                if (core == null || core.getTopManager() == null) return;

                for (HologramSnapshot snapshot : snapshots) {
                    List<TopEntry> topEntries = core.getTopManager().getTop(snapshot.mapId(), snapshot.category(), 10);
                    Location location = parseLocation(snapshot.locStr());
                    if (location == null) continue;
                    SchedulerCompat.runAtLocation(plugin, location, () -> renderHologram(snapshot.holoId(), snapshot.mapId(), snapshot.category(), location, topEntries));
                }
            });
        });
    }

    private void renderHologram(String holoId, String mapId, TopCategory category, Location loc, List<TopEntry> topEntries) {
        List<String> lines = buildLines(mapId, category, topEntries);

        clearHologramEntities(holoId, loc);

        double lineSpacing = Math.max(0.1D, plugin.getFileManager().getConfig().getDouble("hologram-leaderboard.line-spacing", DEFAULT_LINE_SPACING));
        List<UUID> spawned = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            Location lineLocation = loc.clone().subtract(0.0D, i * lineSpacing, 0.0D);
            String line = lines.get(i);
            TextDisplay display = lineLocation.getWorld().spawn(lineLocation, TextDisplay.class, entity -> configureTextDisplay(entity, holoId, line));
            spawned.add(display.getUniqueId());
        }
        activeDisplays.put(holoId, spawned);
    }

    private List<String> buildLines(String mapId, TopCategory category, List<TopEntry> topEntries) {
        List<String> lines = new ArrayList<>();
        lines.add(plugin.getFileManager().getMessageRaw("holograms.header"));
        lines.add(plugin.getFileManager().getMessageRaw("holograms.map_line").replace("<map>", mapId));

        String catName = switch (category) {
            case FASTEST_TIME -> SinceDungeon.getPlugin().getLanguageManager().getString("top.category_time", "Solo Fastest Clears");
            case PARTY_FASTEST_TIME -> SinceDungeon.getPlugin().getLanguageManager().getString("top.category_party_time", "Party Fastest Clears");
            case MOST_KILLS -> SinceDungeon.getPlugin().getLanguageManager().getString("top.category_kills", "Most Kills");
            case MOST_CLEARS -> SinceDungeon.getPlugin().getLanguageManager().getString("top.category_clears", "Most Clears");
        };

        lines.add(plugin.getFileManager().getMessageRaw("holograms.category_line").replace("<category>", catName));
        lines.add(" ");

        int rank = 1;
        String format = plugin.getFileManager().getMessageRaw("holograms.format");
        for (TopEntry entry : topEntries) {
            lines.add(format.replace("<rank>", String.valueOf(rank))
                    .replace("<player>", entry.playerName())
                    .replace("<value>", String.valueOf(entry.value())));
            rank++;
        }

        if (topEntries.isEmpty()) {
            lines.add(plugin.getFileManager().getMessageRaw("holograms.empty"));
        }

        return lines;
    }

    private void configureTextDisplay(TextDisplay display, String holoId, String line) {
        display.text(ColorUtils.parse(line == null || line.isBlank() ? " " : line));
        display.setAlignment(TextDisplay.TextAlignment.CENTER);
        display.setBillboard(Display.Billboard.CENTER);
        display.setSeeThrough(true);
        display.setShadowed(true);
        display.setPersistent(false);
        display.setGravity(false);
        display.setViewRange((float) plugin.getFileManager().getConfig().getDouble("hologram-leaderboard.view-range", 64.0D));
        display.addScoreboardTag(HOLOGRAM_TAG);
        display.addScoreboardTag(hologramIdTag(holoId));
    }

    private void clearHologramEntities(String holoId, Location location) {
        removeTrackedDisplays(holoId);
        if (location == null || location.getWorld() == null) return;

        for (Entity entity : location.getWorld().getNearbyEntities(location, 3.0D, 8.0D, 3.0D)) {
            if (entity.getScoreboardTags().contains(HOLOGRAM_TAG) && entity.getScoreboardTags().contains(hologramIdTag(holoId))) {
                entity.remove();
            }
        }
        activeDisplays.remove(holoId);
    }

    private void removeTrackedDisplays(String holoId) {
        List<UUID> tracked = activeDisplays.remove(holoId);
        if (tracked == null) return;

        for (UUID uuid : tracked) {
            Entity entity = Bukkit.getEntity(uuid);
            if (entity != null) {
                SchedulerCompat.runAtEntity(plugin, entity, entity::remove);
            }
        }
    }

    private String serializeLocation(Location loc) {
        return loc.getWorld().getName() + "," + loc.getX() + "," + loc.getY() + "," + loc.getZ();
    }

    private Location parseLocation(String locStr) {
        if (locStr == null) return null;
        String[] parts = locStr.split(",");
        if (parts.length < 4) return null;

        try {
            if (Bukkit.getWorld(parts[0]) == null) return null;
            return new Location(Bukkit.getWorld(parts[0]), Double.parseDouble(parts[1]), Double.parseDouble(parts[2]), Double.parseDouble(parts[3]));
        } catch (NumberFormatException exception) {
            plugin.getLogger().warning(plugin.getFileManager().getMessageRaw("log.invalid_hologram_location").replace("<location>", locStr));
            return null;
        }
    }

    public void clearAllHolograms() {
        ConfigurationSection holos = plugin.getFileManager().getConfig().getConfigurationSection("hologram-leaderboard.locations");
        if (holos != null) {
            for (String key : holos.getKeys(false)) {
                Location location = parseLocation(holos.getString(key + ".location"));
                if (location != null) {
                    SchedulerCompat.runAtLocation(plugin, location, () -> clearHologramEntities(key, location));
                } else {
                    removeTrackedDisplays(key);
                }
            }
        }

        for (String holoId : new ArrayList<>(activeDisplays.keySet())) {
            removeTrackedDisplays(holoId);
        }
    }

    private String hologramIdTag(String holoId) {
        return "sincedungeon_hologram_" + holoId;
    }

    private record HologramSnapshot(String holoId, String mapId, TopCategory category, String locStr) {
    }
}
