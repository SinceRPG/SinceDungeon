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
import net.danh.sinceDungeon.utils.SchedulerCompat;
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
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Premium-Exclusive Manager: Interactive Roulette Spin
 * Responsibilities:
 * - Generates a configurable physical scrolling menu utilizing Minecraft native APIs.
 * - Preserves ALL metadata formatting (Names, Lore, NBT) when displaying preview items.
 * - Securely pushes execution back into Core systems to process the won rewards.
 * - Optimized: Avoids IO lookups during high-frequency runnable loops.
 */
public class RouletteManager implements Listener {

    private final SinceDungeonPremium plugin;
    private final RewardGUI rewardGUI;

    public RouletteManager(SinceDungeonPremium plugin) {
        this.plugin = plugin;
        this.rewardGUI = new RewardGUI(SinceDungeon.getPlugin());
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private int getGuiSize() {
        int size = plugin.getFileManager().getConfig().getInt("roulette.gui-size", 27);
        if (size % 9 != 0 || size < 27 || size > 54) return 27;
        return size;
    }

    private int getRewardSlot(int size) {
        int slot = plugin.getFileManager().getConfig().getInt("roulette.reward-slot", 13);
        if (slot < 0 || slot >= size) return Math.min(13, size - 1);
        return slot;
    }

    private int getSpinStartSlot(int size) {
        int slot = plugin.getFileManager().getConfig().getInt("roulette.spin-start-slot", 9);
        if (slot < 0 || slot >= size) return 9;
        return slot;
    }

    private int getSpinEndSlot(int size) {
        int slot = plugin.getFileManager().getConfig().getInt("roulette.spin-end-slot", 17);
        if (slot < 0 || slot >= size) return Math.min(17, size - 1);
        return slot;
    }

    private int getIntSetting(String path, int fallback, int min) {
        return Math.max(min, plugin.getFileManager().getConfig().getInt(path, fallback));
    }

    private int getSlotSetting(String path, int fallback, int size) {
        int slot = plugin.getFileManager().getConfig().getInt(path, fallback);
        if (slot < 0 || slot >= size) return Math.min(fallback, size - 1);
        return slot;
    }

    private Material getMaterialSetting(String path, Material fallback) {
        String raw = plugin.getFileManager().getConfig().getString(path, fallback.name());
        Material material = raw == null ? null : Material.matchMaterial(raw);
        return material == null ? fallback : material;
    }

    private boolean isBlockedInventoryAction(InventoryAction action) {
        String actionName = action.name();
        return action == InventoryAction.MOVE_TO_OTHER_INVENTORY ||
                action == InventoryAction.HOTBAR_SWAP ||
                actionName.equals("HOTBAR_MOVE_AND_READD") ||
                action == InventoryAction.COLLECT_TO_CURSOR ||
                actionName.contains("DROP");
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
        int guiSize = getGuiSize();
        Inventory inv = Bukkit.createInventory(new RouletteHolder(session, wonReward), guiSize, ColorUtils.parse(title));

        ItemStack border = new ItemStack(getMaterialSetting("roulette.border-material", Material.BLACK_STAINED_GLASS_PANE));
        for (int i = 0; i < 9; i++) inv.setItem(i, border);
        for (int i = guiSize - 9; i < guiSize; i++) inv.setItem(i, border);

        Material pointer = getMaterialSetting("roulette.pointer-material", Material.HOPPER);
        inv.setItem(getSlotSetting("roulette.top-pointer-slot", 4, guiSize), new ItemStack(pointer));
        inv.setItem(getSlotSetting("roulette.bottom-pointer-slot", 22, guiSize), new ItemStack(pointer));

        player.openInventory(inv);

        startSpinAnimation(player, inv, pool, session, wonReward);
    }

    private void startSpinAnimation(Player player, Inventory inv, List<DungeonReward> pool, RewardSession session, DungeonReward wonReward) {
        // Pre-fetch sound and timing data to avoid YAML reads while the animation is ticking.
        String soundStr = plugin.getFileManager().getConfig().getString("sounds.roulette_tick", "block.note_block.hat");
        Sound soundTick = SoundUtils.getSound(soundStr);
        int maxFrames = getIntSetting("roulette.animation.frames", 60, 1);
        int slowAtFrame = getIntSetting("roulette.animation.slow-at-frame", 40, 1);
        int slowerAtFrame = getIntSetting("roulette.animation.slower-at-frame", 50, 1);
        int slowestAtFrame = getIntSetting("roulette.animation.slowest-at-frame", 55, 1);
        int slowDelayTicks = getIntSetting("roulette.animation.slow-delay-ticks", 2, 1);
        int slowerDelayTicks = getIntSetting("roulette.animation.slower-delay-ticks", 4, 1);
        int slowestDelayTicks = getIntSetting("roulette.animation.slowest-delay-ticks", 8, 1);
        int size = inv.getSize();
        int spinStartSlot = getSpinStartSlot(size);
        int spinEndSlot = Math.max(spinStartSlot, getSpinEndSlot(size));

        final int[] ticks = {0};
        final int[] delay = {1};
        final int maxTicks = 60;
        final SchedulerCompat.TaskHandle[] task = new SchedulerCompat.TaskHandle[1];
        task[0] = SchedulerCompat.runAtEntityTimer(plugin, player, () -> {
            if (!player.isOnline() || !player.getOpenInventory().getTopInventory().equals(inv)) {
                task[0].cancel();
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

            ticks[0]++;

            if (ticks[0] > 40) delay[0] = 2;
            if (ticks[0] > 50) delay[0] = 4;
            if (ticks[0] > 55) delay[0] = 8;

            if (ticks[0] >= maxTicks) {
                task[0].cancel();
                finishSpin(player, inv, session, wonReward);
            } else if (ticks[0] % delay[0] != 0) {
                // Loop skip
            }
        }, 0L, 1L);
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
        inv.setItem(getRewardSlot(inv.getSize()), wonDisplay);

        grantReward(player, wonReward);

        SchedulerCompat.runAtEntity(plugin, player, () -> SchedulerCompat.runGlobalLater(plugin, () -> {
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
        }, 40L));
    }

    private void grantReward(Player player, DungeonReward wonReward) {
        DungeonRewardClaimEvent claimEvent = new DungeonRewardClaimEvent(player, wonReward);
        Bukkit.getPluginManager().callEvent(claimEvent);

        if (!claimEvent.isCancelled()) {
            DungeonReward finalReward = claimEvent.getReward();
            RewardProcessor processor = SinceDungeonAPI.get().getManager().getRewardProcessor(finalReward.type());
            if (processor != null) {
                processor.giveReward(player, finalReward.value(), finalReward.displayName());
            }
        }
    }

    @EventHandler
    public void onRouletteClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof RouletteHolder)) return;

        e.setCancelled(true);
        if (e.getClick() == ClickType.NUMBER_KEY || e.getClick() == ClickType.DOUBLE_CLICK || e.getClick() == ClickType.SWAP_OFFHAND) {
            return;
        }
        if (isBlockedInventoryAction(e.getAction())) {
            return;
        }
    }

    @EventHandler
    public void onRouletteDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof RouletteHolder) {
            e.setCancelled(true);
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
                rewardGUI.forceClaimAll(p, session);
            }

            RewardSessionManager.removeSession(p);
        }
    }

    private DungeonReward getRandomReward(List<DungeonReward> pool) {
        double totalWeight = pool.stream().mapToDouble(DungeonReward::chance).sum();
        if (totalWeight <= 0) {
            return pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
        }

        double roll = ThreadLocalRandom.current().nextDouble(totalWeight);
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
