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
        System.out.println("[TownyCivs DEBUG] ConditionalItemProduction.check() called for structure: " + townContext.structure.id);

        // Check if at least one recipe can be processed
        // Get the main container inventory (the first one in the map)
        if (townContext.loadedStructure.inventory == null || townContext.loadedStructure.inventory.isEmpty()) {
            System.out.println("[TownyCivs DEBUG] No inventory found!");
            return false;
        }

        // Check if recipes are configured
        if (configContext.recipes == null || configContext.recipes.isEmpty()) {
            System.out.println("[TownyCivs DEBUG] No recipes configured!");
            return false;
        }

        Inventory inventory = townContext.loadedStructure.inventory.values().iterator().next();
        System.out.println("[TownyCivs DEBUG] Checking inventory with " + inventory.getSize() + " slots");
        System.out.println("[TownyCivs DEBUG] Inventory contents:");
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack item = inventory.getItem(i);
            if (item != null && item.getType() != Material.AIR) {
                System.out.println("  Slot " + i + ": " + item.getType() + " x" + item.getAmount());
            }
        }

        System.out.println("[TownyCivs DEBUG] Checking " + configContext.recipes.size() + " recipes");
        for (int i = 0; i < configContext.recipes.size(); i++) {
            ConditionalItemList.Recipe recipe = configContext.recipes.get(i);
            boolean hasInput = hasRequiredInput(inventory, recipe.input);
            System.out.println("[TownyCivs DEBUG] Recipe " + i + " (" + recipe.input.material + "): " + (hasInput ? "HAS INPUT" : "missing input"));
            if (hasInput) {
                System.out.println("[TownyCivs DEBUG] check() returning TRUE - recipe can be processed!");
                return true;
            }
        }

        System.out.println("[TownyCivs DEBUG] check() returning FALSE - no recipes can be processed");
        return false;
    }

    @Override
    public void postAction(TownContext townContext, ConditionalItemList configContext) {
        System.out.println("[TownyCivs DEBUG] ConditionalItemProduction.postAction() called - PRODUCTION RUNNING!");

        // Get the main container inventory
        if (townContext.loadedStructure.inventory == null || townContext.loadedStructure.inventory.isEmpty()) {
            System.out.println("[TownyCivs DEBUG] No inventory in postAction!");
            return;
        }

        // Check if recipes are configured
        if (configContext.recipes == null || configContext.recipes.isEmpty()) {
            System.out.println("[TownyCivs DEBUG] No recipes in postAction!");
            return;
        }

        Inventory inventory = townContext.loadedStructure.inventory.values().iterator().next();

        // Sort recipes by priority (highest first)
        List<ConditionalItemList.Recipe> sortedRecipes = new ArrayList<>(configContext.recipes);
        sortedRecipes.sort((a, b) -> Integer.compare(
            b.priority != null ? b.priority : 0,
            a.priority != null ? a.priority : 0
        ));

        System.out.println("[TownyCivs DEBUG] Processing " + sortedRecipes.size() + " sorted recipes");

        // Process each recipe that has the required input
        for (int i = 0; i < sortedRecipes.size(); i++) {
            ConditionalItemList.Recipe recipe = sortedRecipes.get(i);
            if (hasRequiredInput(inventory, recipe.input)) {
                System.out.println("[TownyCivs DEBUG] Recipe " + i + " MATCHED! Processing...");
                System.out.println("[TownyCivs DEBUG]   Input: " + recipe.input.material + " x" + (recipe.input.amount != null ? recipe.input.amount : 1));
                System.out.println("[TownyCivs DEBUG]   Output: " + recipe.output.material + " x" + (recipe.output.amount != null ? recipe.output.amount : 1));

                // Consume/damage input item
                consumeInput(inventory, recipe.input);
                System.out.println("[TownyCivs DEBUG]   Consumed input item");

                // Produce output item
                ItemStack output = recipe.output.toItemStack();
                Set<ItemStack> outputSet = new HashSet<>();
                outputSet.add(output);
                TownyCivs.injector.getInstance(StructureInventoryService.class)
                    .addItemProduction(townContext.loadedStructure, outputSet);
                System.out.println("[TownyCivs DEBUG]   Added output to production!");
            } else {
                System.out.println("[TownyCivs DEBUG] Recipe " + i + " skipped - no input found");
            }
        }

        System.out.println("[TownyCivs DEBUG] postAction() complete!");
    }

    /**
     * Check if the inventory has the required input item
     */
    private boolean hasRequiredInput(Inventory inventory, ItemList.ConfigItem input) {
        // Strip namespace (minecraft:) if present, then uppercase
        String materialName = input.material.replace("minecraft:", "").toUpperCase();
        Material material = Material.getMaterial(materialName);

        System.out.println("[TownyCivs DEBUG]   hasRequiredInput checking for: " + materialName + " -> Material: " + material);

        if (material == null) {
            System.out.println("[TownyCivs DEBUG]   Material not found!");
            return false;
        }

        int requiredAmount = input.amount != null ? input.amount : 1;
        int foundAmount = 0;

        for (ItemStack item : inventory.getContents()) {
            if (item != null && item.getType() == material) {
                foundAmount += item.getAmount();
                if (foundAmount >= requiredAmount) {
                    System.out.println("[TownyCivs DEBUG]   Found enough! Required: " + requiredAmount + ", Found: " + foundAmount);
                    return true;
                }
            }
        }

        System.out.println("[TownyCivs DEBUG]   Not enough found. Required: " + requiredAmount + ", Found: " + foundAmount);
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
            System.out.println("[TownyCivs DEBUG] consumeInput: Material not found for " + materialName);
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

        System.out.println("[TownyCivs DEBUG] ConditionalItemProduction.getGuiItems() called");
        System.out.println("[TownyCivs DEBUG] configContext: " + configContext);
        System.out.println("[TownyCivs DEBUG] configContext.recipes: " + configContext.recipes);

        // Check if recipes are configured
        if (configContext.recipes == null || configContext.recipes.isEmpty()) {
            System.out.println("[TownyCivs DEBUG] No recipes configured - returning empty list");
            // Return empty list if no recipes configured
            return guiItems;
        }

        System.out.println("[TownyCivs DEBUG] Processing " + configContext.recipes.size() + " recipes");

        // Show each recipe as a conversion arrow
        for (int i = 0; i < configContext.recipes.size(); i++) {
            ConditionalItemList.Recipe recipe = configContext.recipes.get(i);
            System.out.println("[TownyCivs DEBUG] Recipe " + i + ": " + recipe);

            // Skip recipes with null input or output
            if (recipe == null || recipe.input == null || recipe.output == null) {
                System.out.println("[TownyCivs DEBUG] Skipping recipe " + i + " - null input or output");
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
                System.out.println("[TownyCivs DEBUG] Successfully added recipe display item " + i);
            } catch (Exception e) {
                System.out.println("[TownyCivs DEBUG] Error creating recipe display for recipe " + i + ": " + e.getMessage());
                e.printStackTrace();
            }
        }

        System.out.println("[TownyCivs DEBUG] Returning " + guiItems.size() + " GUI items");
        return guiItems;
    }
}

