package net.danh.sinceDungeon.reward;

import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.api.events.DungeonRewardClaimEvent;
import net.danh.sinceDungeon.api.interfaces.RewardProcessor;
import net.danh.sinceDungeon.manager.DungeonTemplate;
import net.danh.sinceDungeon.utils.ColorUtils;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class RewardGUI implements Listener {
    private final SinceDungeon plugin;

    public RewardGUI(SinceDungeon plugin) {
        this.plugin = plugin;
    }

    private FileConfiguration getConfig() {
        return plugin.getMessagesFile().getConfig();
    }

    private String getMsg(String key) {
        return getConfig().getString("reward.messages." + key);
    }

    private int getGuiSize() {
        int size = getConfig().getInt("reward.settings.gui_size", 27);
        if (size % 9 != 0 || size < 9 || size > 54) return 27;
        return size;
    }

    private int getButtonSlot() {
        int slot = getConfig().getInt("reward.settings.button_slot", 13);
        int size = getGuiSize();
        if (slot < 0 || slot >= size) return size / 2;
        return slot;
    }

    private void playSound(Player p, String key) {
        try {
            String soundName = plugin.getConfigFile().getString("reward.sounds." + key);
            if (soundName != null) p.playSound(p.getLocation(), soundName, 1f, 1f);
        } catch (Exception ignored) {
        }
    }

    private ItemStack createIcon(String key, int chestCount) {
        String path = "reward.icons." + key;
        Material mat = Material.matchMaterial(getConfig().getString(path + ".material", "STONE"));
        if (mat == null) mat = Material.STONE;

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        String name = getConfig().getString(path + ".name");
        if (name != null) meta.displayName(ColorUtils.parse("<!i>" + name));

        List<String> loreRaw = getConfig().getStringList(path + ".lore");
        List<Component> lore = new ArrayList<>();
        for (String line : loreRaw)
            lore.add(ColorUtils.parse("<!i>" + line.replace("<count>", String.valueOf(chestCount))));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private DungeonReward getRandomReward(List<DungeonReward> pool) {
        if (pool == null || pool.isEmpty()) return null;

        double totalWeight = 0.0;
        for (DungeonReward reward : pool) totalWeight += reward.chance();

        if (totalWeight <= 0) return pool.get(new Random().nextInt(pool.size()));

        double random = new Random().nextDouble() * totalWeight;
        double currentWeight = 0.0;
        for (DungeonReward reward : pool) {
            currentWeight += reward.chance();
            if (random <= currentWeight) return reward;
        }
        return pool.get(0);
    }

    public void openRewardGUI(Player p, int chestCount, DungeonTemplate template) {
        RewardSession session = RewardSessionManager.getSession(p);
        if (session == null) {
            session = new RewardSession(chestCount, template);
            RewardSessionManager.addSession(p, session);
        }

        String titleStr = getConfig().getString("reward.gui_title", "Reward");

        Inventory inv = Bukkit.createInventory(new RewardHolder(session), getGuiSize(), ColorUtils.parse(titleStr));

        inv.setItem(getButtonSlot(), createIcon("button", chestCount));
        p.openInventory(inv);
    }

    public void forceClaimAll(Player p, RewardSession session) {
        int remaining = session.getChestCount();
        if (remaining <= 0) return;

        for (int i = 0; i < remaining; i++) {
            List<DungeonReward> pool = session.getTemplate().rewardPool();
            if (pool != null && !pool.isEmpty()) {
                DungeonReward reward = getRandomReward(pool);
                if (reward != null) giveReward(p, reward);
            }
        }

        while (session.getChestCount() > 0) {
            session.decreaseChestCount();
        }

        String msg = getMsg("auto_claim");
        if (msg != null) p.sendMessage(ColorUtils.parseWithPrefix(msg.replace("<count>", String.valueOf(remaining))));
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        RewardSession session = RewardSessionManager.getSession(p);
        if (session != null && session.getChestCount() > 0) {
            forceClaimAll(p, session);
            RewardSessionManager.removeSession(p);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof RewardHolder) {
            for (int slot : e.getRawSlots()) {
                if (slot < e.getView().getTopInventory().getSize()) {
                    e.setCancelled(true);
                    return;
                }
            }
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;

        if (!(e.getView().getTopInventory().getHolder() instanceof RewardHolder holder)) return;

        // VÁ LỖI BẢO MẬT: Bắt chặt hành vi quăng đồ (phím Q) và các tương tác Swap/Number
        if (e.getClick() == ClickType.NUMBER_KEY || e.getClick() == ClickType.DOUBLE_CLICK || e.getClick() == ClickType.SWAP_OFFHAND) {
            e.setCancelled(true);
            return;
        }

        if (e.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY || e.getAction() == InventoryAction.HOTBAR_SWAP ||
                e.getAction() == InventoryAction.HOTBAR_MOVE_AND_READD || e.getAction() == InventoryAction.COLLECT_TO_CURSOR ||
                e.getAction().name().contains("DROP")) {
            e.setCancelled(true);
            return;
        }

        if (e.getClickedInventory() == null) return;

        if (e.getClickedInventory() == e.getView().getTopInventory()) {
            e.setCancelled(true);

            RewardSession session = holder.getSession();
            if (session == null) {
                p.closeInventory();
                return;
            }

            int slot = e.getRawSlot();
            ItemStack clicked = e.getCurrentItem();
            if (clicked == null || clicked.getType() == Material.AIR) return;

            if (!session.isRevealed()) {
                if (slot == getButtonSlot()) revealRewards(p, e.getInventory(), session);
            } else {
                String mysteryMatName = getConfig().getString("reward.icons.mystery_chest.material", "CHEST");
                if (clicked.getType().name().equals(mysteryMatName)) {
                    claimReward(p, e.getInventory(), slot, session);
                }
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof RewardHolder holder && e.getPlayer() instanceof Player p) {
            RewardSession session = holder.getSession();
            if (session != null && session.getChestCount() > 0) {
                forceClaimAll(p, session);
                playSound(p, "claim");
            }
            RewardSessionManager.removeSession(p);
        }
    }

    private void revealRewards(Player p, Inventory inv, RewardSession session) {
        inv.clear();
        session.setRevealed(true);
        int chests = session.getChestCount();
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < getGuiSize(); i++) slots.add(i);
        Random rand = new Random();
        ItemStack mysteryChest = createIcon("mystery_chest", 0);
        for (int i = 0; i < chests; i++) {
            if (slots.isEmpty()) break;
            inv.setItem(slots.remove(rand.nextInt(slots.size())), mysteryChest);
        }
        playSound(p, "reveal");
    }

    private void claimReward(Player p, Inventory inv, int slot, RewardSession session) {
        if (session.getChestCount() <= 0) return;
        session.decreaseChestCount();
        inv.setItem(slot, new ItemStack(Material.AIR));
        playSound(p, "claim");

        List<DungeonReward> pool = session.getTemplate().rewardPool();
        if (pool != null && !pool.isEmpty()) {
            DungeonReward reward = getRandomReward(pool);
            if (reward != null) giveReward(p, reward);
        }

        if (session.getChestCount() <= 0) {
            String msg = getMsg("claimed_all");
            if (msg != null) p.sendMessage(ColorUtils.parseWithPrefix(msg));
            try {
                Bukkit.getScheduler().runTaskLater(plugin, () -> p.closeInventory(), 20L);
            } catch (Exception ignored) {
            }
        }
    }

    private void giveReward(Player p, DungeonReward reward) {
        DungeonRewardClaimEvent claimEvent = new DungeonRewardClaimEvent(p, reward);
        Bukkit.getPluginManager().callEvent(claimEvent);

        if (claimEvent.isCancelled()) return;
        DungeonReward finalReward = claimEvent.getReward();

        RewardProcessor processor = plugin.getDungeonManager().getRewardProcessor(finalReward.type());

        if (processor != null) {
            processor.giveReward(p, finalReward.value(), finalReward.displayName());
        } else {
            plugin.getLogger().warning("No RewardProcessor found for type: " + finalReward.type());
        }
    }
}