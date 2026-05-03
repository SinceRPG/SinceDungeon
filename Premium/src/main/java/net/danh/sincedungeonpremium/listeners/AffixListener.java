package net.danh.sincedungeonpremium.listeners;

import net.danh.sinceDungeon.api.SinceDungeonAPI;
import net.danh.sinceDungeon.models.DungeonGame;
import net.danh.sinceDungeon.utils.ColorUtils;
import net.danh.sincedungeonpremium.SinceDungeonPremium;
import org.bukkit.Location;
import org.bukkit.Particle;
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

/**
 * Premium-Exclusive Listener: Mythic+ Affixes
 * Responsibilities:
 * - Detects actions inside dungeons and checks the configuration to determine if affixes are active.
 * - Implements Volcanic (Explosions on mob death) and Vampiric (Mobs heal on hitting players).
 * - Retrieves configurable particle effects to eliminate hardcoded instances.
 */
public class AffixListener implements Listener {

    private final SinceDungeonPremium plugin;

    public AffixListener(SinceDungeonPremium plugin) {
        this.plugin = plugin;
    }

    private boolean hasAffix(DungeonGame game, String affix) {
        if (game == null || game.getTemplate() == null) return false;
        String dungeonId = game.getTemplate().id();
        List<String> activeAffixes = plugin.getFileManager().getConfig().getStringList("affixes." + dungeonId);
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

    /**
     * Handles the Vampiric Affix.
     * Mobs heal a percentage of the damage they deal to players.
     */
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

    /**
     * Handles the Volcanic Affix.
     * Mobs spawn a delayed explosion that damages nearby players upon death.
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onMobDeath(EntityDeathEvent e) {
        LivingEntity entity = e.getEntity();
        if (entity instanceof Player) return;

        DungeonGame game = null;
        for (DungeonGame activeGame : SinceDungeonAPI.get().getAllActiveGames().values()) {
            if (activeGame.getWorld() != null && activeGame.getWorld().equals(entity.getWorld())) {
                game = activeGame;
                break;
            }
        }

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
                    deathLoc.getWorld().playSound(deathLoc, org.bukkit.Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f);

                    for (Player p : deathLoc.getWorld().getPlayers()) {
                        if (!p.isDead() && p.getLocation().distanceSquared(deathLoc) <= (radius * radius)) {
                            p.damage(damage);
                            p.sendMessage(ColorUtils.parseWithPrefix(plugin.getFileManager().getMessageRaw("affixes.volcanic_hit")));
                        }
                    }
                }
            }.runTaskLater(plugin, delayTicks);
        }
    }
}