package cz.neumimto.towny.townycivs;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.TownyMessaging;
import com.palmergames.bukkit.towny.object.Town;
import cz.neumimto.towny.townycivs.config.ConfigurationService;
import cz.neumimto.towny.townycivs.config.Structure;
import cz.neumimto.towny.townycivs.db.Flatfile;
import cz.neumimto.towny.townycivs.db.Storage;
import cz.neumimto.towny.townycivs.mechanics.Mechanic;
import cz.neumimto.towny.townycivs.mechanics.TownContext;
import cz.neumimto.towny.townycivs.mechanics.common.DoubleWrapper;
import cz.neumimto.towny.townycivs.model.LoadedStructure;
import cz.neumimto.towny.townycivs.power.PowerService;
import cz.neumimto.towny.townycivs.model.Region;
import cz.neumimto.towny.townycivs.model.StructureAndCount;
import cz.neumimto.towny.townycivs.schedulers.FoliaScheduler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.BoundingBox;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Singleton
public class StructureService {

    private Map<UUID, LoadedStructure> structures = new ConcurrentHashMap<>();
    private Map<UUID, Set<LoadedStructure>> structuresByTown = new ConcurrentHashMap<>();

    @Inject
    private ConfigurationService configurationService;

    @Inject
    private SubclaimService subclaimService;

    @Inject
    private FoliaScheduler structureScheduler;

    @Inject
    private ManagementService managementService;


    public Map<UUID, Set<LoadedStructure>> getAllStructuresByTown() {
        return structuresByTown;
    }


    public Collection<LoadedStructure> getAllStructures(Town town) {
        return structures.values().stream().filter(a -> a.town.equals(town.getUUID())).collect(Collectors.toSet());
    }

    public ItemStack toItemStack(Structure structure, int count) {
        ItemStack itemStack = new ItemStack(structure.material);
        ItemMeta itemMeta = itemStack.getItemMeta();

        var mm = MiniMessage.miniMessage();
        itemMeta.displayName(mm.deserialize(structure.name));
        itemMeta.setCustomModelData(structure.customModelData);

        List<Component> lore = configurationService.buildStructureLore(structure, count, structure.maxCount);
        itemMeta.lore(lore);
        itemStack.setItemMeta(itemMeta);
        return itemStack;
    }

    public List<StructureAndCount> findTownStructures(Town town) {
        Collection<Structure> allStructures = configurationService.getAll();
        Collection<LoadedStructure> townStructures = getAllStructures(town);

        Map<Structure, Integer> alreadyBuilt = new HashMap<>();
        List<Structure> avalaible = new ArrayList<>();

        for (Structure structure : allStructures) {
            boolean found = false;
            for (LoadedStructure townStructure : townStructures) {
                if (townStructure.structureDef == structure) {
                    alreadyBuilt.merge(structure, 1, Integer::sum);
                    found = true;
                }
            }

            if (!found) {
                avalaible.add(structure);
            }
        }

        List<StructureAndCount> merged = new ArrayList<>();
        for (Map.Entry<Structure, Integer> entry : alreadyBuilt.entrySet()) {
            merged.add(new StructureAndCount(entry.getKey(), entry.getValue()));
        }
        avalaible.sort(Comparator.comparing(o -> o.name));

        for (Structure structure : avalaible) {
            merged.add(new StructureAndCount(structure, 0));
        }

        return merged;
    }

    public StructureAndCount findTownStructureById(Town town, Structure structure) {
        Collection<LoadedStructure> townStructures = getAllStructures(town);
        int count = 0;
        for (LoadedStructure townStructure : townStructures) {
            if (townStructure.structureDef == structure) {
                count++;
            }
        }
        return new StructureAndCount(structure, count);
    }

    public ItemStack buyBlueprint(TownContext townContext) {
        for (Structure.LoadedPair<Mechanic<?>, ?> requirement : townContext.structure.buyRequirements) {
            Object configValue = requirement.configValue;
            var mechanic = (Mechanic<Object>) requirement.mechanic;
            mechanic.postAction(townContext, configValue);
            mechanic.okmessage(townContext, configValue);
        }
        return toBlueprintItemStack(townContext.structure);
    }

