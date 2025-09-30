package cz.neumimto.towny.townycivs;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.TownyMessaging;
import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.object.Translatable;
import cz.neumimto.towny.townycivs.config.ConfigurationService;
import cz.neumimto.towny.townycivs.config.Structure;
import cz.neumimto.towny.townycivs.db.Storage;
import cz.neumimto.towny.townycivs.model.EditSession;
import cz.neumimto.towny.townycivs.model.LoadedStructure;
import cz.neumimto.towny.townycivs.model.Region;
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
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;


@Singleton
public class ManagementService {

    Set<UUID> structuresBeingEdited = new HashSet<>();
    private Map<UUID, EditSession> editSessions = new HashMap<>();
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

    public EditSession startNewEditSession(Player player, Structure structure, Location location) {
        var es = new EditSession(structure, location);
        es.structure = structure;
        editSessions.put(player.getUniqueId(), es);
        MiniMessage miniMessage = MiniMessage.miniMessage();
        player.sendMessage(miniMessage.deserialize("<gold>[TownyCivs]</gold> <green>Right click again to change blueprint location, Left click to confirm selection and place blueprint</green>"));
        return es;
    }

    public boolean moveTo(Player player, Location location) {
        if (editSessions.containsKey(player.getUniqueId())) {
            EditSession editSession = editSessions.get(player.getUniqueId());
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
                player.sendMessage(miniMessage.deserialize("<gold>[Townycivs]</gold> <red>" + editSession.structure.name + " region overlaps with " + overlapingStruct.name + "</red>"));
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
                player.sendMessage(miniMessage.deserialize("<gold>[Townycivs]</gold> <red>" + editSession.structure.name + " is outside town claim" + "</red>"));
                isOk = false;
            }

            sendBlockChange(player, editSession.currentStructureBorder, isOk ? Material.GREEN_STAINED_GLASS : Material.RED_STAINED_GLASS);


            return isOk;
        }
        return false;
    }

    public void endSessionWithoutPlacement(Player player) {
        EditSession remove = editSessions.remove(player.getUniqueId());
        if (remove != null) {
            if (remove.overlappintStructureBorder != null) {
                removeAreaBorder(player, remove.overlappintStructureBorder);
            }
            if (remove.currentStructureBorder != null) {
                removeAreaBorder(player, remove.currentStructureBorder);
            }
        }
    }

    public void endSession(Player player, Location location) {
        if (editSessions.containsKey(player.getUniqueId())) {
            EditSession editSession = editSessions.get(player.getUniqueId());
            editSession.center = location;
            if (moveTo(player, editSession.center)) {
                // Skip validation here since it was already done in canPlaceStructure()
                // before the blueprint item was consumed
                placeBlueprintWithoutValidation(player, editSession.center, editSession.structure);
                editSessions.remove(player.getUniqueId());
            }
        }
    }

    public boolean hasEditSession(Player player) {
        return editSessions.containsKey(player.getUniqueId()) && editSessions.get(player.getUniqueId()).structure != null;
    }

    public EditSession getSession(Player player) {
        return editSessions.get(player.getUniqueId());
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
        // Calculate bounds without the extra -1 that was making the visual box too large
        double maxX = location.getX() + area.x;
        double minX = location.getX() - area.x; // Removed the -1 here

        double maxY = (location.getY() - 1) + area.y;
        double minY = (location.getY() - 1) - area.y; // Removed the -1 here

        double maxZ = location.getZ() + area.z;
        double minZ = location.getZ() - area.z; // Removed the -1 here;

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

        // Create a temporary structure and region to check for required blocks
        LoadedStructure tempStructure = new LoadedStructure(UUID.randomUUID(), town.getUUID(), structure.id, center, structure);
        Region tempRegion = subclaimService.createRegion(tempStructure).get();

        // Check if the required blocks are present
        Map<String, Integer> remainingBlocks = subclaimService.remainingBlocks(tempRegion);
        if (!subclaimService.noRemainingBlocks(remainingBlocks, tempStructure)) {
            MiniMessage miniMessage = MiniMessage.miniMessage();
            Component c = miniMessage.deserialize("<gold>[TownyCivs]</gold> <red>Cannot place structure - missing required blocks!</red>");
            player.sendMessage(c);

            // Show the player what blocks are missing
            showMissingBlocks(player, remainingBlocks);

            // Clean up temporary structure visual elements
            if (editSessions.containsKey(player.getUniqueId())) {
                EditSession session = editSessions.get(player.getUniqueId());
                if (session.currentStructureBorder != null) {
                    removeAreaBorder(player, session.currentStructureBorder);
                }
                if (session.overlappintStructureBorder != null) {
                    removeAreaBorder(player, session.overlappintStructureBorder);
                }
                editSessions.remove(player.getUniqueId());
            }
            return;
        }

        // If we reach here, all blocks are present, so create the structure
        LoadedStructure loadedStructure = new LoadedStructure(UUID.randomUUID(), town.getUUID(), structure.id, center, structure);

        // No need for edit mode since blocks are already present
        loadedStructure.editMode.set(false);

        Region lreg = subclaimService.createRegion(loadedStructure).get();

        refreshContainerLocations(loadedStructure, lreg);
        subclaimService.registerRegion(lreg, loadedStructure);

        structureService.addToTown(town, loadedStructure);

        TownyMessaging.sendPrefixedTownMessage(town, player.getName() + " placed " + structure.name + " at " + location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ());
        Storage.scheduleSave(loadedStructure);
    }

    /**
     * Shows a list of missing blocks to the player
     *
     * @param player          The player to show the message to
     * @param remainingBlocks Map of block types and counts needed
     */
    private void showMissingBlocks(Player player, Map<String, Integer> remainingBlocks) {
        MiniMessage miniMessage = MiniMessage.miniMessage();
        player.sendMessage(miniMessage.deserialize("<gold>[TownyCivs]</gold> <yellow>The following blocks are required:</yellow>"));

        for (Map.Entry<String, Integer> entry : remainingBlocks.entrySet()) {
            if (!entry.getKey().startsWith("!") && entry.getValue() > 0) {
                // Regular block requirement
                player.sendMessage(miniMessage.deserialize("<yellow>• " + formatBlockName(entry.getKey()) + ": " + entry.getValue() + "</yellow>"));
            } else if (entry.getKey().startsWith("!") && entry.getValue() != 0) {
                // Negative requirement (blocks that must not be present)
                player.sendMessage(miniMessage.deserialize("<yellow>• " + formatBlockName(entry.getKey().substring(1)) + ": None allowed (remove " + Math.abs(entry.getValue()) + ")</yellow>"));
            }
        }
    }

    /**
     * Formats a block name for display to players
     *
     * @param blockKey The block key (minecraft:block or tag)
     * @return Formatted block name
     */
    private String formatBlockName(String blockKey) {
        if (blockKey.contains(":")) {
            String[] parts = blockKey.split(":");
            if (parts.length > 1) {
                return capitalizeWords(parts[1].replace("_", " "));
            }
        }
        return capitalizeWords(blockKey.replace("_", " "));
    }

    /**
     * Capitalizes the first letter of each word
     *
     * @param str The string to capitalize
     * @return Capitalized string
     */
    private String capitalizeWords(String str) {
        String[] words = str.split("\\s");
        StringBuilder result = new StringBuilder();

        for (String word : words) {
            if (word.length() > 0) {
                result.append(Character.toUpperCase(word.charAt(0)))
                        .append(word.substring(1).toLowerCase())
                        .append(" ");
            }
        }

        return result.toString().trim();
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

    private void sendBlockChange(Player player, Set<Location> locations, Material mat) {
        BlockData data = mat.createBlockData();
        Map<Location, BlockData> map = new HashMap<>();
        for (Location location : locations) {
            map.put(location, data);
        }
        player.sendMultiBlockChange(map, false);
    }

    /**
     * Refreshes container locations for a structure
     * Finds and registers container blocks (like chests) within the structure's region
     *
     * @param loadedStructure The structure to refresh containers for
     * @param region          The region containing the structure
     */
    private void refreshContainerLocations(LoadedStructure loadedStructure, Region region) {
        // Get all potential container blocks
        Set<Material> containerTypes = new HashSet<>();
        containerTypes.add(Material.CHEST);
        containerTypes.add(Material.TRAPPED_CHEST);
        containerTypes.add(Material.BARREL);
        containerTypes.add(Material.HOPPER);
        containerTypes.add(Material.DISPENSER);
        containerTypes.add(Material.DROPPER);
        containerTypes.add(Material.FURNACE);
        containerTypes.add(Material.BLAST_FURNACE);
        containerTypes.add(Material.SMOKER);
        containerTypes.add(Material.BREWING_STAND);

        // Find all container blocks within the region
        Collection<Block> containerBlocks = subclaimService.blocksWithinRegion(containerTypes, region);

        // Register containers with the structure
        loadedStructure.containerLocations.clear();
        for (Block block : containerBlocks) {
            loadedStructure.containerLocations.add(block.getLocation());
        }

        // If structure has an inventory system, initialize it
        if (loadedStructure.structureDef.inventorySize > 0) {
            structureInventoryService.registerStructure(loadedStructure);
        }
    }

    /**
     * Checks if a structure can be placed at the given location
     * This method should be called before consuming the blueprint item
     *
     * @param player   The player placing the structure
     * @param location The location to check
     * @param structure The structure definition
     * @return true if the structure can be placed, false otherwise
     */
    public boolean canPlaceStructure(Player player, Location location, Structure structure) {
        Town town = TownyAPI.getInstance().getResident(player).getTownOrNull();

        if (town == null) {
            MiniMessage miniMessage = MiniMessage.miniMessage();
            player.sendMessage(miniMessage.deserialize("<gold>[TownyCivs]</gold> <red>You must be in a town to place structures!</red>"));
            return false;
        }

        Location center = new Location(player.getWorld(), location.getX(), location.getY(), location.getZ());

        // Create a temporary structure and region to check for required blocks
        LoadedStructure tempStructure = new LoadedStructure(UUID.randomUUID(), town.getUUID(), structure.id, center, structure);
        Region tempRegion = subclaimService.createRegion(tempStructure).get();

        // Check for overlapping structures
        Optional<Region> overlaps = subclaimService.overlaps(tempRegion);
        if (overlaps.isPresent()) {
            Region region1 = overlaps.get();
            Structure overlapingStruct = configurationService.findStructureById(region1.structureId).get();
            MiniMessage miniMessage = MiniMessage.miniMessage();
            player.sendMessage(miniMessage.deserialize("<gold>[TownyCivs]</gold> <red>" + structure.name + " region overlaps with " + overlapingStruct.name + "</red>"));
            return false;
        }

        // Check if structure is within town boundaries
        if (subclaimService.isOutsideTownClaim(tempRegion, town)) {
            MiniMessage miniMessage = MiniMessage.miniMessage();
            player.sendMessage(miniMessage.deserialize("<gold>[Townycivs]</gold> <red>" + structure.name + " is outside town claim</red>"));
            return false;
        }

        // Check if the required blocks are present
        Map<String, Integer> remainingBlocks = subclaimService.remainingBlocks(tempRegion);
        if (!subclaimService.noRemainingBlocks(remainingBlocks, tempStructure)) {
            MiniMessage miniMessage = MiniMessage.miniMessage();
            Component c = miniMessage.deserialize("<gold>[TownyCivs]</gold> <red>Cannot place structure - missing required blocks!</red>");
            player.sendMessage(c);

            // Show the player what blocks are missing
            showMissingBlocks(player, remainingBlocks);
            return false;
        }

        return true;
    }

    /**
     * Places a blueprint without doing validation (validation should have been done already)
     * This method is used after canPlaceStructure() has already validated everything
     *
     * @param player    The player placing the structure
     * @param location  The location to place at
     * @param structure The structure to place
     */
    private void placeBlueprintWithoutValidation(Player player, Location location, Structure structure) {
        Town town = TownyAPI.getInstance().getResident(player).getTownOrNull();
        Location center = new Location(player.getWorld(), location.getX(), location.getY(), location.getZ());

        // Create the structure without validation since it was already done
        LoadedStructure loadedStructure = new LoadedStructure(UUID.randomUUID(), town.getUUID(), structure.id, center, structure);
        loadedStructure.editMode.set(false);

        Region lreg = subclaimService.createRegion(loadedStructure).get();

        refreshContainerLocations(loadedStructure, lreg);
        subclaimService.registerRegion(lreg, loadedStructure);

        structureService.addToTown(town, loadedStructure);

        TownyMessaging.sendPrefixedTownMessage(town, player.getName() + " placed " + structure.name + " at " + location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ());
        Storage.scheduleSave(loadedStructure);
    }
}
