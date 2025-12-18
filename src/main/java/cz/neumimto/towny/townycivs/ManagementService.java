package cz.neumimto.towny.townycivs;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.TownyMessaging;
import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.object.Translatable;
import cz.neumimto.towny.townycivs.config.ConfigurationService;
import cz.neumimto.towny.townycivs.config.Structure;
import cz.neumimto.towny.townycivs.db.Storage;
import cz.neumimto.towny.townycivs.model.*;
import cz.neumimto.towny.townycivs.schedulers.FoliaScheduler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;


@Singleton
public class ManagementService {

    Set<UUID> structuresBeingEdited = new HashSet<>();
    private Map<PlayerBlueprintKey, EditSession> editSessions = new HashMap<>();
    @Inject
    private StructureService structureService;

    @Inject
    private SubclaimService subclaimService;

    @Inject
    private ConfigurationService configurationService;

    @Inject
    private FoliaScheduler structureScheduler;

    @Inject
    private StructureInventoryService structureInventoryService;

    @Inject
    private TownService townService;

    @Inject
    private cz.neumimto.towny.townycivs.tutorial.TutorialManager tutorialManager;

    public EditSession startNewEditSession(Player player, Structure structure, Location location, BlueprintItem item) {
        Iterator<Map.Entry<PlayerBlueprintKey, EditSession>> it = editSessions.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<PlayerBlueprintKey, EditSession> entry = it.next();
            if (entry.getKey().getPlayerId().equals(player.getUniqueId())) {
                EditSession old = entry.getValue();
                if (old.currentStructureBorder != null) {
                    removeAreaBorder(player, old.currentStructureBorder);
                }
                if (old.overlappintStructureBorder != null) {
                    removeAreaBorder(player, old.overlappintStructureBorder);
                }
                it.remove(); // actually remove old session
            }
        }


