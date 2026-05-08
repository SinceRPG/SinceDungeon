package net.danh.sincedungeonpremium.actions;

import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.actions.DungeonAction;
import net.danh.sinceDungeon.models.DungeonGame;
import net.danh.sinceDungeon.utils.ColorUtils;
import net.danh.sinceDungeon.utils.ItemBuilder;
import net.danh.sincedungeonpremium.SinceDungeonPremium;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;

/**
 * Premium Action: Give Item
 * Dynamically grants an item (Vanilla, MMOItem, or Custom Key) to all participants in the dungeon.
 * Useful for puzzle solving or rewarding players mid-dungeon.
 */
public class GiveItemAction extends DungeonAction {

    private final String itemData;
    private final String receiveMessage;

    public GiveItemAction(String itemData, String receiveMessage) {
        this.itemData = itemData;
        this.receiveMessage = receiveMessage;
    }

    @Override
    public void start(DungeonGame game) {
        ItemStack item = ItemBuilder.parseDynamicItem(itemData);

        if (item != null) {
            for (Player p : game.getParticipants()) {
                if (p.isOnline() && !p.isDead()) {
                    HashMap<Integer, ItemStack> leftover = p.getInventory().addItem(item.clone());
                    if (!leftover.isEmpty()) {
                        for (ItemStack drop : leftover.values()) {
                            p.getWorld().dropItem(p.getLocation(), drop);
                        }
                        p.sendMessage(ColorUtils.parseWithPrefix(SinceDungeon.getPlugin().getLanguageManager().getString("reward.messages.inventory_full")));
                    }

                    if (receiveMessage != null && !receiveMessage.isEmpty()) {
                        p.sendMessage(ColorUtils.parseWithPrefix(receiveMessage));
                    }
                }
            }
        } else {
            String logMsg = SinceDungeonPremium.getInstance().getFileManager().getMessageRaw("log.give_item_fail");
            SinceDungeon.getPlugin().getLogger().warning(logMsg.replace("<data>", itemData));
        }

        forceComplete();
    }

    @Override
    public String getObjectiveText() {
        return SinceDungeon.getPlugin().getLanguageManager().getString("objective.give_item");
    }
}