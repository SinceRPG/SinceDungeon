package net.danh.sinceDungeon.reward;

import io.lumine.mythic.lib.api.item.NBTItem;
import me.clip.placeholderapi.PlaceholderAPI;
import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.manager.DungeonTemplate;
import net.danh.sinceDungeon.system.MMOItemsHook;
import net.danh.sinceDungeon.utils.ColorUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

public class RewardGUI implements Listener {
    private final SinceDungeon plugin;

    public RewardGUI(SinceDungeon plugin) {
        this.plugin = plugin;
    }

    // --- HELPER METHODS ---
    private FileConfiguration getConfig() {
        return plugin.getMessagesFile().getConfig();
    }

    private String getMsg(String key) {
        return getConfig().getString("reward.messages." + key);
    }

    private String getPlainText(Component c) {
        return PlainTextComponentSerializer.plainText().serialize(c);
    }

    private boolean isRewardGui(Component titleComp) {
        String guiTitle = getPlainText(titleComp);
        String rawConfig = getConfig().getString("reward.gui_title");
        if (rawConfig == null) return false;
        String configPlain = getPlainText(ColorUtils.parse(rawConfig));
        return guiTitle.equals(configPlain);
    }

    private int getGuiSize() {
        int size = getConfig().getInt("reward.settings.gui_size", 27);
        if (size % 9 != 0 || size < 9 || size > 54) return 27;
        return size;
    }

    private int getButtonSlot() {
        return getConfig().getInt("reward.settings.button_slot", 13);
    }

    private void playSound(Player p, String key) {
        try {
            String soundName = plugin.getConfigFile().getString("reward.sounds." + key);
            if (soundName != null) {
                p.playSound(p.getLocation(), soundName, 1f, 1f);
            }
        } catch (Exception ignored) {
        }
    }

