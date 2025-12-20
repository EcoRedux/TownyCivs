package cz.neumimto.towny.townycivs;

import cz.neumimto.towny.townycivs.config.ConfigurationService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class ItemService {

    private final NamespacedKey blockerKey = new NamespacedKey(TownyCivs.INSTANCE, "iblocker");
    @Inject
    private ConfigurationService configurationService;

    public static ItemStack getTownAdministrationTool() {
        ItemStack itemStack = new ItemStack(Material.ENCHANTED_BOOK);

        Component name = Component.text("Town Administration")
                .color(NamedTextColor.GOLD)
                .decorate(TextDecoration.BOLD);


        itemStack.editMeta(meta -> {
            meta.setCustomModelData(3077);
            meta.displayName(name);
            meta.setEnchantmentGlintOverride(true);
        });
        return itemStack;
    }

    public static ItemStack getStructureTool() {
        ItemStack itemStack = new ItemStack(Material.PAPER);


        Component name = Component.text("Structure Edit Tool")
                .color(NamedTextColor.GOLD)
                .decorate(TextDecoration.BOLD);

        itemStack.editMeta(meta -> {
            meta.setCustomModelData(3078);
            meta.displayName(name);
            meta.setEnchantmentGlintOverride(true);
        });
        return itemStack;
    }

    public static ItemStack getPowerTool() {
        ItemStack itemStack = new ItemStack(Material.REDSTONE_TORCH);

        Component name = Component.text("Power Line Tool")
                .color(NamedTextColor.YELLOW)
                .decorate(TextDecoration.BOLD);

        itemStack.editMeta(meta -> {
            meta.setCustomModelData(3079);
            meta.displayName(name);
            meta.setEnchantmentGlintOverride(true);
            meta.lore(java.util.List.of(
                Component.text("Right-click power towers to connect them", NamedTextColor.GRAY),
                Component.text("Shift+Right-click to cancel connection", NamedTextColor.GRAY)
            ));
        });
        return itemStack;
    }

    public void registerRecipes() {
        NamespacedKey recipe = NamespacedKey.fromString("townycivs:town_book");
        if (Bukkit.getServer().getRecipe(recipe) != null) {
            Bukkit.getServer().removeRecipe(recipe);
        }
        ItemStack itemStack = getTownAdministrationTool();
        ShapelessRecipe shapelessRecipe = new ShapelessRecipe(recipe, itemStack);
        shapelessRecipe.addIngredient(Material.BOOK);
        shapelessRecipe.addIngredient(Material.PAPER);

        Bukkit.getServer().addRecipe(shapelessRecipe);

        recipe = NamespacedKey.fromString("townycivs:structure_tool");
        if (Bukkit.getServer().getRecipe(recipe) != null) {
            Bukkit.getServer().removeRecipe(recipe);
        }
        itemStack = getStructureTool();
        ShapelessRecipe editTool = new ShapelessRecipe(recipe, itemStack);
        editTool.addIngredient(Material.PAPER);
        editTool.addIngredient(Material.WOODEN_SHOVEL);
        Bukkit.getServer().addRecipe(editTool);

        // Power Tool recipe
        recipe = NamespacedKey.fromString("townycivs:power_tool");
        if (Bukkit.getServer().getRecipe(recipe) != null) {
            Bukkit.getServer().removeRecipe(recipe);
        }
        itemStack = getPowerTool();
        ShapelessRecipe powerTool = new ShapelessRecipe(recipe, itemStack);
        powerTool.addIngredient(Material.REDSTONE_TORCH);
        powerTool.addIngredient(Material.COPPER_INGOT);
        Bukkit.getServer().addRecipe(powerTool);
    }

    public StructureTool getItemType(ItemStack itemInUse) {
        if (itemInUse == null) {
            return null;
        }

        if (itemInUse.getType() == Material.ENCHANTED_BOOK) {
            ItemMeta itemMeta = itemInUse.getItemMeta();
            if (itemMeta.hasCustomModelData()) {
                if (itemMeta.getCustomModelData() == 3077) {
                    return StructureTool.TOWN_TOOL;
                }
            }
        }

        if (itemInUse.getType() == Material.PAPER) {
            ItemMeta itemMeta = itemInUse.getItemMeta();
            if (itemMeta.hasCustomModelData()) {
                if (itemMeta.getCustomModelData() == 3078) {
                    return StructureTool.EDIT_TOOL;
                }
            }
        }

        if (itemInUse.getType() == Material.REDSTONE_TORCH) {
            ItemMeta itemMeta = itemInUse.getItemMeta();
            if (itemMeta.hasCustomModelData()) {
                if (itemMeta.getCustomModelData() == 3079) {
                    return StructureTool.POWER_TOOL;
                }
            }
        }

        return null;
    }

    /**
     * Called from async
     *
     * @param key
     * @param value
     * @return
     */
    public ItemStack toItemStack(String key, Integer value) {
        Material material = Material.matchMaterial(key);
        return new ItemStack(material, value);
    }

    public ItemStack getInventoryBlocker() {
        var is = new ItemStack(configurationService.config.inventoryBlockerMaterial, 1);
        is.editMeta(itemMeta -> {
            itemMeta.setCustomModelData(configurationService.config.inventoryBlockerCustomModelData);
            itemMeta.displayName(Component.empty());
            itemMeta.getPersistentDataContainer().set(blockerKey, PersistentDataType.INTEGER, 1);
        });
        return is;
    }

    public boolean isInventoryBlocker(ItemStack itemStack) {
        if (itemStack.getType() != configurationService.config.inventoryBlockerMaterial) {
            return false;
        }
        if (itemStack.getItemMeta() == null) {
            return false;
        }
        return itemStack.getItemMeta().getPersistentDataContainer().has(blockerKey);
    }

    public enum StructureTool {
        EDIT_TOOL, TOWN_TOOL, POWER_TOOL
    }

}
