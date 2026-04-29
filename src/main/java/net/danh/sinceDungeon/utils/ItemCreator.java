package net.danh.sinceDungeon.utils;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.danh.sinceDungeon.SinceDungeon;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemRarity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ItemCreator {

    private final SinceDungeon plugin;

    public ItemCreator(SinceDungeon plugin) {
        this.plugin = plugin;
    }

    public ItemStack buildItem(String configPath, String defMat, int amount) {
        String matStr = plugin.getConfigFile().getString(configPath + ".material", defMat);
        Material mat = Material.matchMaterial(matStr.toUpperCase());
        if (mat == null) mat = Material.valueOf(defMat.toUpperCase());
        return new ItemStack(mat, amount);
    }

    public void applyItemMeta(ItemStack item, ConfigurationSection cfg, String defName, String... replacements) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        if (cfg == null) {
            meta.displayName(ColorUtils.parse(defName).decoration(TextDecoration.ITALIC, false));
            item.setItemMeta(meta);
            return;
        }

        // 1. Display Name
        String name = cfg.getString("name", defName);
        for (int i = 0; i < replacements.length; i += 2) name = name.replace(replacements[i], replacements[i + 1]);
        meta.displayName(ColorUtils.parse(name).decoration(TextDecoration.ITALIC, false));

        // 2. Item Name (1.21+ API)
        if (cfg.contains("item-name")) {
            try {
                String itemName = cfg.getString("item-name");
                for (int i = 0; i < replacements.length; i += 2)
                    itemName = itemName.replace(replacements[i], replacements[i + 1]);
                meta.itemName(ColorUtils.parse(itemName).decoration(TextDecoration.ITALIC, false));
            } catch (Throwable ignored) {
            }
        }

        // 3. Lore
        if (cfg.contains("lore")) {
            List<String> rawLore = cfg.getStringList("lore");
            List<Component> compLore = new ArrayList<>();
            for (String line : rawLore) {
                for (int i = 0; i < replacements.length; i += 2)
                    line = line.replace(replacements[i], replacements[i + 1]);
                compLore.add(ColorUtils.parse(line).decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(compLore);
        }

        // 4. Custom Model Data (Compatible with Legacy and 1.21+ Components)
        if (cfg.contains("custom-model-data")) {
            if (cfg.isConfigurationSection("custom-model-data")) {
                ConfigurationSection cmdSec = cfg.getConfigurationSection("custom-model-data");
                if (ServerVersion.isAtLeast(1, 21, 5)) {
                    try {
                        CustomModelDataComponent cmdc = meta.getCustomModelDataComponent();
                        if (cmdSec.contains("floats")) {
                            List<Float> floats = new ArrayList<>();
                            for (Double d : cmdSec.getDoubleList("floats")) floats.add(d.floatValue());
                            cmdc.setFloats(floats);
                        }
                        if (cmdSec.contains("strings")) cmdc.setStrings(cmdSec.getStringList("strings"));
                        if (cmdSec.contains("flags")) cmdc.setFlags(cmdSec.getBooleanList("flags"));
                        if (cmdSec.contains("colors")) {
                            List<Color> colors = new ArrayList<>();
                            for (String hex : cmdSec.getStringList("colors")) {
                                try {
                                    colors.add(Color.fromRGB(Integer.parseInt(hex.replace("#", ""), 16)));
                                } catch (Exception ignored) {
                                }
                            }
                            cmdc.setColors(colors);
                        }
                        meta.setCustomModelDataComponent(cmdc);
                    } catch (Throwable t) {
                        if (cmdSec.contains("value")) meta.setCustomModelData(cmdSec.getInt("value"));
                    }
                } else {
                    if (cmdSec.contains("value")) meta.setCustomModelData(cmdSec.getInt("value"));
                }
            } else {
                if (ServerVersion.isAtLeast(1, 21, 5)) {
                    try {
                        CustomModelDataComponent cmdc = meta.getCustomModelDataComponent();
                        cmdc.setFloats(List.of((float) cfg.getInt("custom-model-data")));
                        meta.setCustomModelDataComponent(cmdc);
                    } catch (Throwable t) {
                        meta.setCustomModelData(cfg.getInt("custom-model-data"));
                    }
                } else {
                    meta.setCustomModelData(cfg.getInt("custom-model-data"));
                }
            }
        }

        // 5. Item Model (1.21+)
        if (cfg.contains("item-model")) {
            try {
                NamespacedKey key = NamespacedKey.fromString(cfg.getString("item-model"));
                if (key != null) meta.setItemModel(key);
            } catch (Throwable ignored) {
            }
        }

        // 6. Tooltip Style (1.21+)
        if (cfg.contains("tooltip-style")) {
            try {
                NamespacedKey key = NamespacedKey.fromString(cfg.getString("tooltip-style"));
                if (key != null) meta.setTooltipStyle(key);
            } catch (Throwable ignored) {
            }
        }

        // 7. Max Stack Size (1.21+)
        if (cfg.contains("max-stack-size")) {
            try {
                meta.setMaxStackSize(Math.max(1, Math.min(99, cfg.getInt("max-stack-size"))));
            } catch (Throwable ignored) {
            }
        }

        // 8. Rarity
        if (cfg.contains("rarity")) {
            try {
                meta.setRarity(ItemRarity.valueOf(cfg.getString("rarity").toUpperCase()));
            } catch (Throwable ignored) {
            }
        }

        // 9. Hide Tooltip
        if (cfg.contains("hide-tooltip")) {
            try {
                meta.setHideTooltip(cfg.getBoolean("hide-tooltip"));
            } catch (Throwable ignored) {
            }
        }

        // 10. Glint Override (Glowing)
        if (cfg.contains("glowing") || cfg.contains("glint-override")) {
            try {
                meta.setEnchantmentGlintOverride(cfg.getBoolean("glowing", cfg.getBoolean("glint-override")));
            } catch (Throwable ignored) {
                if (cfg.getBoolean("glowing")) {
                    meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                    meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                }
            }
        }

        // 11. Glider (Elytra behavior)
        if (cfg.contains("glider")) {
            try {
                meta.setGlider(cfg.getBoolean("glider"));
            } catch (Throwable ignored) {
            }
        }

        // 12. Enchantable
        if (cfg.contains("enchantable")) {
            try {
                meta.setEnchantable(cfg.getInt("enchantable"));
            } catch (Throwable ignored) {
            }
        }

        // 13. Unbreakable
        if (cfg.contains("unbreakable")) meta.setUnbreakable(cfg.getBoolean("unbreakable"));

        // 14. Item Flags
        if (cfg.contains("flags")) {
            for (String flag : cfg.getStringList("flags")) {
                try {
                    meta.addItemFlags(ItemFlag.valueOf(flag.toUpperCase()));
                } catch (Exception ignored) {
                }
            }
        }

        // 15. Vanilla Enchantments
        if (cfg.contains("enchants")) {
            ConfigurationSection enchSec = cfg.getConfigurationSection("enchants");
            if (enchSec != null) {
                for (String key : enchSec.getKeys(false)) {
                    NamespacedKey nsKey = NamespacedKey.fromString(key.toLowerCase());
                    if (nsKey != null) {
                        Enchantment enchantment = RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT).get(nsKey);
                        if (enchantment != null) meta.addEnchant(enchantment, enchSec.getInt(key), true);
                    }
                }
            }
        }

        // 16. Attribute Modifiers
        if (cfg.contains("attributes")) {
            ConfigurationSection attrSec = cfg.getConfigurationSection("attributes");
            if (attrSec != null) {
                for (String key : attrSec.getKeys(false)) {
                    NamespacedKey nsKey = NamespacedKey.fromString(key.toLowerCase());
                    if (nsKey != null) {
                        Attribute attribute = RegistryAccess.registryAccess().getRegistry(RegistryKey.ATTRIBUTE).get(nsKey);
                        if (attribute != null) {
                            String attrPath = "attributes." + key;
                            double amt = cfg.getDouble(attrPath + ".amount", 0.0);
                            String opStr = cfg.getString(attrPath + ".operation", "ADD_NUMBER");
                            String slotStr = cfg.getString(attrPath + ".slot", "ANY");
                            try {
                                AttributeModifier.Operation op = AttributeModifier.Operation.valueOf(opStr.toUpperCase());
                                EquipmentSlotGroup slotGroup = EquipmentSlotGroup.getByName(slotStr.toLowerCase());
                                if (slotGroup == null) slotGroup = EquipmentSlotGroup.ANY;
                                NamespacedKey modKey = new NamespacedKey(plugin, UUID.randomUUID().toString());
                                AttributeModifier modifier = new AttributeModifier(modKey, amt, op, slotGroup);
                                meta.addAttributeModifier(attribute, modifier);
                            } catch (Exception e) {
                                plugin.getLogger().warning("Failed to parse attribute modifier for " + key);
                            }
                        }
                    }
                }
            }
        }

        // 17. Damage
        if (cfg.contains("damage") && meta instanceof Damageable dmgMeta) {
            dmgMeta.setDamage(cfg.getInt("damage"));
        }

        item.setItemMeta(meta);
    }

    /**
     * Specifically creates the Life Consumable item and injects the persistent data.
     */
    public ItemStack createLifeItem(int amount) {
        ConfigurationSection cfg = plugin.getConfigFile().getConfig().getConfigurationSection("lives.life-item");
        ItemStack item = buildItem("lives.life-item", "TOTEM_OF_UNDYING", 1); // Give 1 physical item

        applyItemMeta(item, cfg, "&a&lExtra Life (+<amount>)", "<amount>", String.valueOf(amount));

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            NamespacedKey key = new NamespacedKey(plugin, "life_amount");
            meta.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, amount);
            item.setItemMeta(meta);
        }
        return item;
    }
}