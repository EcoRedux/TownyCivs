package cz.neumimto.towny.townycivs.mechanics;

import cz.neumimto.towny.townycivs.StructureInventoryService;
import cz.neumimto.towny.townycivs.TownyCivs;
import cz.neumimto.towny.townycivs.mechanics.common.ItemList;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;

import java.util.HashSet;
import java.util.Set;

public class ItemProduction implements Mechanic<ItemList> {


    @Override
    public String id() {
        return Mechanics.ITEM_PRODUCTION;
    }

    @Override
    public boolean check(TownContext townContext, ItemList configContext) {
        // Enhanced debugging to identify config issues
        String structureName = (townContext.loadedStructure != null ? townContext.loadedStructure.structureDef.name : "unknown");

        if (configContext == null) {
            TownyCivs.logger.warning("ItemProduction check: configContext is completely null for structure: " + structureName);
            TownyCivs.logger.warning("  -> This means the 'Production' section with 'Mechanic: item' is missing from the .conf file");
            TownyCivs.logger.warning("  -> Expected format: Production: [ { Mechanic: item, Value: { Items: [...] } } ]");
            return true;
        }

        if (configContext.configItems == null) {
            TownyCivs.logger.warning("ItemProduction check: configItems is null for structure: " + structureName);
            TownyCivs.logger.warning("  -> This means the 'Items' array is missing from the Production.Value section");
            TownyCivs.logger.warning("  -> Check your .conf file for: Value: { Items: [ { Material: \"...\", Amount: ... } ] }");
            return true;
        }

        if (configContext.configItems.isEmpty()) {
            TownyCivs.logger.warning("ItemProduction check: configItems is empty for structure: " + structureName);
            TownyCivs.logger.warning("  -> The 'Items' array exists but contains no items");
            return true;
        }

        // Check if required items are present in inventory before allowing production
        if (!checkRequiredItems(townContext)) {
            TownyCivs.logger.info("ItemProduction check: Required items not available for structure: " + structureName + " - production blocked");
            return false; // Block production if required items are missing
        }

        // Debug each item configuration
        TownyCivs.logger.info("ItemProduction check: Found " + configContext.configItems.size() + " items for structure: " + structureName);
        for (int i = 0; i < configContext.configItems.size(); i++) {
            ItemList.ConfigItem item = configContext.configItems.get(i);
            if (item == null) {
                TownyCivs.logger.warning("  -> Item " + i + " is null");
                continue;
            }

            if (item.material == null || item.material.isEmpty()) {
                TownyCivs.logger.warning("  -> Item " + i + ": Material is null or empty");
                continue;
            }

            TownyCivs.logger.info("  -> Item " + i + ": Material=" + item.material +
                               ", Amount=" + (item.amount != null ? item.amount : "1 (default)") +
                               ", CustomModelData=" + (item.customModelData != null ? item.customModelData : "none") +
                               ", CustomName=" + (item.customName != null ? item.customName : "none"));
        }

        //todo TownyCivs.injector.getInstance(StructureInventoryService.class).canTakeProducedItems(townContext.loadedStructure, itemStackSet);
        return true;
    }

