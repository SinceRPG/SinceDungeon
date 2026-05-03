package net.danh.sincedungeonpremium.actions;

import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.actions.DungeonAction;
import net.danh.sinceDungeon.models.DungeonGame;
import net.danh.sincedungeonpremium.SinceDungeonPremium;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Locale;

public class BuffAction extends DungeonAction {

    private final String effectType;
    private final int durationTicks;
    private final int amplifier;

    public BuffAction(String effectType, int durationTicks, int amplifier) {
        this.effectType = effectType;
        this.durationTicks = durationTicks;
        this.amplifier = amplifier;
    }

    @Override
    public void start(DungeonGame game) {
        PotionEffectType type = Registry.MOB_EFFECT.get(NamespacedKey.minecraft(effectType.toLowerCase(Locale.ROOT)));

        if (type != null) {
            PotionEffect effect = new PotionEffect(type, durationTicks, amplifier);
            for (Player p : game.getParticipants()) {
                if (p.isOnline() && !p.isDead()) {
                    p.addPotionEffect(effect);
                }
            }
        } else {
            SinceDungeonPremium.getInstance().getLogger().warning("Failed to apply buff. Invalid PotionEffectType provided: " + effectType);
        }

        this.forceComplete();
    }

    @Override
    public String getObjectiveText() {
        return SinceDungeon.getPlugin().getLanguageManager().getString("objective.apply_buff");
    }
}