    private ItemStack toBlueprintItemStack(Structure structure) {
        ItemStack itemStack = new ItemStack(structure.material);
        itemStack.editMeta(itemMeta -> {
            net.kyori.adventure.text.minimessage.MiniMessage mm = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage();
            itemMeta.displayName(mm.deserialize("Blueprint - " + structure.name));
            itemMeta.setCustomModelData(structure.customModelData);
        });
        return itemStack;
    }

    public boolean canShow(TownContext context) {
        boolean pass = true;

        // Check if structure is buyable at all
        if (context.structure.buyable != null && !context.structure.buyable) {
            return false;
        }

        // Check MaxCount limit (including upgraded versions in the count)
        if (context.structure.maxCount != null) {
            int totalCount = countStructuresInUpgradeChain(context.town, context.structure);
            if (totalCount >= context.structure.maxCount) {
                pass = false;
            }
        }

        for (Structure.LoadedPair<Mechanic<?>, ?> requirement : context.structure.buyRequirements) {
            Object configValue = requirement.configValue;
            var mechanic = (Mechanic<Object>) requirement.mechanic;
            if (!mechanic.check(context, configValue)) {
                pass = false;
            }
        }
        return pass;
    }

    public boolean canBuy(TownContext context) {
        boolean pass = true;

        // Check if structure is buyable at all
        if (context.structure.buyable != null && !context.structure.buyable) {
            context.player.sendMessage("§c" + context.structure.name + " cannot be purchased. It can only be obtained through upgrades.");
            return false;
        }

        // Check MaxCount limit (including upgraded versions in the count)
        if (context.structure.maxCount != null) {
            int totalCount = countStructuresInUpgradeChain(context.town, context.structure);
            if (totalCount >= context.structure.maxCount) {
                context.player.sendMessage("§cYou have reached the maximum number of " + context.structure.name + " structures (" + context.structure.maxCount + "). This includes upgraded versions.");
                pass = false;
            }
        }

        for (Structure.LoadedPair<Mechanic<?>, ?> requirement : context.structure.buyRequirements) {
            Object configValue = requirement.configValue;
            var mechanic = (Mechanic<Object>) requirement.mechanic;
            if (!mechanic.check(context, configValue)) {
                mechanic.nokmessage(context, configValue);
                pass = false;
            }
        }
        return pass;
    }

    /**
     * Counts all structures in an upgrade chain for a given base structure.
     * For example, if checking coal_mine and town has:
     * - 2 coal_mine
     * - 3 advanced_coal_mine (UpgradeFrom: coal_mine)
     * - 1 elite_coal_mine (UpgradeFrom: advanced_coal_mine)
     * Total count = 2 + 3 + 1 = 6
     */
    public int countStructuresInUpgradeChain(Town town, Structure baseStructure) {
        int count = 0;
        Collection<LoadedStructure> townStructures = getAllStructures(town);

        for (LoadedStructure loadedStructure : townStructures) {
            Structure builtStructure = loadedStructure.structureDef;

            // Count the base structure itself
            if (builtStructure.id.equalsIgnoreCase(baseStructure.id)) {
                count++;
            }
            // Count any structure that is an upgrade of this base structure
            else if (isUpgradeOf(builtStructure, baseStructure.id)) {
                count++;
            }
        }

        return count;
    }

    /**
     * Recursively checks if 'potentialUpgrade' is an upgrade of 'baseStructureId'.
     * Handles upgrade chains: coal_mine -> advanced_coal_mine -> elite_coal_mine
     */
    private boolean isUpgradeOf(Structure potentialUpgrade, String baseStructureId) {
        if (potentialUpgrade.upgradeFrom == null || potentialUpgrade.upgradeFrom.isEmpty()) {
            return false;
        }

        // Direct upgrade
        if (potentialUpgrade.upgradeFrom.equalsIgnoreCase(baseStructureId)) {
            return true;
        }

        // Check the chain: maybe potentialUpgrade.upgradeFrom is also an upgrade of baseStructureId
        Optional<Structure> parentStructure = configurationService.findStructureById(potentialUpgrade.upgradeFrom);
        if (parentStructure.isPresent()) {
            return isUpgradeOf(parentStructure.get(), baseStructureId);
        }

        return false;
    }