    /**
     * Check if all required items (from upkeep mechanics) are present in the structure's inventory
     */
    private boolean checkRequiredItems(TownContext townContext) {
        if (townContext.loadedStructure == null || townContext.loadedStructure.structureDef == null) {
            return true; // No structure to check, allow production
        }

        String structureName = townContext.loadedStructure.structureDef.name;

        // Print all upkeep mechanic ids and their config for debugging
            TownyCivs.logger.info("[DEBUG] Upkeep mechanics for structure '" + structureName + "':");
            for (var upkeepPair : townContext.loadedStructure.structureDef.upkeep) {
                if (upkeepPair.mechanic != null) {
                    TownyCivs.logger.info("[DEBUG]   Mechanic id: " + upkeepPair.mechanic.id());
                    TownyCivs.logger.info("[DEBUG]   Config value: " + String.valueOf(upkeepPair.configValue));
                } else {
                    TownyCivs.logger.info("[DEBUG]   Mechanic is null");
                }
            }

        // Check all upkeep mechanics for item requirements
        if (townContext.loadedStructure.structureDef.upkeep != null) {
            for (var upkeepPair : townContext.loadedStructure.structureDef.upkeep) {
                String id = upkeepPair.mechanic != null ? upkeepPair.mechanic.id() : "null";
                // Accept both 'item_required' and 'upkeep' (or whatever your config uses)
                if ("item_required".equals(id) || "upkeep".equals(id) || Mechanics.UPKEEP.equals(id)) {
                    try {
                        ItemList itemRequirements = (ItemList) upkeepPair.configValue;
                        if (itemRequirements != null && itemRequirements.configItems != null && !itemRequirements.configItems.isEmpty()) {
                            Set<ItemStack> requiredItems = new HashSet<>();
                            for (ItemList.ConfigItem configItem : itemRequirements.configItems) {
                                if (configItem != null) {
                                    try {
                                        ItemStack requiredItem = configItem.toItemStack();
                                        if (requiredItem != null) {
                                            requiredItems.add(requiredItem);
                                            TownyCivs.logger.info("[DEBUG] ItemProduction: Checking for required item: " +
                                                requiredItem.getAmount() + "x " + requiredItem.getType() +
                                                " for structure: " + structureName);
                                        }
                                    } catch (Exception e) {
                                        TownyCivs.logger.warning("[DEBUG] ItemProduction: Error creating required ItemStack for structure: " +
                                            structureName + " - " + e.getMessage());
                                    }
                                }
                            }
                            if (!requiredItems.isEmpty()) {
                                TownyCivs.logger.info("[DEBUG] Calling checkUpkeep for " + requiredItems.size() + " items");
                                boolean hasRequiredItems = TownyCivs.injector.getInstance(StructureInventoryService.class)
                                    .checkUpkeep(townContext, requiredItems);
                                TownyCivs.logger.info("[DEBUG] checkUpkeep result: " + hasRequiredItems);
                                if (!hasRequiredItems) {
                                    playMissingItemsAlarm(townContext);
                                    TownyCivs.logger.warning("ItemProduction: Missing required items for structure: " + structureName);
                                    for (ItemStack required : requiredItems) {
                                        TownyCivs.logger.warning("  -> Missing: " + required.getAmount() + "x " + required.getType());
                                    }
                                    return false;
                                } else {
                                    TownyCivs.logger.info("ItemProduction: All required items available for structure: " + structureName);
                                }
                            }
                        }
                    } catch (Exception e) {
                        TownyCivs.logger.warning("ItemProduction: Error checking required items for structure: " +
                            structureName + " - " + e.getMessage());
                    }
                }
            }
        }
        return true; // All checks passed or no requirements found
    }

    @Override
    public void postAction(TownContext townContext, ItemList configContext) {
        // Only run postAction if check previously passed
        // Enhanced debugging to identify config issues
        String structureName = (townContext.loadedStructure != null ? townContext.loadedStructure.structureDef.name : "unknown");

        if (configContext == null) {
            TownyCivs.logger.warning("ItemProduction postAction: configContext is completely null for structure: " + structureName);
            TownyCivs.logger.warning("  -> Cannot produce items - Production section missing from .conf file");
            return;
        }

        if (configContext.configItems == null) {
            TownyCivs.logger.warning("ItemProduction postAction: configItems is null for structure: " + structureName);
            TownyCivs.logger.warning("  -> Cannot produce items - Items array missing from Production.Value section");
            return;
        }

        if (configContext.configItems.isEmpty()) {
            TownyCivs.logger.warning("ItemProduction postAction: configItems is empty for structure: " + structureName);
            TownyCivs.logger.warning("  -> Cannot produce items - Items array is empty");
            return;
        }

        Set<ItemStack> itemStackSet = new HashSet<>();
        for (int i = 0; i < configContext.configItems.size(); i++) {
            ItemList.ConfigItem configItem = configContext.configItems.get(i);
            if (configItem == null) {
                TownyCivs.logger.warning("ItemProduction postAction: Item " + i + " is null for structure: " + structureName);
                continue;
            }

            try {
                ItemStack itemStack = configItem.toItemStack();
                if (itemStack != null) {
                    itemStackSet.add(itemStack);
                    TownyCivs.logger.info("ItemProduction postAction: Successfully created " + itemStack.getAmount() + "x " + itemStack.getType() + " for structure: " + structureName);
                } else {
                    TownyCivs.logger.warning("ItemProduction postAction: Failed to create ItemStack from config item " + i + " for structure: " + structureName);
                }
            } catch (Exception e) {
                TownyCivs.logger.warning("ItemProduction postAction: Error creating ItemStack from config item " + i + " for structure: " + structureName + " - " + e.getMessage());
            }
        }

        if (!itemStackSet.isEmpty()) {
            TownyCivs.logger.info("ItemProduction postAction: Adding " + itemStackSet.size() + " different item types to structure inventory for: " + structureName);
            TownyCivs.injector.getInstance(StructureInventoryService.class).addItemProduction(townContext.loadedStructure, itemStackSet);
        } else {
            TownyCivs.logger.warning("ItemProduction postAction: No valid items to produce for structure: " + structureName);
        }

        // Now process item damage and consumption for upkeep items after production
        if (townContext.loadedStructure != null && townContext.loadedStructure.structureDef != null && townContext.loadedStructure.structureDef.upkeep != null) {
            for (var upkeepPair : townContext.loadedStructure.structureDef.upkeep) {
                String id = upkeepPair.mechanic != null ? upkeepPair.mechanic.id() : "null";
                if ("item_required".equals(id) || "upkeep".equals(id) || Mechanics.UPKEEP.equals(id)) {
                    try {
                        ItemList itemRequirements = (ItemList) upkeepPair.configValue;
                        if (itemRequirements != null && itemRequirements.configItems != null && !itemRequirements.configItems.isEmpty()) {
                            processItemDamageAndConsumption(townContext, itemRequirements);
                        }
                    } catch (Exception e) {
                        TownyCivs.logger.warning("ItemProduction postAction: Error processing item damage/consumption for structure: " + structureName + " - " + e.getMessage());
                    }
                }
            }
        }
    }


