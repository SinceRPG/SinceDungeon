package net.danh.sincedungeonpremium.listeners;

import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.api.SinceDungeonAPI;
import net.danh.sinceDungeon.models.DungeonGame;
import net.danh.sinceDungeon.utils.ColorUtils;
import net.danh.sincedungeonpremium.SinceDungeonPremium;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Executes dynamic difficulty modifiers (Affixes) across all active Dungeon Maps.
 * Optimized: Aggressively caches active affixes to memory to prevent massive File I/O YAML spikes on every hit event.
 */
public class AffixListener implements Listener {

    private final SinceDungeonPremium plugin;
    private final Map<String, List<String>> affixCache = new ConcurrentHashMap<>();

    public AffixListener(SinceDungeonPremium plugin) {
        this.plugin = plugin;
    }

    public void clearCache() {
        affixCache.clear();
    }

    /**
     * Checks if a dungeon game currently has an active affix.
     * Computes the list into a localized hashmap to preserve ticks during heavy combat.
     */
    private boolean hasAffix(DungeonGame game, String affix) {
        if (game == null || game.getTemplate() == null) return false;
        String dungeonId = game.getTemplate().id();

        List<String> activeAffixes = affixCache.computeIfAbsent(dungeonId, k ->
                plugin.getFileManager().getConfig().getStringList("affixes." + k)
        );

        return activeAffixes.contains(affix.toUpperCase());
    }

    private Particle getParticle(String path, Particle fallback) {
        try {
            String pStr = plugin.getFileManager().getConfig().getString(path);
            if (pStr != null) {
                return Particle.valueOf(pStr.toUpperCase());
            }
        } catch (IllegalArgumentException ignored) {
        }
        return fallback;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMobDamagePlayer(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player player)) return;
        if (!(e.getDamager() instanceof LivingEntity damager)) return;

        DungeonGame game = SinceDungeonAPI.get().getGame(player);
        if (game != null && hasAffix(game, "VAMPIRIC")) {
            double healPercentage = plugin.getFileManager().getConfig().getDouble("affixes-settings.vampiric.heal-percentage", 0.5);
            double healAmount = e.getFinalDamage() * healPercentage;

            double maxHealth = damager.getAttribute(Attribute.MAX_HEALTH).getValue();
            damager.setHealth(Math.min(maxHealth, damager.getHealth() + healAmount));

            Particle particle = getParticle("particles.affix_vampiric", Particle.HEART);
            damager.getWorld().spawnParticle(particle, damager.getLocation().add(0, 1, 0), 3, 0.5, 0.5, 0.5, 0);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onMobDeath(EntityDeathEvent e) {
        LivingEntity entity = e.getEntity();
        if (entity instanceof Player) return;

        // [Performance Fix] O(1) Fast lookup via API Manager
        DungeonGame game = SinceDungeonAPI.get().getManager().getGameByWorld(entity.getWorld().getName());

        if (game != null && hasAffix(game, "VOLCANIC")) {
            final Location deathLoc = entity.getLocation().clone();
            final double damage = plugin.getFileManager().getConfig().getDouble("affixes-settings.volcanic.damage", 10.0);
            final double radius = plugin.getFileManager().getConfig().getDouble("affixes-settings.volcanic.radius", 3.0);
            final int delayTicks = plugin.getFileManager().getConfig().getInt("affixes-settings.volcanic.delay-ticks", 40);

            Particle warnParticle = getParticle("particles.affix_volcanic_warn", Particle.FLAME);
            entity.getWorld().spawnParticle(warnParticle, deathLoc, 10, 0.5, 0.1, 0.5, 0.05);

            new BukkitRunnable() {
                @Override
                public void run() {
                    if (deathLoc.getWorld() == null) return;

                    Particle boomParticle = getParticle("particles.affix_volcanic_boom", Particle.EXPLOSION);
                    deathLoc.getWorld().spawnParticle(boomParticle, deathLoc, 2);
                    deathLoc.getWorld().playSound(deathLoc, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f);

                    for (Player p : deathLoc.getWorld().getPlayers()) {
                        if (!p.isDead() && p.getLocation().distanceSquared(deathLoc) <= (radius * radius)) {
                            p.damage(damage);
                            String msg = SinceDungeon.getPlugin().getLanguageManager().getString("action.affix_volcanic_hit", "&cYou were burned by a Volcanic explosion!");
                            p.sendMessage(ColorUtils.parseWithPrefix(msg));
                        }
                    }
                }
            }.runTaskLater(plugin, delayTicks);
        }
    }
}