    private ItemStack createIcon(String key, int chestCount) {
        String path = "reward.icons." + key;
        String matName = getConfig().getString(path + ".material", "STONE");
        Material mat = Material.matchMaterial(matName);
        if (mat == null) mat = Material.STONE;

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();

        String name = getConfig().getString(path + ".name");
        if (name != null) meta.displayName(ColorUtils.parse("<!i>" + name));

        List<String> loreRaw = getConfig().getStringList(path + ".lore");
        List<Component> lore = new ArrayList<>();
        for (String line : loreRaw) {
            lore.add(ColorUtils.parse("<!i>" + line.replace("<count>", String.valueOf(chestCount))));
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    // --- LOGIC ---

    public void openRewardGUI(Player p, int chestCount, DungeonTemplate template) {
        String titleStr = getConfig().getString("reward.gui_title", "Reward");
        Inventory inv = Bukkit.createInventory(null, getGuiSize(), ColorUtils.parse(titleStr));

        ItemStack btn = createIcon("button", chestCount);
        inv.setItem(getButtonSlot(), btn);

        RewardSessionManager.addSession(p, new RewardSession(chestCount, template));
        p.openInventory(inv);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        RewardSession session = RewardSessionManager.getSession(p);
        if (session != null && session.getChestCount() > 0) {
            int remaining = session.getChestCount();
            for (int i = 0; i < remaining; i++) {
                List<DungeonReward> pool = session.getTemplate().rewardPool();
                if (pool != null && !pool.isEmpty()) {
                    giveReward(p, pool.get(new Random().nextInt(pool.size())));
                }
            }
            RewardSessionManager.removeSession(p);
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!isRewardGui(e.getView().title())) return;

        // [SỬA LỖI]: Chặn Shift-Click từ dưới lên trên
        if (e.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY && e.getClickedInventory() == e.getView().getBottomInventory()) {
            e.setCancelled(true);
            return;
        }

        // [SỬA LỖI]: Chặn bấm phím số (1-9) để trộm đồ từ GUI
        if (e.getAction() == InventoryAction.HOTBAR_SWAP || e.getAction() == InventoryAction.HOTBAR_MOVE_AND_READD) {
            e.setCancelled(true);
            return;
        }

        if (e.getClickedInventory() == null || e.getClickedInventory() == e.getView().getBottomInventory()) return;

        // Xử lý click trong GUI
        if (e.getClickedInventory() == e.getView().getTopInventory()) {
            e.setCancelled(true); // Luôn luôn cancel để không cho lấy item ra

            RewardSession session = RewardSessionManager.getSession(p);
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
        if (isRewardGui(e.getView().title())) {
            if (e.getPlayer() instanceof Player p) {
                RewardSession session = RewardSessionManager.getSession(p);
                if (session != null && session.getChestCount() > 0) {
                    int remaining = session.getChestCount();
                    for (int i = 0; i < remaining; i++) {
                        List<DungeonReward> pool = session.getTemplate().rewardPool();
                        if (pool != null && !pool.isEmpty()) {
                            giveReward(p, pool.get(new Random().nextInt(pool.size())));
                        }
                    }
                    String msg = getMsg("auto_claim");
                    if (msg != null)
                        p.sendMessage(ColorUtils.parseWithPrefix(msg.replace("<count>", String.valueOf(remaining))));
                    playSound(p, "claim");
                }
                RewardSessionManager.removeSession(p);
            }
        }
    }

    private void revealRewards(Player p, Inventory inv, RewardSession session) {
        inv.clear();
        session.setRevealed(true);
        int chests = session.getChestCount();
        int size = getGuiSize();
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < size; i++) slots.add(i);
        Random rand = new Random();
        ItemStack mysteryChest = createIcon("mystery_chest", 0);
        for (int i = 0; i < chests; i++) {
            if (slots.isEmpty()) break;
            int slot = slots.remove(rand.nextInt(slots.size()));
            inv.setItem(slot, mysteryChest);
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
            giveReward(p, pool.get(new Random().nextInt(pool.size())));
        }
        if (session.getChestCount() <= 0) {
            String msg = getMsg("claimed_all");
            if (msg != null) p.sendMessage(ColorUtils.parseWithPrefix(msg));
            Bukkit.getScheduler().runTaskLater(plugin, () -> p.closeInventory(), 20L);
        }
    }

    private void giveReward(Player p, DungeonReward reward) {
        String type = reward.type();
        String val = PlaceholderAPI.setPlaceholders(p, reward.value());

        if (type.equalsIgnoreCase("COMMAND")) {
            String cmd = val.replace("%player%", p.getName());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
            String displayName = reward.displayName();
            String msg = getMsg("received_custom");
            if (displayName != null && msg != null) {
                p.sendMessage(ColorUtils.parseWithPrefix(msg.replace("<item>", displayName)));
            }
        } else if (type.equalsIgnoreCase("ITEM")) {
            try {
                String[] parts = val.split(":");
                Material mat = Material.valueOf(parts[0]);
                int amount = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;
                ItemStack item = new ItemStack(mat, amount);
                HashMap<Integer, ItemStack> left = p.getInventory().addItem(item);
                if (!left.isEmpty()) {
                    for (ItemStack drop : left.values()) p.getWorld().dropItem(p.getLocation(), drop);
                    String fullMsg = getMsg("inventory_full");
                    if (fullMsg != null) p.sendMessage(ColorUtils.parseWithPrefix(fullMsg));
                }
                String displayName = reward.displayName();
                if (displayName == null || displayName.isEmpty()) displayName = mat.name() + " x" + amount;
                String msg = getMsg("received_item");
                if (msg != null)
                    p.sendMessage(ColorUtils.parseWithPrefix(msg.replace("<item>", displayName)));
            } catch (Exception ignored) {
            }
        } else if (type.equalsIgnoreCase("MMOITEM")) {
            if (Bukkit.getPluginManager().getPlugin("MMOItems") == null) return;
            try {
                String[] parts = val.split(":");
                String mType = parts[0];
                String mId = parts[1];
                int amount = parts.length > 2 ? Integer.parseInt(parts[2]) : 1;
                ItemStack item = MMOItemsHook.getMMOItem(mType, mId, amount);
                if (item != null) {
                    HashMap<Integer, ItemStack> left = p.getInventory().addItem(item);
                    if (!left.isEmpty()) {
                        for (ItemStack drop : left.values()) p.getWorld().dropItem(p.getLocation(), drop);
                        String fullMsg = getMsg("inventory_full");
                        if (fullMsg != null) p.sendMessage(ColorUtils.parseWithPrefix(fullMsg));
                    }
                    String displayName = reward.displayName();
                    if (displayName == null) {
                        NBTItem nbtItem = NBTItem.get(item);
                        if (nbtItem.hasTag("MMOITEMS_NAME")) {
                            displayName = ColorUtils.convertLegacyToMiniMessage(nbtItem.getString("MMOITEMS_NAME"));
                        } else {
                            if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
                                displayName = ColorUtils.convertLegacyToMiniMessage(item.getItemMeta().getDisplayName());
                            } else {
                                displayName = mId;
                            }
                        }
                    }
                    String msg = getMsg("received_item");
                    if (msg != null)
                        p.sendMessage(ColorUtils.parseWithPrefix(msg.replace("<item>", displayName)));
                }
            } catch (Exception ignored) {
            }
        }
    }
}