    @Override
    public ItemList getNew() {
        return new ItemList();
    }

    /**
     * Play sound alarm when required items are missing from the structure
     */
    private void playMissingItemsAlarm(TownContext townContext) {
        if (townContext.loadedStructure == null || townContext.loadedStructure.center == null) {
            return;
        }

        Location structureLocation = townContext.loadedStructure.center;
        String structureName = townContext.loadedStructure.structureDef.name;

        // Play noteblock sound alarm at the structure location
        TownyCivs.MORE_PAPER_LIB.scheduling().regionSpecificScheduler(structureLocation)
            .run(() -> {
                World world = structureLocation.getWorld();
                if (world != null) {
                    // Play multiple noteblock sounds for alarm effect
                    world.playSound(structureLocation, Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);

                    // Schedule additional alarm sounds with delay
                    Bukkit.getScheduler().runTaskLater(TownyCivs.INSTANCE, () ->
                        world.playSound(structureLocation, Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.7f), 10L); // 0.5 seconds later

                    Bukkit.getScheduler().runTaskLater(TownyCivs.INSTANCE, () ->
                        world.playSound(structureLocation, Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f), 20L); // 1 second later

                    TownyCivs.logger.info("ItemProduction: Played missing items alarm for structure: " + structureName +
                        " at location: " + structureLocation.getBlockX() + "," + structureLocation.getBlockY() + "," + structureLocation.getBlockZ());
                }
            });
    }

    /**
     * Process item damage and consumption for upkeep items based on configuration
     */
    private void processItemDamageAndConsumption(TownContext townContext, ItemList itemRequirements) {
        if (townContext.loadedStructure == null || itemRequirements == null || itemRequirements.configItems == null) {
            return;
        }

        String structureName = townContext.loadedStructure.structureDef.name;

        // Process each required item for damage/consumption
        for (ItemList.ConfigItem configItem : itemRequirements.configItems) {
            if (configItem == null) continue;

            try {
                Material itemMaterial = Material.matchMaterial(configItem.material);
                if (itemMaterial == null) continue;

                // Check if this item should take damage or be consumed
                boolean shouldDamage = shouldItemTakeDamage(configItem, itemMaterial);
                boolean shouldConsume = shouldItemBeConsumed(configItem, itemMaterial);

                if (shouldDamage || shouldConsume) {
                    processItemUsage(townContext, configItem, itemMaterial, shouldDamage, shouldConsume, structureName);
                }

            } catch (Exception e) {
                TownyCivs.logger.warning("ItemProduction: Error processing item damage/consumption for structure: " +
                    structureName + " - " + e.getMessage());
            }
        }
    }

