package net.danh.sincedungeonpremium.managers;

import net.danh.sinceDungeon.api.events.DungeonRewardClaimEvent;
import net.danh.sinceDungeon.guis.reward.RewardSession;
import net.danh.sinceDungeon.guis.reward.RewardSessionManager;
import net.danh.sinceDungeon.models.DungeonReward;
import net.danh.sinceDungeon.utils.ColorUtils;
import net.danh.sinceDungeon.utils.ItemBuilder;
import net.danh.sinceDungeon.utils.SoundUtils;
import net.danh.sincedungeonpremium.SinceDungeonPremium;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.Random;

/**
 * Premium-Exclusive Manager: Interactive Roulette Spin
 * Responsibilities:
 * - Generates a 27-slot physical scrolling menu utilizing Minecraft native APIs.
 * - Reads sounds dynamically from config to keep the plugin highly configurable.
 * - Forces fairness rolls identically to the Core implementation but visually impressive.
 */
public class RouletteManager {

    private final SinceDungeonPremium plugin;
    private final Random random = new Random();

    public RouletteManager(SinceDungeonPremium plugin) {
        this.plugin = plugin;
    }

    public void openRoulette(Player player, RewardSession session) {
        if (session == null || session.getChestCount() <= 0) {
            player.sendMessage(ColorUtils.parseWithPrefix(plugin.getFileManager().getMessageRaw("rewards.no_spins_left")));
            RewardSessionManager.removeSession(player);
            return;
        }

        String title = plugin.getFileManager().getConfig().getString("roulette.title", "&6&lReward Spin");
        Inventory inv = Bukkit.createInventory(null, 27, ColorUtils.parse(title));

        ItemStack border = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        for (int i = 0; i < 9; i++) inv.setItem(i, border);
        for (int i = 18; i < 27; i++) inv.setItem(i, border);

        inv.setItem(4, new ItemStack(Material.HOPPER));
        inv.setItem(22, new ItemStack(Material.HOPPER));

        player.openInventory(inv);

        session.decreaseChestCount();

        List<DungeonReward> pool = session.getTemplate().rewardPool();
        if (pool == null || pool.isEmpty()) return;

        startSpinAnimation(player, inv, pool, session);
    }

    private void startSpinAnimation(Player player, Inventory inv, List<DungeonReward> pool, RewardSession session) {
        new BukkitRunnable() {
            int ticks = 0;
            int delay = 1;
            int maxTicks = 60;

            @Override
            public void run() {
                if (!player.isOnline() || !player.getOpenInventory().getTopInventory().equals(inv)) {
                    this.cancel();
                    return;
                }

                for (int i = 16; i > 9; i--) {
                    inv.setItem(i, inv.getItem(i - 1));
                }

                DungeonReward nextReward = getRandomReward(pool);
                ItemStack displayItem = buildDisplayItem(nextReward);
                inv.setItem(9, displayItem);

                String soundStr = plugin.getFileManager().getConfig().getString("sounds.roulette_tick", "block.note_block.hat");
                Sound soundTick = SoundUtils.getSound(soundStr);
                if (soundTick != null) {
                    player.playSound(player.getLocation(), soundTick, 1f, 1f);
                }

                ticks++;

                if (ticks > 40) delay = 2;
                if (ticks > 50) delay = 4;
                if (ticks > 55) delay = 8;

                if (ticks >= maxTicks) {
                    this.cancel();
                    finishSpin(player, inv, session, pool);
                } else if (ticks % delay != 0) {
                    // Loop skip
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void finishSpin(Player player, Inventory inv, RewardSession session, List<DungeonReward> pool) {
        String finishSoundStr = plugin.getFileManager().getConfig().getString("sounds.roulette_finish", "entity.player.levelup");
        Sound finishSound = SoundUtils.getSound(finishSoundStr);
        if (finishSound != null) {
            player.playSound(player.getLocation(), finishSound, 1f, 1f);
        }

        DungeonReward wonReward = getRandomReward(pool);
        ItemStack wonDisplay = buildDisplayItem(wonReward);
        inv.setItem(13, wonDisplay);

        Bukkit.getPluginManager().callEvent(new DungeonRewardClaimEvent(player, wonReward));

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                if (session.getChestCount() > 0) {
                    openRoulette(player, session);
                } else {
                    player.closeInventory();
                }
            }
        }, 40L);
    }

    private DungeonReward getRandomReward(List<DungeonReward> pool) {
        double totalWeight = pool.stream().mapToDouble(DungeonReward::chance).sum();
        double roll = random.nextDouble() * totalWeight;
        double currentWeight = 0.0;
        for (DungeonReward reward : pool) {
            currentWeight += reward.chance();
            if (roll <= currentWeight) return reward;
        }
        return pool.get(0);
    }

    private ItemStack buildDisplayItem(DungeonReward reward) {
        ItemStack item = ItemBuilder.parseDynamicItem(reward.value());
        if (item == null) item = new ItemStack(Material.PAPER);

        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        if (meta != null && reward.displayName() != null) {
            meta.displayName(ColorUtils.parse("<!i>" + reward.displayName()));
            item.setItemMeta(meta);
        }
        return item;
    }
}