package net.danh.sinceDungeon.listeners;

import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.utils.ColorUtils;
import net.danh.sinceDungeon.utils.SoundUtils;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Locale;

/**
 * Listens for interactions with specialized Cooldown manipulation items.
 * Processes reductions and full resets globally across all dungeon maps.
 */
public class CooldownItemListener implements Listener {
    private final SinceDungeon plugin;
    private final NamespacedKey resetKey;
    private final NamespacedKey reduceKey;

    public CooldownItemListener(SinceDungeon plugin) {
        this.plugin = plugin;
        this.resetKey = new NamespacedKey(plugin, "cooldown_reset");
        this.reduceKey = new NamespacedKey(plugin, "cooldown_reduce");
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (!e.getAction().isRightClick()) return;

        Player p = e.getPlayer();
        ItemStack item = e.getItem();
        if (item == null || !item.hasItemMeta()) return;

        if (p.hasCooldown(item.getType())) return;

        ItemMeta meta = item.getItemMeta();

        // Handle Cooldown RESET Item
        if (meta.getPersistentDataContainer().has(resetKey, PersistentDataType.BYTE)) {
            e.setCancelled(true);
            p.setCooldown(item.getType(), 10);

            if (!plugin.getCooldownManager().hasAnyCooldown(p.getUniqueId())) {
                p.sendMessage(ColorUtils.parseWithPrefix(plugin.getLanguageManager().getString("cooldown.no_active_cooldowns")));
                return;
            }

            item.setAmount(item.getAmount() - 1);
            plugin.getCooldownManager().resetAllCooldowns(p.getUniqueId());

            String msg = plugin.getLanguageManager().getString("cooldown.item_reset_used");
            p.sendMessage(ColorUtils.parseWithPrefix(msg));

            playConsumeEffects(p, "items.cooldown_reset");
            return;
        }

        // Handle Cooldown REDUCE Item
        if (meta.getPersistentDataContainer().has(reduceKey, PersistentDataType.INTEGER)) {
            e.setCancelled(true);
            p.setCooldown(item.getType(), 10);

            if (!plugin.getCooldownManager().hasAnyCooldown(p.getUniqueId())) {
                p.sendMessage(ColorUtils.parseWithPrefix(plugin.getLanguageManager().getString("cooldown.no_active_cooldowns")));
                return;
            }

            int secondsToReduce = meta.getPersistentDataContainer().getOrDefault(reduceKey, PersistentDataType.INTEGER, 0);
            if (secondsToReduce <= 0) return;

            item.setAmount(item.getAmount() - 1);
            plugin.getCooldownManager().reduceAllCooldowns(p.getUniqueId(), secondsToReduce);

            String msg = plugin.getLanguageManager().getString("cooldown.item_reduce_used").replace("<time>", String.valueOf(secondsToReduce));
            p.sendMessage(ColorUtils.parseWithPrefix(msg));

            playConsumeEffects(p, "items.cooldown_reduce");
        }
    }

    /**
     * Executes the visual and audio feedback upon successfully consuming an item.
     * Follows the standard defined in the configuration.
     *
     * @param p    The player consuming the item.
     * @param path The config path mapping to the item settings.
     */
    private void playConsumeEffects(Player p, String path) {
        ConfigurationSection sec = plugin.getConfigFile().getSection(path);
        if (sec == null) return;

        ConfigurationSection soundSec = sec.getConfigurationSection("consume-sound");
        ConfigurationSection fallbackSec = plugin.getConfigFile().getSection("items.life_crystal");
        if (soundSec == null && fallbackSec != null) {
            soundSec = fallbackSec.getConfigurationSection("consume-sound");
        }

        if (soundSec != null && soundSec.getBoolean("enabled", true)) {
            String soundName = soundSec.getString("sound", "ENTITY_PLAYER_LEVELUP");
            float volume = (float) soundSec.getDouble("volume", 1.0);
            float pitch = (float) soundSec.getDouble("pitch", 0.5);

            Sound sound = SoundUtils.getSound(soundName);
            if (sound != null) {
                p.playSound(p.getLocation(), sound, volume, pitch);
            }
        }

        ConfigurationSection particleSec = sec.getConfigurationSection("consume-particle");
        if (particleSec == null && fallbackSec != null) {
            particleSec = fallbackSec.getConfigurationSection("consume-particle");
        }

        if (particleSec != null && particleSec.getBoolean("enabled", true)) {
            String particleName = particleSec.getString("particle", "TOTEM_OF_UNDYING");
            int amount = particleSec.getInt("amount", 30);
            double offsetX = particleSec.getDouble("offset-x", 0.5);
            double offsetY = particleSec.getDouble("offset-y", 1.0);
            double offsetZ = particleSec.getDouble("offset-z", 0.5);
            double speed = particleSec.getDouble("speed", 0.1);

            try {
                Particle particle = Particle.valueOf(particleName.toUpperCase(Locale.ROOT));
                p.getWorld().spawnParticle(particle, p.getLocation().add(0, 1.0, 0), amount, offsetX, offsetY, offsetZ, speed);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }
}