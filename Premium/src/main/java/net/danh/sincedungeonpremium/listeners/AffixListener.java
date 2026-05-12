package net.danh.sincedungeonpremium.listeners;

import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.api.SinceDungeonAPI;
import net.danh.sinceDungeon.api.events.DungeonEndEvent;
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
 * Handles Mythic+ style Dungeon Affixes.
 * OPTIMIZATION: Implements Cache-Cleaning to prevent long-term memory leaks.
 */
public class AffixListener implements Listener {

    private final SinceDungeonPremium plugin;
    private final Map<String, List<String>> affixCache = new ConcurrentHashMap<>();

    public AffixListener(SinceDungeonPremium plugin) {
        this.plugin = plugin;
    }

    /**
     * CLEANUP: Listens for Dungeon End events to purge the memory cache.
     * Prevents the RAM usage from growing indefinitely.
     */
    @EventHandler
    public void onDungeonEnd(DungeonEndEvent e) {
        if (e.getGame().getTemplate() != null) {
            affixCache.remove(e.getGame().getTemplate().id());
        }
    }

    private boolean hasAffix(DungeonGame game, String affix) {
        if (game == null || game.getTemplate() == null) return false;
        String dungeonId = game.getTemplate().id();

        List<String> activeAffixes = affixCache.computeIfAbsent(dungeonId, k ->
                plugin.getFileManager().getConfig().getStringList("affixes." + k)
        );

        return activeAffixes.contains(affix.toUpperCase());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMobDamagePlayer(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player player)) return;
        if (!(e.getDamager() instanceof LivingEntity damager)) return;

        DungeonGame game = SinceDungeonAPI.get().getGame(player);
        if (game != null && hasAffix(game, "VAMPIRIC")) {
            double healPercentage = plugin.getFileManager().getConfig().getDouble("affixes-settings.vampiric.heal-percentage", 0.5);
            double healAmount = e.getFinalDamage() * healPercentage;

            if (damager.getAttribute(Attribute.MAX_HEALTH) != null) {
                double maxHealth = damager.getAttribute(Attribute.MAX_HEALTH).getValue();
                damager.setHealth(Math.min(maxHealth, damager.getHealth() + healAmount));
            }

            try {
                String pStr = plugin.getFileManager().getConfig().getString("particles.affix_vampiric", "HEART");
                damager.getWorld().spawnParticle(Particle.valueOf(pStr.toUpperCase()), damager.getLocation().add(0, 1, 0), 3, 0.5, 0.5, 0.5, 0);
            } catch (Exception ignored) {
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onMobDeath(EntityDeathEvent e) {
        LivingEntity entity = e.getEntity();
        if (entity instanceof Player) return;

        DungeonGame game = SinceDungeonAPI.get().getManager().getGameByEntity(entity);

        if (game != null && hasAffix(game, "VOLCANIC")) {
            final Location deathLoc = entity.getLocation().clone();
            final double damage = plugin.getFileManager().getConfig().getDouble("affixes-settings.volcanic.damage", 10.0);
            final double radius = plugin.getFileManager().getConfig().getDouble("affixes-settings.volcanic.radius", 3.0);
            final int delayTicks = plugin.getFileManager().getConfig().getInt("affixes-settings.volcanic.delay-ticks", 40);

            try {
                String warnP = plugin.getFileManager().getConfig().getString("particles.affix_volcanic_warn", "FLAME");
                entity.getWorld().spawnParticle(Particle.valueOf(warnP.toUpperCase()), deathLoc, 10, 0.5, 0.1, 0.5, 0.05);
            } catch (Exception ignored) {
            }

            new BukkitRunnable() {
                @Override
                public void run() {
                    // CRITICAL FIX: Ensure world still exists before processing explosion
                    if (deathLoc.getWorld() == null || !deathLoc.getChunk().isLoaded()) return;

                    try {
                        String boomP = plugin.getFileManager().getConfig().getString("particles.affix_volcanic_boom", "EXPLOSION");
                        deathLoc.getWorld().spawnParticle(Particle.valueOf(boomP.toUpperCase()), deathLoc, 2);
                        deathLoc.getWorld().playSound(deathLoc, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f);
                    } catch (Exception ignored) {
                    }

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
