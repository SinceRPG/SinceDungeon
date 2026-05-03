package net.danh.sincedungeonpremium.actions;

import net.danh.sinceDungeon.actions.DungeonAction;
import net.danh.sinceDungeon.models.DungeonGame;
import net.danh.sincedungeonpremium.SinceDungeonPremium;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Locale;

/**
 * Premium-Exclusive Action: Apply Buff
 * Responsibilities:
 * - Grants a specific PotionEffect to all alive/online participants in the dungeon.
 * - Adapts to Paper 1.21+ Registry for retrieving PotionEffectTypes safely, mitigating deprecation warnings.
 * - Instantly completes to avoid halting the dungeon phase progression.
 */
public class BuffAction extends DungeonAction {

    private final String effectType;
    private final int durationTicks;
    private final int amplifier;
    private final String objectiveText;

    public BuffAction(String effectType, int durationTicks, int amplifier, String objectiveText) {
        this.effectType = effectType;
        this.durationTicks = durationTicks;
        this.amplifier = amplifier;
        this.objectiveText = objectiveText;
    }

    @Override
    public void start(DungeonGame game) {
        PotionEffectType type = Registry.POTION_EFFECT_TYPE.get(NamespacedKey.minecraft(effectType.toLowerCase(Locale.ROOT)));

        if (type == null) {
            type = PotionEffectType.getByName(effectType.toUpperCase(Locale.ROOT));
        }

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
        return objectiveText;
    }
}