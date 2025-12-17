package cz.neumimto.towny.townycivs;

import cz.neumimto.towny.townycivs.mechanics.TownContext;
import cz.neumimto.towny.townycivs.mechanics.common.ItemList;
import cz.neumimto.towny.townycivs.model.LoadedStructure;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

@Singleton
public class StructureInventoryService {

    private static Map<Location, UUID> structsAndPlayers = new ConcurrentHashMap<>();
    private static Map<UUID, StructAndInv> playersAndInv = new ConcurrentHashMap<>();
    @Inject
    private ItemService itemService;

    private static void putToInventory(Inventory inventory, Set<ItemStack> itemStackSet, CountDownLatch cdl, Set<ItemStack> couldNotFit) {
        try {
            HashMap<Integer, ItemStack> map = inventory.addItem(itemStackSet.toArray(ItemStack[]::new));
            couldNotFit.addAll(map.values());
        } finally {
            cdl.countDown();
        }
    }


    private void checkItemsForUpkeepAndWait(Inventory inventory1, Map<Material, AmountAndModel> fulfilled, CountDownLatch cdl) {
        try {
            checkItemsForUpkeep(inventory1, fulfilled);
        } finally {
            cdl.countDown();
        }
    }

    private void checkItemsForUpkeep(Inventory inventory1, Map<Material, AmountAndModel> fulfilled) {
        ItemStack inventoryBlocker = itemService.getInventoryBlocker();

        for (ItemStack i : inventory1.getContents()) {
            if (i == null) {
                continue;
            }
            if (i.equals(inventoryBlocker)) {
                break;
            }


            AmountAndModel amountAndModel = fulfilled.get(i.getType());
            if (amountAndModel == null) {
                continue;
            }


            Integer modelData = null;
            ItemMeta itemMeta = i.getItemMeta();

            if (itemMeta.hasCustomModelData()) {
                modelData = itemMeta.getCustomModelData();
            }

            if (!Objects.equals(amountAndModel.model, modelData)) {
                continue;
            }

            if (itemMeta instanceof Damageable d && i.getType().getMaxDurability() > 0) {
                if (d.getDamage() + 1 <= i.getType().getMaxDurability()) {
                    fulfilled.remove(i.getType());
                }
            } else {
                fulfilled.computeIfPresent(i.getType(), (material, a) -> {
                    a.amount = a.amount - i.getAmount();
                    return a.amount <= 0 ? null : a;
                });
            }
        }

    }

    public void openInventory(Player player, Location location, LoadedStructure structure) {
        structsAndPlayers.put(location, player.getUniqueId());

        Inventory inv = getStructureInventory(structure, location);
        playersAndInv.put(player.getUniqueId(), new StructAndInv(structure.uuid, inv, location));

        TownyCivs.MORE_PAPER_LIB.scheduling().entitySpecificScheduler(player)
                .run(() -> player.openInventory(inv), null);
    }

    public void closeInvenotory(Player player) {
        StructAndInv sai = playersAndInv.remove(player.getUniqueId());
        if (sai != null) {
            structsAndPlayers.remove(sai.location);
        }
    }

    public void addItemProduction(LoadedStructure loadedStructure, Set<ItemStack> itemStackSet) {
        Map<Location, Inventory> inventories = loadedStructure.inventory;
        Set<ItemStack> remaining = new HashSet<>();
        for (Map.Entry<Location, Inventory> e : inventories.entrySet()) {
            UUID uuid = structsAndPlayers.get(e.getKey());
            Inventory inventory1 = e.getValue();
            if (uuid != null) {
                Player vplayer = Bukkit.getPlayer(uuid);
                addItemProductionAndWait(vplayer, inventory1, itemStackSet, remaining);

            } else {
                HashMap<Integer, ItemStack> map = inventory1.addItem(itemStackSet.toArray(ItemStack[]::new));
                remaining.addAll(map.values());
            }
            if (remaining.isEmpty()) {
                break;
            }
        }
    }