        var es = new EditSession(structure, location, item);
        es.structure = structure;
        editSessions.put(new PlayerBlueprintKey(player.getUniqueId(), item), es);
        MiniMessage miniMessage = MiniMessage.miniMessage();
        player.sendMessage(miniMessage.deserialize("<gold>[TownyCivs]</gold> <green>Right click again to change blueprint location, Left click to confirm selection and place blueprint</green>"));
        return es;
    }

    public boolean moveTo(Player player, Location location, BlueprintItem item) {
        if (editSessions.containsKey(new PlayerBlueprintKey(player.getUniqueId(), item))) {
            EditSession editSession = editSessions.get(new PlayerBlueprintKey(player.getUniqueId(), item));
            editSession.center = location.clone().add(0, editSession.structure.area.y + 1, 0);
            Region region = subclaimService.createRegion(editSession.structure, editSession.center);


            Set<Location> locations = prepareVisualBox(player, editSession.center, editSession.structure.area);
            if (editSession.currentStructureBorder != null) {
                removeAreaBorder(player, editSession.currentStructureBorder);
            }
            editSession.currentStructureBorder = locations;


            boolean isOk = true;

            Optional<Region> overlaps = subclaimService.overlaps(region);
            if (overlaps.isPresent()) {
                Region region1 = overlaps.get();
                Structure overlapingStruct = configurationService.findStructureById(region1.structureId).get();
                MiniMessage miniMessage = MiniMessage.miniMessage();
                player.sendMessage(miniMessage.deserialize("<gold>[TownyCivs]</gold> <red>" + editSession.structure.name + " region overlaps with " + overlapingStruct.name + "</red>"));
                isOk = false;

                if (editSession.overlappintStructureBorder != null) {
                    removeAreaBorder(player, editSession.overlappintStructureBorder);
                    editSession.overlappintStructureBorder = null;
                }
                editSession.overlappintStructureBorder = prepareVisualBox(player, overlaps.get().boundingBox.getCenter().toLocation(player.getWorld()), overlapingStruct.area);
                sendBlockChange(player, editSession.overlappintStructureBorder, Material.YELLOW_STAINED_GLASS);

            } else if (editSession.overlappintStructureBorder != null) {
                removeAreaBorder(player, editSession.overlappintStructureBorder);
                editSession.overlappintStructureBorder = null;
            }

            Town town = TownyAPI.getInstance().getResident(player).getTownOrNull();
            if (subclaimService.isOutsideTownClaim(region, town)) {
                MiniMessage miniMessage = MiniMessage.miniMessage();
                player.sendMessage(miniMessage.deserialize("<gold>[TownyCivs]</gold> <red>" + editSession.structure.name + " is outside town claim" + "</red>"));
                isOk = false;
            }

            sendBlockChange(player, editSession.currentStructureBorder, isOk ? Material.GREEN_STAINED_GLASS : Material.RED_STAINED_GLASS);


            return isOk;
        }
        return false;
    }

    public void endSessionWithoutPlacement(Player player, BlueprintItem item) {
        EditSession remove = editSessions.remove(new PlayerBlueprintKey(player.getUniqueId(), item));
        if (remove != null) {
            if (remove.overlappintStructureBorder != null) {
                removeAreaBorder(player, remove.overlappintStructureBorder);
            }
            if (remove.currentStructureBorder != null) {
                removeAreaBorder(player, remove.currentStructureBorder);
            }
        }
    }

    public void endSession(Player player, Location location, BlueprintItem item) {
        if (editSessions.containsKey(new PlayerBlueprintKey(player.getUniqueId(), item))) {
            EditSession editSession = editSessions.get(new PlayerBlueprintKey(player.getUniqueId(), item));
            editSession.center = location;

            // Check max build count before placing
            Town town = TownyAPI.getInstance().getResident(player).getTownOrNull();
            if (town != null && editSession.structure.maxCount != null && editSession.structure.maxCount > 0) {
                int currentCount = structureService.countStructuresInUpgradeChain(town, editSession.structure);
                if (currentCount >= editSession.structure.maxCount) {
                    MiniMessage mm = MiniMessage.miniMessage();
                    player.sendMessage(mm.deserialize("<gold>[TownyCivs]</gold> <red>Cannot place " + editSession.structure.name + ". Maximum build count reached (" + currentCount + "/" + editSession.structure.maxCount + "). This includes upgraded versions.</red>"));
                    return;
                }
            }

            if (editSession.structure.placeRequirements != null && !editSession.structure.placeRequirements.isEmpty()) {
                cz.neumimto.towny.townycivs.mechanics.TownContext townContext = new cz.neumimto.towny.townycivs.mechanics.TownContext();
                townContext.town = town;
                townContext.player = player;
                townContext.resident = TownyAPI.getInstance().getResident(player);
                townContext.structure = editSession.structure;
                townContext.structureCenterLocation = location;

                boolean allMet = true;
                for (Structure.LoadedPair<cz.neumimto.towny.townycivs.mechanics.Mechanic<?>, ?> requirement : editSession.structure.placeRequirements) {
                    @SuppressWarnings("unchecked")
                    cz.neumimto.towny.townycivs.mechanics.Mechanic<Object> mechanic = (cz.neumimto.towny.townycivs.mechanics.Mechanic<Object>) requirement.mechanic;
                    boolean checkResult = mechanic.check(townContext, requirement.configValue);
                    if (!checkResult) {
                        mechanic.nokmessage(townContext, requirement.configValue);
                        allMet = false;
                    }
                }

                if (!allMet) {
                    return;
                }
            }

            if (moveTo(player, editSession.center, item)) {
                placeBlueprint(player, editSession.center, editSession.structure);
                if (editSession.currentStructureBorder != null) {
                    removeAreaBorder(player, editSession.currentStructureBorder);
                }
                editSessions.remove(new PlayerBlueprintKey(player.getUniqueId(), item));
                EquipmentSlot hand = player.getActiveItemHand();
                ItemStack itemInUse = player.getInventory().getItem(hand);
                if (itemInUse.getAmount() > 1) {
                    itemInUse.setAmount(itemInUse.getAmount() - 1);
                } else {
                    itemInUse = null;
                }
                player.getInventory().setItem(hand, itemInUse);
            }
        }
    }

    public boolean hasEditSession(Player player, BlueprintItem item) {
        return editSessions.containsKey(new PlayerBlueprintKey(player.getUniqueId(), item)) && editSessions.get(new PlayerBlueprintKey(player.getUniqueId(), item)).structure != null;
    }

    public EditSession getSession(Player player, BlueprintItem item) {
        return editSessions.get(new PlayerBlueprintKey(player.getUniqueId(), item));
    }

    public void removeAreaBorder(Player player, Set<Location> locs) {
        Map<Location, BlockData> blockDataMap = new HashMap<>();
        for (Location location : locs) {
            BlockData blockData = location.getWorld().getBlockData(location);
            blockDataMap.put(location, blockData);
        }
        player.sendMultiBlockChange(blockDataMap, true);
    }

    public Set<Location> prepareVisualBox(Player player, Location location, Structure.Area area) {
        double maxX = location.getX() + area.x;
        double minX = location.getX() - area.x - 1;

        double maxY = location.getY() + area.y;
        double minY = location.getY() - area.y - 1;

        double maxZ = location.getZ() + area.z;
        double minZ = location.getZ() - area.z - 1;

        World world = location.getWorld();

        Set<Location> set = new HashSet<>();
        for (double x = minX; x <= maxX; x++) {
            blockChange(world, x, minY, minZ, set);
            blockChange(world, x, maxY, maxZ, set);
            blockChange(world, x, minY, maxZ, set);
            blockChange(world, x, maxY, minZ, set);
        }

        for (double z = minZ; z <= maxZ; z++) {
            blockChange(world, minX, minY, z, set);
            blockChange(world, maxX, maxY, z, set);
            blockChange(world, minX, maxY, z, set);
            blockChange(world, maxX, minY, z, set);
        }

        for (double y = minY; y <= maxY; y++) {
            blockChange(world, minX, y, minZ, set);
            blockChange(world, maxX, y, maxZ, set);
            blockChange(world, minX, y, maxZ, set);
            blockChange(world, maxX, y, minZ, set);
        }

        return set;
    }

    private void blockChange(World world, double x, double y, double z, Set<Location> set) {
        if (world.getMinHeight() > y && world.getMaxHeight() < y) {
            return;
        }
        set.add(new Location(world, x, y, z));
    }

    public void placeBlueprint(Player player, Location location, Structure structure) {
        Town town = TownyAPI.getInstance().getResident(player).getTownOrNull();

        Location center = new Location(player.getWorld(), location.getX(), location.getY(), location.getZ());

        LoadedStructure loadedStructure = new LoadedStructure(UUID.randomUUID(), town.getUUID(), structure.id, center, structure);
        loadedStructure.editMode.set(true);

        Region lreg = subclaimService.createRegion(loadedStructure).get();

        refreshContainerLocations(loadedStructure, lreg);
        subclaimService.registerRegion(lreg, loadedStructure);

        structureService.addToTown(town, loadedStructure);

        TownyMessaging.sendPrefixedTownMessage(town, player.getName() + " placed " + structure.name + " at " + location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ());
        Storage.scheduleSave(loadedStructure);

        // Track tutorial progress - structure placed
        com.palmergames.bukkit.towny.object.Resident resident = TownyAPI.getInstance().getResident(player);
        if (resident != null && resident.isMayor()) {
            tutorialManager.onStructurePlaced(town, player, structure.id);
        }
    }

    private void refreshContainerLocations(LoadedStructure loadedStructure, Region lreg) {
        Collection<Material> materials = Materials.getMaterials("tc:container");

        Collection<Block> map = subclaimService.blocksWithinRegion(materials, lreg);
        Map<Location, Inventory> newLocs = new HashMap<>();
        for (Block block : map) {
            if (materials.contains(block.getType())) {
                newLocs.put(block.getLocation(), loadedStructure.inventory.get(block.getLocation()));
            }
        }

        loadedStructure.inventory.clear();

        Iterator<Map.Entry<Location, Inventory>> iterator = newLocs.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Location, Inventory> next = iterator.next();
            loadedStructure.inventory.put(next.getKey(),
                    next.getValue() == null ? structureInventoryService.loadStructureInventory(loadedStructure, next.getKey(), new ItemStack[0]) : next.getValue());
        }

    }

    public void toggleEditMode(LoadedStructure loadedStructure, Player player) {
        Town town = TownyAPI.getInstance().getResident(player).getTownOrNull();

        if (!structuresBeingEdited.contains(loadedStructure.uuid)) {
            structuresBeingEdited.add(loadedStructure.uuid);
            TownyMessaging.sendPrefixedTownMessage(town, player.getName() + " put " + loadedStructure.structureDef.name + " into edit mode ");

            loadedStructure.editMode.set(true);

        } else {

            Region region = subclaimService.getRegion(loadedStructure);
            Map<String, Integer> remainingBlocks = subclaimService.remainingBlocks(region);
            if (!subclaimService.noRemainingBlocks(remainingBlocks, loadedStructure)) {
                MiniMessage miniMessage = MiniMessage.miniMessage();
                Component c = miniMessage.deserialize(Translatable.of("toco_error_missing_blocks").forLocale(player),
                        Placeholder.component("name", Component.text(loadedStructure.structureDef.name)));
                player.sendMessage(c);
                return;
            }

            if (!townService.hasPointsForStructure(town, loadedStructure.structureDef)) {
                MiniMessage miniMessage = MiniMessage.miniMessage();
                Component c = miniMessage.deserialize(Translatable.of("toco_error_missing_townpoints").forLocale(player),
                        Placeholder.component("name", Component.text(loadedStructure.structureDef.name)));
                player.sendMessage(c);
                return;
            }

            structuresBeingEdited.remove(loadedStructure.uuid);
            refreshContainerLocations(loadedStructure, region);
            loadedStructure.editMode.set(false);
        }
    }

    public boolean isBeingEdited(LoadedStructure loadedStructure) {
        return structuresBeingEdited.contains(loadedStructure.uuid);
    }

    public void sendBlockChange(Player player, Set<Location> locations, Material mat) {
        BlockData data = mat.createBlockData();
        Map<Location, BlockData> map = new HashMap<>();
        for (Location location : locations) {
            map.put(location, data);
        }
        player.sendMultiBlockChange(map, false);
    }
}