    public void addToTown(Town town, LoadedStructure loadedStructure) {
        structures.put(loadedStructure.uuid, loadedStructure);

        Set<LoadedStructure> set = ConcurrentHashMap.newKeySet();
        set.add(loadedStructure);

        structuresByTown.merge(loadedStructure.town, set, (a, b) -> {
            a.addAll(b);
            return a;
        });

        // Register with PowerService if structure has power-related mechanics
        registerWithPowerService(loadedStructure);
    }

    /**
     * Register structure with PowerService based on its mechanics
     */
    private void registerWithPowerService(LoadedStructure structure) {
        PowerService powerService = TownyCivs.injector.getInstance(PowerService.class);

        boolean isConnector = false;

        // Check production for power generation and storage
        if (structure.structureDef.production != null) {
            for (var production : structure.structureDef.production) {
                if (production.mechanic.id().equals("power_generation")) {
                    DoubleWrapper config = (DoubleWrapper) production.configValue;
                    powerService.registerPowerGenerator(structure, config.value);
                    isConnector = true;
                }

                if (production.mechanic.id().equals("power_storage")) {
                    DoubleWrapper config = (DoubleWrapper) production.configValue;
                    powerService.registerPowerStorage(structure, config.value);
                    isConnector = true;
                }
            }
        }

        // Check upkeep for power consumption
        if (structure.structureDef.upkeep != null) {
            for (var upkeep : structure.structureDef.upkeep) {
                if (upkeep.mechanic.id().equals("power_consumption")) {
                    DoubleWrapper config = (DoubleWrapper) upkeep.configValue;
                    powerService.registerPowerConsumer(structure, config.value);
                    isConnector = true;
                }
            }
        }

        // Check tags for explicit power connector
        if (structure.structureDef.tags != null) {
            for (String tag : structure.structureDef.tags) {
                if (tag.equals("PowerConnector") || tag.equals("Power")) {
                    powerService.registerPowerConnector(structure);
                    isConnector = true;
                }
            }
        }

        // If structure has lightning rod but no power mechanics, register as connector
        if (!isConnector && powerService.isPowerConnector(structure)) {
            powerService.registerPowerConnector(structure);
        }
    }

    public void loadAll() {
        structures.clear();
        new Storage(TownyCivs.injector.getInstance(Flatfile.class));
        Collection<LoadedStructure> loaded = Storage.allStructures();
        Collection<UUID> towns = TownyAPI.getInstance().getTowns().stream().map(Town::getUUID).collect(Collectors.toSet());
        loaded.stream()
                .filter(a -> a.structureDef != null)
                .filter(a -> towns.contains(a.town))
                .peek(a -> {
                    if (a.editMode.get()) {
                        managementService.structuresBeingEdited.add(a.uuid);
                    }
                })
                .peek(a -> subclaimService.createRegion(a).ifPresent(b -> subclaimService.registerRegion(b, a)))
                .forEach(a -> {
                    Town town = TownyAPI.getInstance().getTown(a.town);
                    addToTown(town, a);
                });
    }

    public void delete(Region region, Player player) {
        subclaimService.delete(region);
        LoadedStructure l = region.loadedStructure;
        structures.remove(l.uuid);
        Set<LoadedStructure> loadedStructures = structuresByTown.getOrDefault(l.town, Collections.emptySet());
        loadedStructures.remove(l);
        Town town = TownyAPI.getInstance().getTown(l.town);

        TownyMessaging.sendPrefixedTownMessage(town, player.getName() + " deleted structure " + l.structureDef.name);

        Storage.scheduleRemove(l);
    }

    public Optional<BoundingBox> getStructureBoundingBox(UUID structureUUID) {
        Optional<LoadedStructure> structure = findStructureByUUID(structureUUID);
        if (structure.isPresent()) {
            Region region = subclaimService.getRegion(structure.get());
            return region != null ? Optional.of(region.boundingBox) : Optional.empty();
        }
        return Optional.empty();
    }