    private void addItemProductionAndWait(Player player, Inventory inventory, Set<ItemStack> itemStackSet, Set<ItemStack> couldNotFit) {
        CountDownLatch cdl = new CountDownLatch(1);

        TownyCivs.MORE_PAPER_LIB.scheduling().entitySpecificScheduler(player)
                .run(() -> putToInventory(inventory, itemStackSet, cdl, couldNotFit),
                        () -> putToInventory(inventory, itemStackSet, cdl, couldNotFit)
                );
        try {
            cdl.await(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            TownyCivs.logger.log(Level.WARNING, "An error occurred while waiting for a lock. Could not process addItemProduction ", e);
        }
    }

    private Inventory getStructureInventory(LoadedStructure loadedStructure, Location location) {
        Inventory inventory = loadedStructure.inventory.get(location);
        return inventory;
    }

    private Inventory createStructureInventory(LoadedStructure structure) {
        Inventory inventory = Bukkit.getServer().createInventory(null, 27, Component.text(structure.structureDef.name));
        if (structure.structureDef.inventorySize > 0) {
            for (int i = structure.structureDef.inventorySize; i < inventory.getSize(); i++) {
                inventory.setItem(i, itemService.getInventoryBlocker());
            }
        }
        return inventory;
    }

    public Inventory loadStructureInventory(LoadedStructure structure, Location location, ItemStack[] itemStacks) {
        Inventory structureInventory = createStructureInventory(structure);
        structureInventory.addItem(itemStacks);
        structure.inventory.put(location, structureInventory);
        return structureInventory;
    }

    public UUID getPlayerViewingInventory(LoadedStructure structure) {
        return structsAndPlayers.get(structure.uuid);
    }

    public boolean checkUpkeep(TownContext townContext, ItemList itemList) {
        // Build a map of required items (Material + model) and their required amounts from the config-aware ItemList
        Map<Material, AmountAndModel> fulfilled = new HashMap<>();

        for (ItemList.ConfigItem configItem : itemList.configItems) {
            ItemStack itemStack = configItem.toItemStack();
            ItemMeta itemMeta = itemStack.getItemMeta();
            Integer model = null;
            if (itemMeta != null && itemMeta.hasCustomModelData()) {
                model = itemMeta.getCustomModelData();
            }

            // Use config values with defaults
            boolean consume = configItem.consumeItem != null ? configItem.consumeItem : false;
            int consumeAmount = configItem.consumeAmount != null ? configItem.consumeAmount : 0;
            int damageAmount = configItem.damageAmount != null ? configItem.damageAmount : 0;

            fulfilled.put(itemStack.getType(), new AmountAndModel(itemStack.getAmount(), model, consume, consumeAmount, damageAmount));
        }

        // ← TOGGLE LOGIC: Determine if we need ALL items (AND) or ANY item (OR)
        boolean requireAll = itemList.requireAll != null ? itemList.requireAll : true;  // Default: true (AND logic)
        int initialSize = fulfilled.size();

        // For each inventory, check if it fulfills the config-aware requirements
        for (Map.Entry<Location, Inventory> e : townContext.loadedStructure.inventory.entrySet()) {
            UUID uuid = structsAndPlayers.get(e.getKey());
            Inventory inventory1 = e.getValue();


            if (uuid != null) {
                Player vplayer = Bukkit.getPlayer(uuid);
                checkItemsForUpkeepAndWait(vplayer, inventory1, fulfilled);

            } else {
                checkItemsForUpkeep(inventory1, fulfilled);
            }

            // ← TOGGLE LOGIC: Check based on RequireAll setting
            if (requireAll) {
                // AND logic: ALL items must be fulfilled
                if (!fulfilled.isEmpty()) {
                    return false;  // Some items still missing
                }
            } else {
                // OR logic: ANY ONE item must be fulfilled
                if (fulfilled.size() < initialSize) {
                    return true;  // At least one item was found and removed from fulfilled
                }
            }

        }
        
        // For OR logic, if we get here, no items were found
        if (!requireAll) {
            return false;
        }
        
        return true;
    }

    private void checkItemsForUpkeepAndWait(Player player, Inventory inventory1,Map<Material, AmountAndModel> fulfilled) {
        CountDownLatch cdl = new CountDownLatch(1);
        TownyCivs.MORE_PAPER_LIB.scheduling().entitySpecificScheduler(player)
                .run(
                        () -> checkItemsForUpkeepAndWait(inventory1, fulfilled, cdl),
                        () -> checkItemsForUpkeepAndWait(inventory1, fulfilled, cdl)
                );
        try {
            cdl.await(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            TownyCivs.logger.log(Level.WARNING, "Could not wait for lock checkItemsForUpkeepAndWait", e);
        }
    }

    // Config-aware processUpkeep method that uses ItemList
    public void processUpkeep(LoadedStructure loadedStructure, ItemList itemList) {
        Map<Material, AmountAndModel> fulfilled = new HashMap<>();

        for (ItemList.ConfigItem configItem : itemList.configItems) {
            ItemStack itemStack = configItem.toItemStack();
            ItemMeta itemMeta = itemStack.getItemMeta();
            Integer model = null;
            if (itemMeta != null && itemMeta.hasCustomModelData()) {
                model = itemMeta.getCustomModelData();
            }

            // Use config values with defaults
            boolean consume = configItem.consumeItem != null ? configItem.consumeItem : false;
            int consumeAmount = configItem.consumeAmount != null ? configItem.consumeAmount : 0;
            int damageAmount = configItem.damageAmount != null ? configItem.damageAmount : 0;

            fulfilled.put(itemStack.getType(), new AmountAndModel(itemStack.getAmount(), model, consume, consumeAmount, damageAmount));
        }

        for (Map.Entry<Location, Inventory> e : loadedStructure.inventory.entrySet()) {
            UUID uuid = structsAndPlayers.get(e.getKey());
            Inventory inventory = e.getValue();

            if (uuid != null) {
                Player vplayer = Bukkit.getPlayer(uuid);
                processUpkeepConfigAwareAndWait(vplayer, inventory, fulfilled);
            } else {
                processUpkeepConfigAware(inventory, fulfilled);
            }
        }
    }

    private void processUpkeepConfigAwareAndWait(Player player, Inventory inventory, Map<Material, AmountAndModel> fulfilled) {
        CountDownLatch cdl = new CountDownLatch(1);
        TownyCivs.MORE_PAPER_LIB.scheduling().entitySpecificScheduler(player)
                .run(
                        () -> processUpkeepConfigAwareAndWait(inventory, fulfilled, cdl),
                        () -> processUpkeepConfigAwareAndWait(inventory, fulfilled, cdl)
                );
        try {
            cdl.await(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            TownyCivs.logger.log(Level.WARNING, "Could not wait for lock processUpkeepConfigAwareAndWait", e);
        }
    }

    private void processUpkeepConfigAwareAndWait(Inventory inventory, Map<Material, AmountAndModel> fulfilled, CountDownLatch cdl) {
        try {
            processUpkeepConfigAware(inventory, fulfilled);
        } finally {
            cdl.countDown();
        }
    }

    private void processUpkeepConfigAware(Inventory inventory, Map<Material, AmountAndModel> fulfilled) {
        for (int i = 0; i < inventory.getContents().length; i++) {
            ItemStack content = inventory.getContents()[i];
            if (content == null) {
                continue;
            }
            AmountAndModel amountAndModel = fulfilled.get(content.getType());
            if (amountAndModel == null) {
                continue;
            }
            Integer modelData = null;
            ItemMeta itemMeta = content.getItemMeta();

            if (itemMeta.hasCustomModelData()) {
                modelData = itemMeta.getCustomModelData();
            }

            if (!Objects.equals(amountAndModel.model, modelData)) {
                continue;
            }

            // Handle damageable items using config values
            if (itemMeta instanceof Damageable d && content.getType().getMaxDurability() > 0) {
                // Use damageAmount from config if available, otherwise fall back to amount
                int damageToApply = amountAndModel.damageAmount > 0 ? amountAndModel.damageAmount : amountAndModel.amount;
                d.setDamage(d.getDamage() + damageToApply);

                if (d.getDamage() >= content.getType().getMaxDurability()) {
                    inventory.setItem(i, null);
                } else {
                    content.setItemMeta(d);
                }
                fulfilled.remove(content.getType());
            } else {
                // Handle consumable items using config values
                if (amountAndModel.consume) {
                    // Use consumeAmount from config if available, otherwise use amount
                    int amountToConsume = amountAndModel.consumeAmount > 0 ? amountAndModel.consumeAmount : amountAndModel.amount;
                    int currentAmount = content.getAmount();

                    if (currentAmount > amountToConsume) {
                        content.setAmount(currentAmount - amountToConsume);
                        fulfilled.remove(content.getType());
                    } else {
                        inventory.setItem(i, null);
                        amountAndModel.amount -= currentAmount;
                    }
                } else {
                    // Item is not consumed, just check if it exists (for upkeep checking)
                    fulfilled.remove(content.getType());
                }
            }
        }
    }

    private void processUpkeepAndWait(Player player, Inventory inventory, Map<Material, AmountAndModel> fulfilled) {
        CountDownLatch cdl = new CountDownLatch(1);
        TownyCivs.MORE_PAPER_LIB.scheduling().entitySpecificScheduler(player)
                .run(
                        () -> processUpkeepAndWait(inventory, fulfilled, cdl),
                        () -> processUpkeepAndWait(inventory, fulfilled, cdl)
                );
        try {
            cdl.await(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            TownyCivs.logger.log(Level.WARNING, "Could not wait for lock checkItemsForUpkeepAndWait", e);
        }
    }

    private void processUpkeepAndWait(Inventory inventory, Map<Material, AmountAndModel> fulfilled, CountDownLatch cdl) {
        try {
            processUpkeep(inventory, fulfilled);
        } finally {
            cdl.countDown();
        }
    }

    private void processUpkeep(Inventory inventory, Map<Material, AmountAndModel> fulfilled) {
        for (int i = 0; i < inventory.getContents().length; i++) {
            ItemStack content = inventory.getContents()[i];
            if (content == null) {
                continue;
            }
            AmountAndModel amountAndModel = fulfilled.get(content.getType());
            if (amountAndModel == null) {
                continue;
            }
            Integer modelData = null;
            ItemMeta itemMeta = content.getItemMeta();

            if (itemMeta.hasCustomModelData()) {
                modelData = itemMeta.getCustomModelData();
            }

            if (!Objects.equals(amountAndModel.model, modelData)) {
                continue;
            }

            if (itemMeta instanceof Damageable d && content.getType().getMaxDurability() > 0) {
                d.setDamage(d.getDamage() + amountAndModel.amount);
                if (d.getDamage() >= content.getType().getMaxDurability()) {
                    inventory.setItem(i, null);
                } else {
                    content.setItemMeta(d);
                    fulfilled.remove(content.getType());
                }
            } else {
                int amount = content.getAmount();

                if (amount > amountAndModel.amount) {
                    content.setAmount(amount - amountAndModel.amount);
                    fulfilled.remove(content.getType());

                } else {
                    inventory.setItem(i, null);
                    amountAndModel.amount -= amount;
                }

            }
        }

    }

    public boolean anyInventoryIsBeingAccessed(LoadedStructure structure) {
        for (Map.Entry<Location, Inventory> e : structure.inventory.entrySet()) {
            if (structsAndPlayers.containsKey(e.getKey())) {
                return true;
            }
        }
        return false;
    }

    private record StructAndInv(UUID structureId, Inventory inventory, Location location) {
    }

    private static class AmountAndModel {
        int amount;
        Integer model;
        boolean consume;
        int consumeAmount;
        int damageAmount;

        public AmountAndModel(int amount, Integer model, boolean consume, int consumeAmount, int damageAmount) {
            this.amount = amount;
            this.model = model;
            this.consume = consume;
            this.consumeAmount = consumeAmount;
            this.damageAmount = damageAmount;
        }


        @Override
        public String toString() {
            return "AmountAndModel{amount=" + amount + ", model=" + model + ", consume=" + consume + ", consumeAmount=" + consumeAmount + ", damageAmount=" + damageAmount + '}';
        }
    }
}
