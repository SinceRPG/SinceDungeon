package net.danh.sinceDungeon.utils;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.danh.sinceDungeon.SinceDungeon;
import net.danh.sinceDungeon.api.interfaces.CustomItemProvider;
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
import java.util.Random;
import java.util.UUID;

/**
 * Advanced ItemBuilder utility using the Builder Pattern.
 * Replaces hardcoded generation methods with a flexible chainable API.
 * Centralizes configuration parsing and PersistentData (NBT) handling.
 */
public class ItemBuilder {
    private final SinceDungeon plugin;
    private final ItemStack item;
    private final ItemMeta meta;

    public ItemBuilder(SinceDungeon plugin, Material material) {
        this.plugin = plugin;
        this.item = new ItemStack(material);
        this.meta = this.item.getItemMeta();
    }

    public ItemBuilder(SinceDungeon plugin, ItemStack itemStack) {
        this.plugin = plugin;
        this.item = itemStack.clone();
        this.meta = this.item.getItemMeta();
    }

    /**
     * Helper method to parse amounts that might be an algorithmic range (e.g., "1-5").
     *
     * @param amtStr The amount string to parse.
     * @return The parsed or randomized integer amount.
     */
    public static int parseRandomAmount(String amtStr) {
        try {
            if (amtStr.contains("-")) {
                String[] range = amtStr.split("-");
                int min = Integer.parseInt(range[0].trim());
                int max = Integer.parseInt(range[1].trim());
                if (min > max) {
                    int temp = min;
                    min = max;
                    max = temp;
                }
                return min + new Random().nextInt(max - min + 1);
            } else {
                return Integer.parseInt(amtStr.trim());
            }
        } catch (Exception e) {
            return 1;
        }
    }

    public static ItemBuilder fromConfig(SinceDungeon plugin, String configPath, String fallbackMaterial) {
        String matStr = plugin.getConfigFile().getString(configPath + ".material", fallbackMaterial);
        Material mat = Material.matchMaterial(matStr.toUpperCase());
        if (mat == null) mat = Material.valueOf(fallbackMaterial.toUpperCase());
        return new ItemBuilder(plugin, mat);
    }

