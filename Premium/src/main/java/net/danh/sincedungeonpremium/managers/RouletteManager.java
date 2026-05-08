package net.danh.sincedungeonpremium.managers;

import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.api.SinceDungeonAPI;
import net.danh.sinceDungeon.api.events.DungeonRewardClaimEvent;
import net.danh.sinceDungeon.api.interfaces.RewardProcessor;
import net.danh.sinceDungeon.guis.reward.RewardGUI;
import net.danh.sinceDungeon.guis.reward.RewardSession;
import net.danh.sinceDungeon.guis.reward.RewardSessionManager;
import net.danh.sinceDungeon.models.DungeonReward;
import net.danh.sinceDungeon.utils.ColorUtils;
import net.danh.sinceDungeon.utils.ItemBuilder;
import net.danh.sinceDungeon.utils.SoundUtils;
import net.danh.sincedungeonpremium.SinceDungeonPremium;
import net.danh.sincedungeonpremium.guis.RouletteHolder;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Premium-Exclusive Manager: Interactive Roulette Spin
 * Responsibilities:
 * - Generates a 27-slot physical scrolling menu utilizing Minecraft native APIs.
 * - Preserves ALL metadata formatting (Names, Lore, NBT) when displaying preview items.
 * - Securely pushes execution back into Core systems to process the won rewards.
 * - Optimized: Avoids IO lookups during high-frequency runnable loops.
 */
public class RouletteManager implements Listener {

    private final SinceDungeonPremium plugin;
    private final Random random = new Random();

    public RouletteManager(SinceDungeonPremium plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void openRoulette(Player player, RewardSession session) {
        if (session == null || session.getChestCount() <= 0) {
            player.sendMessage(ColorUtils.parseWithPrefix(plugin.getFileManager().getMessageRaw("rewards.no_spins_left")));
            RewardSessionManager.removeSession(player);
            return;
        }

        List<DungeonReward> pool = session.getTemplate().rewardPool();
        if (pool == null || pool.isEmpty()) return;

        session.decreaseChestCount();
        DungeonReward wonReward = getRandomReward(pool); // Pre-calculate winning reward to secure against closing

        String title = plugin.getFileManager().getConfig().getString("roulette.title", "&6&lReward Spin");
        Inventory inv = Bukkit.createInventory(new RouletteHolder(session, wonReward), 27, ColorUtils.parse(title));

        ItemStack border = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        for (int i = 0; i < 9; i++) inv.setItem(i, border);
        for (int i = 18; i < 27; i++) inv.setItem(i, border);

        inv.setItem(4, new ItemStack(Material.HOPPER));
        inv.setItem(22, new ItemStack(Material.HOPPER));

        player.openInventory(inv);

        startSpinAnimation(player, inv, pool, session, wonReward);
    }

    private void startSpinAnimation(Player player, Inventory inv, List<DungeonReward> pool, RewardSession session, DungeonReward wonReward) {
        // JIT Optimization: Pre-fetch sound data to avoid YAML read loops during animation ticks
        String soundStr = plugin.getFileManager().getConfig().getString("sounds.roulette_tick", "block.note_block.hat");
        Sound soundTick = SoundUtils.getSound(soundStr);

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

                if (soundTick != null) {
                    player.playSound(player.getLocation(), soundTick, 1f, 1f);
                }

                ticks++;

                if (ticks > 40) delay = 2;
                if (ticks > 50) delay = 4;
                if (ticks > 55) delay = 8;

                if (ticks >= maxTicks) {
                    this.cancel();
                    finishSpin(player, inv, session, wonReward);
                } else if (ticks % delay != 0) {
                    // Loop skip
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void finishSpin(Player player, Inventory inv, RewardSession session, DungeonReward wonReward) {
        if (!(inv.getHolder() instanceof RouletteHolder holder)) return;

        holder.setClaimed(true); // Mark as claimed internally so the onClose event doesn't duplicate

        String finishSoundStr = plugin.getFileManager().getConfig().getString("sounds.roulette_finish", "entity.player.levelup");
        Sound finishSound = SoundUtils.getSound(finishSoundStr);
        if (finishSound != null) {
            player.playSound(player.getLocation(), finishSound, 1f, 1f);
        }

        ItemStack wonDisplay = buildDisplayItem(wonReward);
        inv.setItem(13, wonDisplay);

        grantReward(player, wonReward);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                if (session.getChestCount() > 0) {
                    // Activate state lock to prevent InventoryCloseEvent from triggering auto-claim
                    session.setSwitchingPage(true);
                    openRoulette(player, session);
                    session.setSwitchingPage(false);
                } else {
                    player.closeInventory();
                }
            }
        }, 40L);
    }

    private void grantReward(Player player, DungeonReward wonReward) {
        DungeonRewardClaimEvent claimEvent = new DungeonRewardClaimEvent(player, wonReward);
        Bukkit.getPluginManager().callEvent(claimEvent);

        if (!claimEvent.isCancelled()) {
            RewardProcessor processor = SinceDungeonAPI.get().getManager().getRewardProcessor(wonReward.type());
            if (processor != null) {
                processor.giveReward(player, wonReward.value(), wonReward.displayName());
            }
        }
    }

    @EventHandler
    public void onRouletteClose(InventoryCloseEvent e) {
        if (e.getInventory().getHolder() instanceof RouletteHolder holder) {
            Player p = (Player) e.getPlayer();
            RewardSession session = holder.getSession();

            // If the plugin is actively transitioning to the next spin, abort processing closure
            if (session != null && session.isSwitchingPage()) {
                return;
            }

            // If they closed before the spin finished, give the pending reward immediately!
            if (!holder.isClaimed() && holder.getPendingReward() != null) {
                holder.setClaimed(true);
                grantReward(p, holder.getPendingReward());
            }

            // Securely force-claim the rest of the chests so no data is lost
            if (session != null && session.getChestCount() > 0) {
                new RewardGUI(SinceDungeon.getPlugin()).forceClaimAll(p, session);
            }

            RewardSessionManager.removeSession(p);
        }
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

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String displayName = reward.displayName() != null && !reward.displayName().isEmpty() ? reward.displayName() : ColorUtils.formatEnumName(item.getType().name());
            meta.displayName(ColorUtils.parse("<!i>" + displayName));

            if (reward.lore() != null && !reward.lore().isEmpty()) {
                List<Component> lore = new ArrayList<>();
                for (String line : reward.lore()) {
                    lore.add(ColorUtils.parse("<!i>" + line));
                }
                meta.lore(lore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }
}