    /**
     * Changes a structure from one type to another (e.g., coal_mine -> coal_mine_2)
     * Updates the structure in memory and saves to flatfile
     *
     * @param loadedStructure The Loaded Structure for the UUID to change
     * @param newStructureId The ID of the new structure type
     * @return true if successful, false otherwise
     */
    public boolean changeStructure(LoadedStructure loadedStructure, String newStructureId) {
        // Find existing structure
        LoadedStructure oldStructure = loadedStructure;
        if (oldStructure == null) {
            return false;
        }

        UUID structureUUID = oldStructure.uuid;

        // Find new structure definition
        Optional<Structure> newStructureDef = configurationService.findStructureById(newStructureId);
        if (newStructureDef.isEmpty()) {
            return false;
        }

        Structure newStructure = newStructureDef.get();

        // Remove old region
        Region oldRegion = subclaimService.getRegion(oldStructure);
        if (oldRegion != null) {
            subclaimService.delete(oldRegion);
        }

        // Create new LoadedStructure with same UUID and location
        LoadedStructure newLoadedStructure = new LoadedStructure(
                oldStructure.uuid,
                oldStructure.town,
                newStructureId,
                oldStructure.center,
                newStructure
        );

        // Preserve state
        newLoadedStructure.editMode.set(oldStructure.editMode.get());
        newLoadedStructure.lastTickTime = oldStructure.lastTickTime;

        // Transfer inventory if new structure supports it
        if (newStructure.inventorySize > 0) {
            // Transfer container locations
            newLoadedStructure.containerLocations.addAll(oldStructure.containerLocations);

            // Get the item service for blocker checks
            ItemService itemService = TownyCivs.injector.getInstance(ItemService.class);
            ItemStack blocker = itemService.getInventoryBlocker();

            // Close any open inventories and recreate with new structure name/title
            for (Map.Entry<Location, org.bukkit.inventory.Inventory> entry : oldStructure.inventory.entrySet()) {
                Location containerLoc = entry.getKey();
                org.bukkit.inventory.Inventory oldInv = entry.getValue();

                // Close inventory for all viewers before modifying
                new java.util.ArrayList<>(oldInv.getViewers()).forEach(viewer -> viewer.closeInventory());

                // Create new inventory with 27 slots (standard chest) and updated structure name
                org.bukkit.inventory.Inventory newInv = org.bukkit.Bukkit.getServer().createInventory(
                    null,
                    27,
                    net.kyori.adventure.text.Component.text(newStructure.name)
                );

                // Transfer items from old inventory to new inventory (skip blocker items)
                ItemStack[] oldContents = oldInv.getContents();
                for (int i = 0; i < Math.min(oldContents.length, newStructure.inventorySize); i++) {
                    ItemStack item = oldContents[i];
                    // Only transfer if not null and not a blocker item
                    if (item != null && !itemService.isInventoryBlocker(item)) {
                        newInv.setItem(i, item.clone());
                    }
                }

                // Add inventory blocker items for slots beyond the usable inventory size
                for (int i = newStructure.inventorySize; i < 27; i++) {
                    newInv.setItem(i, blocker);
                }

                // Store new inventory
                newLoadedStructure.inventory.put(containerLoc, newInv);
            }
        }

        // Update in memory
        structures.put(structureUUID, newLoadedStructure);

        Set<LoadedStructure> townStructures = structuresByTown.get(oldStructure.town);
        if (townStructures != null) {
            townStructures.remove(oldStructure);
            townStructures.add(newLoadedStructure);
        }

        // Create and register new region
        Optional<Region> newRegion = subclaimService.createRegion(newLoadedStructure);
        newRegion.ifPresent(region -> subclaimService.registerRegion(region, newLoadedStructure));

        // Save to flatfile
        Storage.scheduleSave(newLoadedStructure);

        return true;
    }


    public Optional<BoundingBox> getStructureBoundingBox(LoadedStructure loadedStructure) {
        Region region = subclaimService.getRegion(loadedStructure);
        return region != null ? Optional.of(region.boundingBox) : Optional.empty();
    }

    public Optional<LoadedStructure> findStructureByUUID(UUID uuid) {
        return Optional.ofNullable(structures.get(uuid));
    }
}