    public static boolean hasTag(ItemStack item, NamespacedKey key, PersistentDataType<?, ?> type) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(key, type);
    }

    public static <T, Z> Z getTag(ItemStack item, NamespacedKey key, PersistentDataType<T, Z> type) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(key, type);
    }

    /**
     * Parses dynamic item configurations utilizing the internal CustomItemProvider registry.
     * Supports standard Vanilla Item Parsing format: MATERIAL:AMOUNT:CMD
     *
     * @param data The configuration string (e.g., "PREFIX:DATA:AMOUNT")
     * @return The constructed ItemStack or null if parsing fails.
     */
    public static ItemStack parseDynamicItem(String data) {
        if (data == null || data.isEmpty()) return null;
        try {
            String cleanData = data.replace(" ", "");
            String[] parts = cleanData.split(":", 2);

            String prefix = parts[0].toUpperCase();

            // 1. Check if a custom provider is registered for this prefix
            CustomItemProvider provider = SinceDungeon.getPlugin().getDungeonManager().getItemProvider(prefix);

            if (provider != null) {
                return provider.parseItem(cleanData);
            }

            // 2. Fallback to standard Vanilla Item Parsing (MATERIAL:AMOUNT or MATERIAL:MIN-MAX or MATERIAL:AMOUNT:CMD)
            Material mat = Material.matchMaterial(prefix);
            if (mat != null) {
                int amount = 1;
                int cmd = -1;
                if (parts.length > 1) {
                    String[] subParts = parts[1].split(":");
                    amount = parseRandomAmount(subParts[0]);
                    if (subParts.length > 1) {
                        try {
                            cmd = Integer.parseInt(subParts[1]);
                        } catch (Exception ignored) {
                        }
                    }
                }

                ItemStack is = new ItemStack(mat, amount);
                if (cmd != -1) {
                    ItemMeta itemMeta = is.getItemMeta();
                    if (itemMeta != null) {
                        itemMeta.setCustomModelData(cmd);
                        is.setItemMeta(itemMeta);
                    }
                }
                return is;
            }

        } catch (Throwable e) {
            String logMsg = SinceDungeon.getPlugin().getLanguageManager().getString("admin.log.item_dynamic_parse_error", "Error parsing dynamic item data: <data>");
            SinceDungeon.getPlugin().getLogger().warning(logMsg.replace("<data>", data));
        }
        return null;
    }

    public ItemBuilder amount(int amount) {
        this.item.setAmount(amount);
        return this;
    }

    public <T, Z> ItemBuilder setTag(NamespacedKey key, PersistentDataType<T, Z> type, Z value) {
        if (meta != null) {
            meta.getPersistentDataContainer().set(key, type, value);
        }
        return this;
    }

    public ItemBuilder applyConfig(ConfigurationSection cfg, String defName, String... replacements) {
        if (meta == null) return this;

        if (cfg == null) {
            meta.displayName(ColorUtils.parse(defName).decoration(TextDecoration.ITALIC, false));
            return this;
        }

        String name = cfg.getString("name", defName);
        for (int i = 0; i < replacements.length; i += 2) name = name.replace(replacements[i], replacements[i + 1]);
        meta.displayName(ColorUtils.parse(name).decoration(TextDecoration.ITALIC, false));

        if (cfg.contains("item-name")) {
            try {
                String itemName = cfg.getString("item-name");
                for (int i = 0; i < replacements.length; i += 2)
                    itemName = itemName.replace(replacements[i], replacements[i + 1]);
                meta.itemName(ColorUtils.parse(itemName).decoration(TextDecoration.ITALIC, false));
            } catch (Throwable ignored) {
            }
        }

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

        if (cfg.contains("item-model")) {
            try {
                NamespacedKey key = NamespacedKey.fromString(cfg.getString("item-model"));
                if (key != null) meta.setItemModel(key);
            } catch (Throwable ignored) {
            }
        }

        if (cfg.contains("tooltip-style")) {
            try {
                NamespacedKey key = NamespacedKey.fromString(cfg.getString("tooltip-style"));
                if (key != null) meta.setTooltipStyle(key);
            } catch (Throwable ignored) {
            }
        }

        if (cfg.contains("max-stack-size")) {
            try {
                meta.setMaxStackSize(Math.max(1, Math.min(99, cfg.getInt("max-stack-size"))));
            } catch (Throwable ignored) {
            }
        }

        if (cfg.contains("rarity")) {
            try {
                meta.setRarity(ItemRarity.valueOf(cfg.getString("rarity").toUpperCase()));
            } catch (Throwable ignored) {
            }
        }

        if (cfg.contains("hide-tooltip")) {
            try {
                meta.setHideTooltip(cfg.getBoolean("hide-tooltip"));
            } catch (Throwable ignored) {
            }
        }

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

        if (cfg.contains("glider")) {
            try {
                meta.setGlider(cfg.getBoolean("glider"));
            } catch (Throwable ignored) {
            }
        }

        if (cfg.contains("enchantable")) {
            try {
                meta.setEnchantable(cfg.getInt("enchantable"));
            } catch (Throwable ignored) {
            }
        }

        if (cfg.contains("unbreakable")) meta.setUnbreakable(cfg.getBoolean("unbreakable"));

        if (cfg.contains("flags")) {
            for (String flag : cfg.getStringList("flags")) {
                try {
                    meta.addItemFlags(ItemFlag.valueOf(flag.toUpperCase()));
                } catch (Exception ignored) {
                }
            }
        }

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

        if (cfg.contains("damage") && meta instanceof Damageable dmgMeta) {
            dmgMeta.setDamage(cfg.getInt("damage"));
        }

        return this;
    }

    public ItemStack build() {
        if (meta != null) {
            item.setItemMeta(meta);
        }
        return item;
    }
}