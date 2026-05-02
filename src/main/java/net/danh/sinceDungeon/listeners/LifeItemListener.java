package net.danh.sinceDungeon.listeners;

import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.managers.LivesManager;
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
 * Listens for interactions with Life Items.
 * Consumes the item and grants the player additional dungeon lives.
 */
public class LifeItemListener implements Listener {
    private final SinceDungeon plugin;
    private final NamespacedKey lifeKey;

    public LifeItemListener(SinceDungeon plugin) {
        this.plugin = plugin;
        this.lifeKey = new NamespacedKey(plugin, "life_amount");
    }

    /**
     * Handles the interaction event when a player consumes a Life Item.
     * Applies cooldowns to prevent macro abuse and safely updates the player's life count.
     *
     * @param e The player interact event.
     */
    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (!e.getAction().isRightClick()) return;

        Player p = e.getPlayer();
        ItemStack item = e.getItem();
        if (item == null || !item.hasItemMeta()) return;

        if (p.hasCooldown(item.getType())) return;

        ItemMeta meta = item.getItemMeta();
        if (meta.getPersistentDataContainer().has(lifeKey, PersistentDataType.INTEGER)) {
            int amount = meta.getPersistentDataContainer().getOrDefault(lifeKey, PersistentDataType.INTEGER, 0);
            if (amount <= 0) return;

            e.setCancelled(true);
            p.setCooldown(item.getType(), 10);

            LivesManager.PlayerLives lives = plugin.getLivesManager().getLives(p.getUniqueId());
            if (lives == null) return;

            if (lives.getCurrentLives() >= lives.getMaxLives()) {
                p.sendMessage(ColorUtils.parseWithPrefix(plugin.getLanguageManager().getString("lives.max_reached")));
                return;
            }

            item.setAmount(item.getAmount() - 1);
            plugin.getLivesManager().addLives(p.getUniqueId(), amount);

            String msg = plugin.getLanguageManager().getString("lives.item_used")
                    .replace("<amount>", String.valueOf(amount))
                    .replace("<current>", String.valueOf(lives.getCurrentLives()))
                    .replace("<max>", String.valueOf(lives.getMaxLives()));
            p.sendMessage(ColorUtils.parseWithPrefix(msg));

            playConsumeEffects(p);
        }
    }

    private void playConsumeEffects(Player p) {
        ConfigurationSection sec = plugin.getConfigFile().getConfig().getConfigurationSection("lives.life-item");
        if (sec == null) return;

        ConfigurationSection soundSec = sec.getConfigurationSection("consume-sound");
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
                plugin.getLogger().warning("Invalid particle name in config: " + particleName);
            }
        }
    }
}