    /**
     * Determine if an item should take damage based on configuration and item properties
     */
    private boolean shouldItemTakeDamage(ItemList.ConfigItem configItem, Material itemMaterial) {
        // If ConsumeItem is explicitly set to true, prioritize consumption over damage
        if (configItem.consumeItem != null && configItem.consumeItem) {
            return false; // Don't damage if we're consuming
        }

        // If ConsumeItem is explicitly set to false, or if DamageAmount is specified, use damage
        if ((configItem.consumeItem != null && !configItem.consumeItem) || configItem.damageAmount != null) {
            return itemMaterial.getMaxDurability() > 0; // Only damage if item can be damaged
        }

        // Legacy behavior: Check if item has a Fuel value (indicates it should take damage)
        if (configItem.fuel != null && configItem.fuel > 0) {
            return itemMaterial.getMaxDurability() > 0;
        }

        // Default: Check if item is naturally damageable (tools, armor, etc.)
        return itemMaterial.getMaxDurability() > 0;
    }

    /**
     * Determine if an item should be consumed based on configuration
     */
    private boolean shouldItemBeConsumed(ItemList.ConfigItem configItem, Material itemMaterial) {
        // If ConsumeItem is explicitly set to true, consume the item
        if (configItem.consumeItem != null && configItem.consumeItem) {
            return true;
        }

        // If ConsumeItem is explicitly set to false, don't consume
        if (configItem.consumeItem != null && !configItem.consumeItem) {
            return false;
        }

        // If ConsumeAmount is specified, consume the item
        if (configItem.consumeAmount != null && configItem.consumeAmount > 0) {
            return true;
        }

        // Legacy behavior: If Amount is specified and > 1, it should be consumed instead of damaged
        if (configItem.amount != null && configItem.amount > 1) {
            return true;
        }

        // Default: Consumable items (food, potions, etc.) should be consumed
        return itemMaterial.isEdible() ||
               itemMaterial.name().contains("POTION") ||
               itemMaterial.name().contains("BUCKET") ||
               itemMaterial == Material.COAL ||
               itemMaterial == Material.CHARCOAL;
    }

    /**
     * Process the actual usage (damage or consumption) of an item
     */
    private void processItemUsage(TownContext townContext, ItemList.ConfigItem configItem, Material itemMaterial,
                                  boolean shouldDamage, boolean shouldConsume, String structureName) {

        // Use the existing processUpkeep method to handle the item usage
        Set<ItemStack> usageItems = new HashSet<>();

        if (shouldConsume) {
            // Determine consumption amount based on configuration priority
            int consumeAmount = 1; // default

            if (configItem.consumeAmount != null && configItem.consumeAmount > 0) {
                consumeAmount = configItem.consumeAmount;
            } else if (configItem.fuel != null && configItem.fuel > 0) {
                consumeAmount = configItem.fuel;
            } else if (configItem.amount != null && configItem.amount > 1) {
                consumeAmount = 1; // Consume 1 item from the stack
            }

            ItemStack consumeItem = new ItemStack(itemMaterial, consumeAmount);

            if (configItem.customModelData != null) {
                consumeItem.editMeta(meta -> meta.setCustomModelData(configItem.customModelData));
            }

            usageItems.add(consumeItem);
            TownyCivs.logger.info("ItemProduction: Consuming " + consumeAmount + "x " + itemMaterial +
                " for structure: " + structureName + " (ConsumeItem=" + configItem.consumeItem +
                ", ConsumeAmount=" + configItem.consumeAmount + ")");

        } else if (shouldDamage) {
            // Determine damage amount based on configuration priority
            int damageAmount = 1; // default

            if (configItem.damageAmount != null && configItem.damageAmount > 0) {
                damageAmount = configItem.damageAmount;
            } else if (configItem.fuel != null && configItem.fuel > 0) {
                damageAmount = configItem.fuel;
            }

            // Create a temporary config item for damage processing
            ItemList.ConfigItem damageConfig = new ItemList.ConfigItem();
            damageConfig.material = configItem.material;
            damageConfig.customModelData = configItem.customModelData;
            damageConfig.amount = damageAmount; // This will be used as damage amount
            damageConfig.fuel = damageAmount;

            usageItems.add(damageConfig.toItemStack());
            TownyCivs.logger.info("ItemProduction: Applying " + damageAmount + " damage to " + itemMaterial +
                " for structure: " + structureName + " (DamageAmount=" + configItem.damageAmount + ")");
        }

        if (!usageItems.isEmpty()) {
            // Use the existing processUpkeep method to handle the item usage
            TownyCivs.injector.getInstance(StructureInventoryService.class)
                .processUpkeep(townContext.loadedStructure, usageItems);
        }
    }
}
