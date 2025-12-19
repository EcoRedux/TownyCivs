package cz.neumimto.towny.townycivs.mechanics;

import cz.neumimto.towny.townycivs.StructureInventoryService;
import cz.neumimto.towny.townycivs.TownyCivs;
import cz.neumimto.towny.townycivs.mechanics.common.ConditionalItemList;
import cz.neumimto.towny.townycivs.mechanics.common.ItemList;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

import java.util.*;

/**
 * Conditional item production mechanic.
 * Produces different outputs based on what input items are present in the structure.
 *
 * Example: Oak logs → Oak planks, Birch logs → Birch planks
 *          Wooden axe → 3 planks, Stone axe → 4 planks
 */
public class ConditionalItemProduction implements Mechanic<ConditionalItemList> {

    @Override
    public String id() {
        return Mechanics.CONDITIONAL_ITEM_PRODUCTION;
    }

    @Override
    public boolean check(TownContext townContext, ConditionalItemList configContext) {

        // Check if at least one recipe can be processed
        // Get the main container inventory (the first one in the map)
        if (townContext.loadedStructure.inventory == null || townContext.loadedStructure.inventory.isEmpty()) {
            return false;
        }

        // Check if recipes are configured
        if (configContext.recipes == null || configContext.recipes.isEmpty()) {
            return false;
        }

        Inventory inventory = townContext.loadedStructure.inventory.values().iterator().next();

        for (int i = 0; i < configContext.recipes.size(); i++) {
            ConditionalItemList.Recipe recipe = configContext.recipes.get(i);
            boolean hasInput = hasRequiredInput(inventory, recipe.input);
            if (hasInput) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void postAction(TownContext townContext, ConditionalItemList configContext) {

        // Get the main container inventory
        if (townContext.loadedStructure.inventory == null || townContext.loadedStructure.inventory.isEmpty()) {
            return;
        }

        // Check if recipes are configured
        if (configContext.recipes == null || configContext.recipes.isEmpty()) {
            return;
        }

        Inventory inventory = townContext.loadedStructure.inventory.values().iterator().next();

        // Sort recipes by priority (highest first)
        List<ConditionalItemList.Recipe> sortedRecipes = new ArrayList<>(configContext.recipes);
        sortedRecipes.sort((a, b) -> Integer.compare(
            b.priority != null ? b.priority : 0,
            a.priority != null ? a.priority : 0
        ));


        // Process each recipe that has the required input
        for (int i = 0; i < sortedRecipes.size(); i++) {
            ConditionalItemList.Recipe recipe = sortedRecipes.get(i);
            if (hasRequiredInput(inventory, recipe.input)) {
                // Consume/damage input item
                consumeInput(inventory, recipe.input);

                // Produce output item
                ItemStack output = recipe.output.toItemStack();
                Set<ItemStack> outputSet = new HashSet<>();
                outputSet.add(output);
                TownyCivs.injector.getInstance(StructureInventoryService.class)
                    .addItemProduction(townContext.loadedStructure, outputSet);
            } else {
            }
        }

    }

    /**
     * Check if the inventory has the required input item
     */
    private boolean hasRequiredInput(Inventory inventory, ItemList.ConfigItem input) {
        // Strip namespace (minecraft:) if present, then uppercase
        String materialName = input.material.replace("minecraft:", "").toUpperCase();
        Material material = Material.getMaterial(materialName);

        if (material == null) {
            return false;
        }

        int requiredAmount = input.amount != null ? input.amount : 1;
        int foundAmount = 0;

        for (ItemStack item : inventory.getContents()) {
            if (item != null && item.getType() == material) {
                foundAmount += item.getAmount();
                if (foundAmount >= requiredAmount) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Consume or damage the input item
     */
    private void consumeInput(Inventory inventory, ItemList.ConfigItem input) {
        // Strip namespace (minecraft:) if present, then uppercase
        String materialName = input.material.replace("minecraft:", "").toUpperCase();
        Material material = Material.getMaterial(materialName);

        if (material == null) {
            return;
        }

        int requiredAmount = input.amount != null ? input.amount : 1;
        boolean shouldConsume = input.consumeItem != null && input.consumeItem;
        Integer damageAmount = input.damageAmount;

        // Find and consume/damage the item
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack item = inventory.getItem(i);
            if (item != null && item.getType() == material) {
                if (shouldConsume) {
                    // Consume the item
                    if (input.consumeAmount != null && input.consumeAmount > 0) {
                        int toConsume = Math.min(input.consumeAmount, item.getAmount());
                        item.setAmount(item.getAmount() - toConsume);
                        if (item.getAmount() <= 0) {
                            inventory.setItem(i, null);
                        }
                    } else {
                        int toConsume = Math.min(requiredAmount, item.getAmount());
                        item.setAmount(item.getAmount() - toConsume);
                        if (item.getAmount() <= 0) {
                            inventory.setItem(i, null);
                        }
                    }
                    return;
                } else if (damageAmount != null && damageAmount > 0) {
                    // Damage the item
                    if (item.getItemMeta() instanceof Damageable damageable) {
                        int currentDamage = damageable.getDamage();
                        int maxDurability = material.getMaxDurability();
                        int newDamage = currentDamage + damageAmount;

                        if (newDamage >= maxDurability) {
                            // Item breaks
                            inventory.setItem(i, null);
                        } else {
                            damageable.setDamage(newDamage);
                            item.setItemMeta(damageable);
                        }
                    }
                    return;
                }
            }
        }
    }

    @Override
    public ConditionalItemList getNew() {
        return new ConditionalItemList();
    }

    @Override
    public List<ItemStack> getGuiItems(TownContext townContext, ConditionalItemList configContext) {
        List<ItemStack> guiItems = new ArrayList<>();
        MiniMessage mm = MiniMessage.miniMessage();

        // Check if recipes are configured
        if (configContext.recipes == null || configContext.recipes.isEmpty()) {
            // Return empty list if no recipes configured
            return guiItems;
        }


        // Show each recipe as a conversion arrow
        for (int i = 0; i < configContext.recipes.size(); i++) {
            ConditionalItemList.Recipe recipe = configContext.recipes.get(i);

            // Skip recipes with null input or output
            if (recipe == null || recipe.input == null || recipe.output == null) {
                continue;
            }

            try {
                // Create a display item showing the recipe
                ItemStack recipeDisplay = new ItemStack(Material.PAPER);
                recipeDisplay.editMeta(meta -> {
                    meta.displayName(mm.deserialize("<aqua>Conversion Recipe</aqua>"));
                    List<Component> lore = new ArrayList<>();

                    // Input
                    String inputName = recipe.input.material.replace("minecraft:", "");
                    lore.add(mm.deserialize("<yellow>Input: " + inputName + " x" + (recipe.input.amount != null ? recipe.input.amount : 1) + "</yellow>"));
                    if (recipe.input.consumeItem != null && recipe.input.consumeItem) {
                        lore.add(mm.deserialize("<red>  (Consumed)</red>"));
                    } else if (recipe.input.damageAmount != null) {
                        lore.add(mm.deserialize("<gold>  (Damage: " + recipe.input.damageAmount + ")</gold>"));
                    }

                    lore.add(mm.deserialize("<gray>     ⬇</gray>"));

                    // Output
                    String outputName = recipe.output.material.replace("minecraft:", "");
                    lore.add(mm.deserialize("<green>Output: " + outputName + " x" + (recipe.output.amount != null ? recipe.output.amount : 1) + "</green>"));

                    meta.lore(lore);
                });
                guiItems.add(recipeDisplay);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return guiItems;
    }
}

