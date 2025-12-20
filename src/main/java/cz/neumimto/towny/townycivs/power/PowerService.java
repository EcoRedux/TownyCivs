package cz.neumimto.towny.townycivs.power;

import com.palmergames.bukkit.towny.object.Town;
import cz.neumimto.towny.townycivs.StructureService;
import cz.neumimto.towny.townycivs.SubclaimService;
import cz.neumimto.towny.townycivs.TownyCivs;
import cz.neumimto.towny.townycivs.model.LoadedStructure;
import cz.neumimto.towny.townycivs.TownyCivs;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Town;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages power grids, power lines, and power distribution for towns.
 */
@Singleton
public class PowerService {

    private final TownyCivs plugin = TownyCivs.INSTANCE;

    @Inject
    private StructureService structureService;

    @Inject
    private SubclaimService subclaimService;

    // Power grids per town
    private final Map<UUID, PowerGrid> powerGrids = new ConcurrentHashMap<>();

    // All power lines
    private final Map<UUID, PowerLine> powerLines = new ConcurrentHashMap<>();

    // Player's current power line connection state (first point selected)
    private final Map<UUID, PowerLineConnectionState> playerConnectionStates = new ConcurrentHashMap<>();

    // Maximum distance for power line connections
    private static final double MAX_POWER_LINE_DISTANCE = 50.0;

    // Track power generation and consumption per town per tick
    private final Map<UUID, Double> currentTickGeneration = new ConcurrentHashMap<>();
    private final Map<UUID, Double> currentTickConsumption = new ConcurrentHashMap<>();

    /**
     * Add power generation from a structure (called during production tick)
     * Respects town power capacity limits
     */
    public void addPowerGeneration(LoadedStructure structure, double amount) {
        PowerGrid grid = getPowerGrid(structure.town);
        double maxCapacity = grid.getMaxPowerCapacity();

        // Get current generation this tick
        double currentGeneration = currentTickGeneration.getOrDefault(structure.town, 0.0);

        // Cap the additional power to not exceed town capacity
        double cappedAmount = Math.min(amount, Math.max(0, maxCapacity - currentGeneration));

        if (cappedAmount > 0) {
            currentTickGeneration.merge(structure.town, cappedAmount, Double::sum);
        }

        // Log if power is being capped
        if (cappedAmount < amount) {
            plugin.getLogger().info("[PowerService] Power generation capped for town " + structure.town + 
                ". Attempted: " + amount + ", Applied: " + cappedAmount + ", Capacity: " + maxCapacity);
        }
    }

    /**
     * Check if town has sufficient power for consumption
     * Accounts for capacity limits and current usage
     */
    public boolean hasSufficientPower(UUID townUuid, double requiredAmount) {
        double availablePower = getAvailablePower(townUuid);

        // Debug logging
        if (availablePower < requiredAmount) {
            PowerGrid grid = getPowerGrid(townUuid);
            plugin.getLogger().info("[PowerService] Insufficient power for town " + townUuid + 
                ". Required: " + requiredAmount + ", Available: " + availablePower + 
                ", Capacity: " + grid.getMaxPowerCapacity() + 
                ", Current Generation: " + currentTickGeneration.getOrDefault(townUuid, 0.0) +
                ", Stored: " + grid.getCurrentStoredEnergy());
        }

        return availablePower >= requiredAmount;
    }

    /**
     * Get available power for a town (respects capacity limits)
     */
    public double getAvailablePower(UUID townUuid) {
        PowerGrid grid = powerGrids.get(townUuid);
        if (grid == null) return 0;

        // Use effective power generation (capped by town level) instead of raw generation
        double effectiveGenerated = Math.min(
            currentTickGeneration.getOrDefault(townUuid, 0.0),
            grid.getMaxPowerCapacity()
        );

        double consumed = currentTickConsumption.getOrDefault(townUuid, 0.0);

        // Available power is effective generation plus stored energy, minus what's already consumed this tick
        return effectiveGenerated + grid.getCurrentStoredEnergy() - consumed;
    }

    /**
     * Consume power from town grid
     */
    public void consumePower(UUID townUuid, double amount) {
        currentTickConsumption.merge(townUuid, amount, Double::sum);
    }

    /**
     * Register a structure as a power storage (battery)
     */
    public void registerPowerStorage(LoadedStructure structure, double capacity) {
        PowerGrid grid = getPowerGrid(structure.town);
        grid.addStorage(structure.uuid);
        double currentCapacity = grid.getTotalStorageCapacity();
        grid.setTotalStorageCapacity(currentCapacity + capacity);
    }

    /**
     * Get stored energy for a town
     */
    public double getStoredEnergy(UUID townUuid) {
        PowerGrid grid = powerGrids.get(townUuid);
        return grid != null ? grid.getCurrentStoredEnergy() : 0;
    }

    /**
     * Set stored energy for a town (used for consuming power from storage)
     */
    public void setStoredEnergy(UUID townUuid, double amount) {
        PowerGrid grid = powerGrids.get(townUuid);
        if (grid != null) {
            grid.setCurrentStoredEnergy(Math.max(0, amount)); // Don't go below 0
        }
    }

    /**
     * Get total storage capacity for a town
     */
    public double getTotalStorageCapacity(UUID townUuid) {
        PowerGrid grid = powerGrids.get(townUuid);
        return grid != null ? grid.getTotalStorageCapacity() : 0;
    }

    /**
     * Reset per-tick power tracking and handle battery charging/discharging
     * Called at start of each production cycle - uses capacity-capped power
     */
    public void resetTickPower() {
        for (UUID townUuid : new HashSet<>(currentTickGeneration.keySet())) {
            PowerGrid grid = powerGrids.get(townUuid);
            if (grid == null) continue;

            // Use capacity-capped generation instead of raw generation
            double rawGenerated = currentTickGeneration.getOrDefault(townUuid, 0.0);
            double effectiveGenerated = Math.min(rawGenerated, grid.getMaxPowerCapacity());
            double consumed = currentTickConsumption.getOrDefault(townUuid, 0.0);
            double balance = effectiveGenerated - consumed;

            if (balance > 0) {
                // Excess power - charge batteries
                double newStored = grid.getCurrentStoredEnergy() + balance;
                grid.setCurrentStoredEnergy(Math.min(newStored, grid.getTotalStorageCapacity()));
            } else if (balance < 0) {
                // Power deficit - discharge batteries
                double deficit = Math.abs(balance);
                double available = grid.getCurrentStoredEnergy();
                double toDischarge = Math.min(deficit, available);
                grid.setCurrentStoredEnergy(available - toDischarge);
            }

            // Log power wasted due to capacity limits
            if (rawGenerated > effectiveGenerated) {
                double wasted = rawGenerated - effectiveGenerated;
                plugin.getLogger().info("[PowerService] Town " + townUuid + " wasted " + wasted + 
                    " power due to capacity limits (" + effectiveGenerated + "/" + grid.getMaxPowerCapacity() + ")");
            }
        }

        currentTickGeneration.clear();
        currentTickConsumption.clear();
    }

    /**
     * Get or create a power grid for a town
     */
    public PowerGrid getPowerGrid(Town town) {
        return powerGrids.computeIfAbsent(town.getUUID(), PowerGrid::new);
    }

    public PowerGrid getPowerGrid(UUID townUuid) {
        return powerGrids.computeIfAbsent(townUuid, PowerGrid::new);
    }

    /**
     * Get formatted power capacity information for a town
     */
    public String getPowerCapacityInfo(UUID townUuid) {
        PowerStatus status = getPowerStatus(townUuid);

        String statusIcon = status.isOverCapacity ? "§c⚠" : "§a✓";
        String efficiencyColor = status.effectiveGeneration >= status.generationCapacity * 0.9 ? "§c" : 
                                status.effectiveGeneration >= status.generationCapacity * 0.7 ? "§e" : "§a";

        StringBuilder info = new StringBuilder();
        info.append(String.format("%s Power Generation: §e%.1f§7/§6%.1f", 
            statusIcon, status.effectiveGeneration, status.generationCapacity));

        if (status.wastedPower > 0) {
            info.append(String.format(" §c(%.1f wasted)", status.wastedPower));
        }

        info.append(String.format("\n§7Storage: §b%.1f§7/§6%.1f", 
            status.storedEnergy, status.storageCapacity));

        info.append(String.format("\n§7Current Usage: %s%.1f§7, Available: §a%.1f", 
            status.consumption > status.effectiveGeneration ? "§c" : "§e", 
            status.consumption, status.availablePower));

        if (status.isOverCapacity) {
            info.append("\n§c⚠ OVER CAPACITY - Upgrade town level for more power!");
        }

        return info.toString();
    }

    /**
     * Check if a town needs to level up for more power capacity
     */
    public boolean needsUpgradeForPower(UUID townUuid) {
        PowerGrid grid = getPowerGrid(townUuid);
        return grid.isNearCapacityLimit();
    }

    /**
     * Register a structure as a power connector (power tower/pole)
     */
    public void registerPowerConnector(LoadedStructure structure) {
        PowerGrid grid = getPowerGrid(structure.town);
        grid.addConnector(structure.uuid);
    }

    /**
     * Register a structure as a power generator
     */
    public void registerPowerGenerator(LoadedStructure structure, double generationRate) {
        PowerGrid grid = getPowerGrid(structure.town);

        // Check if adding this generator would exceed capacity
        if (!grid.canAddPowerGeneration(generationRate)) {
            // Log the capacity issue but still register the structure
            // The grid will cap the effective generation automatically
            try {
                Town town = TownyAPI.getInstance().getTown(structure.town);
                String townName = town != null ? town.getName() : "Unknown";
                plugin.getLogger().warning("Town " + townName + " power generation (" + 
                    (grid.getTotalGeneration() + generationRate) + ") exceeds capacity limit (" + 
                    grid.getMaxPowerCapacity() + "). Generation will be capped.");
            } catch (Exception e) {
                plugin.getLogger().warning("Power generation capacity exceeded for town " + structure.town);
            }
        }

        grid.addGenerator(structure.uuid);
        grid.setTotalGeneration(grid.getTotalGeneration() + generationRate);
    }

    /**
     * Register a structure as a power consumer
     */
    public void registerPowerConsumer(LoadedStructure structure, double consumptionRate) {
        PowerGrid grid = getPowerGrid(structure.town);
        grid.addConsumer(structure.uuid);
        grid.setTotalConsumption(grid.getTotalConsumption() + consumptionRate);
    }

    /**
     * Unregister a structure from the power system
     */
    public void unregisterStructure(LoadedStructure structure) {
        PowerGrid grid = powerGrids.get(structure.town);
        if (grid != null) {
            grid.removeStructure(structure.uuid);

            // Remove all power lines connected to this structure
            removeAllPowerLinesForStructure(structure.uuid);
        }
    }

    /**
     * Start a power line connection from a structure
     * Called when player right-clicks a power tower with the power tool
     */
    public void startPowerLineConnection(Player player, LoadedStructure structure) {
        MiniMessage mm = MiniMessage.miniMessage();

        // Find the lightning rod block in the structure for the connection point
        Location connectionPoint = findLightningRodLocation(structure);
        if (connectionPoint == null) {
            // Fallback to center if no lightning rod found
            connectionPoint = structure.center.clone();
        }

        PowerLineConnectionState state = new PowerLineConnectionState(
                structure.uuid,
                structure.town,
                connectionPoint
        );

        playerConnectionStates.put(player.getUniqueId(), state);
        player.sendMessage(mm.deserialize(
            "<gold>[TownyCivs]</gold> <green>⚡ Started power line connection from <yellow>" + structure.structureDef.name +
            "</yellow>.</green> <gray>Right-click another structure with a lightning rod to complete.</gray>"
        ));
    }

    /**
     * Complete a power line connection to another structure
     * Called when player right-clicks a second power tower with the power tool
     */
    public PowerLineResult completePowerLineConnection(Player player, LoadedStructure targetStructure) {
        PowerLineConnectionState state = playerConnectionStates.remove(player.getUniqueId());

        if (state == null) {
            return PowerLineResult.NO_START_POINT;
        }

        // Check if same town
        if (!state.townUuid.equals(targetStructure.town)) {
            return PowerLineResult.DIFFERENT_TOWNS;
        }

        // Check if same structure
        if (state.sourceStructureUuid.equals(targetStructure.uuid)) {
            return PowerLineResult.SAME_STRUCTURE;
        }

        // Get source and target structure definitions first for validation
        Optional<LoadedStructure> sourceOpt = findStructureByUUID(state.sourceStructureUuid);
        if (sourceOpt.isEmpty()) {
            return PowerLineResult.NOT_A_CONNECTOR;
        }
        LoadedStructure sourceStructure = sourceOpt.get();

        // Re-validate that both structures still have lightning rods
        if (findLightningRodLocation(sourceStructure) == null) {
            return PowerLineResult.NOT_A_CONNECTOR;
        }
        if (findLightningRodLocation(targetStructure) == null) {
            return PowerLineResult.NOT_A_CONNECTOR;
        }

        // Re-validate that both structures are still power connectors
        if (!isPowerConnector(sourceStructure) || !isPowerConnector(targetStructure)) {
            return PowerLineResult.NOT_A_CONNECTOR;
        }

        // Check distance
        Location targetPoint = findLightningRodLocation(targetStructure);
        if (targetPoint == null) {
            targetPoint = targetStructure.center.clone();
        }

        double distance = state.sourceLocation.distance(targetPoint);

        if (distance > MAX_POWER_LINE_DISTANCE) {
            return PowerLineResult.TOO_FAR;
        }

        // Check if connection already exists
        PowerGrid grid = getPowerGrid(targetStructure.town);
        if (grid.getConnections(state.sourceStructureUuid).contains(targetStructure.uuid)) {
            return PowerLineResult.ALREADY_CONNECTED;
        }

        // Check connection restrictions
        PowerConnectionRestriction sourceRestriction = getConnectionRestriction(sourceStructure);
        PowerConnectionRestriction targetRestriction = getConnectionRestriction(targetStructure);

        // If source is a consumer or generator (not a tower), it can only have 1 connection
        if (sourceRestriction != PowerConnectionRestriction.TOWER) {
            if (!grid.getConnections(state.sourceStructureUuid).isEmpty()) {
                return PowerLineResult.ALREADY_HAS_CONNECTION;
            }
        }

        // If target is a consumer or generator (not a tower), it can only have 1 connection
        if (targetRestriction != PowerConnectionRestriction.TOWER) {
            if (!grid.getConnections(targetStructure.uuid).isEmpty()) {
                return PowerLineResult.ALREADY_HAS_CONNECTION;
            }
        }

        // Additional restriction: consumers and generators can't connect to each other directly
        // They must connect through a power tower
        if (sourceRestriction == PowerConnectionRestriction.CONSUMER && targetRestriction == PowerConnectionRestriction.GENERATOR) {
            return PowerLineResult.INCOMPATIBLE_STRUCTURES;
        }
        if (sourceRestriction == PowerConnectionRestriction.GENERATOR && targetRestriction == PowerConnectionRestriction.CONSUMER) {
            return PowerLineResult.INCOMPATIBLE_STRUCTURES;
        }

        // Create the power line
        PowerLine powerLine = new PowerLine(
                state.sourceStructureUuid,
                targetStructure.uuid,
                state.sourceLocation,
                targetPoint
        );

        // Create visual leash connection
        createLeashConnection(powerLine);

        // Register the connection
        powerLines.put(powerLine.getUuid(), powerLine);
        grid.connect(state.sourceStructureUuid, targetStructure.uuid);

        return PowerLineResult.SUCCESS;
    }

    /**
     * Find a loaded structure by its UUID
     */
    private Optional<LoadedStructure> findStructureByUUID(UUID structureUuid) {
        return structureService.findStructureByUUID(structureUuid);
    }

    /**
     * Determine the connection type of a structure
     */
    private PowerConnectionRestriction getConnectionRestriction(LoadedStructure structure) {
        // Check if it's a power tower/connector
        if (structure.structureDef.tags != null) {
            if (structure.structureDef.tags.contains("PowerConnector")) {
                return PowerConnectionRestriction.TOWER;
            }
        }

        // Check if it's a generator
        if (structure.structureDef.production != null) {
            for (var production : structure.structureDef.production) {
                if (production.mechanic.id().equals("power_generation")) {
                    return PowerConnectionRestriction.GENERATOR;
                }
            }
        }

        // Check if it's a consumer
        if (structure.structureDef.upkeep != null) {
            for (var upkeep : structure.structureDef.upkeep) {
                if (upkeep.mechanic.id().equals("power_consumption")) {
                    return PowerConnectionRestriction.CONSUMER;
                }
            }
        }

        // Default to tower if no specific type
        return PowerConnectionRestriction.TOWER;
    }

    /**
     * Types of power structures and their connection restrictions
     */
    enum PowerConnectionRestriction {
        TOWER,      // Can connect to multiple structures
        GENERATOR,  // Can only connect to 1 structure (tower or consumer)
        CONSUMER    // Can only connect to 1 structure (tower or generator)
    }

    /**
     * Find the lightning rod block location in a structure
     * Searches in a 10x10x10 area around the center
     */
    private Location findLightningRodLocation(LoadedStructure structure) {
        Location center = structure.center;
        // can i get some debug info here
        System.out.println("[PowerService] Searching for lightning rod in structure " + structure.uuid + " at " + center);
        World world = center.getWorld();
        if (world == null) {
            System.out.println("[PowerService] World is null for structure " + structure.uuid);
            return null;
        }

        // Search for lightning rod in a 10x10x10 area (increased from 5x5x5)
        for (int x = -5; x <= 5; x++) {
            for (int y = -5; y <= 5; y++) {
                for (int z = -5; z <= 5; z++) {
                    Location checkLoc = center.clone().add(x, y, z);
                    if (checkLoc.getBlock().getType().name().contains("LIGHTNING_ROD")) {
                        // Return the center of the lightning rod block (accepts all variants)
                        System.out.println("[PowerService] Found lightning rod at " + checkLoc);
                        return checkLoc.add(0.5, 0.5, 0.5);
                    }
                }
            }
        }
        return null;
    }

    /**
     * Cancel a pending power line connection
     */
    public void cancelPowerLineConnection(Player player) {
        MiniMessage mm = MiniMessage.miniMessage();
        PowerLineConnectionState removed = playerConnectionStates.remove(player.getUniqueId());
        if (removed != null) {
            player.sendMessage(mm.deserialize("<gold>[TownyCivs]</gold> <red>Power line connection cancelled.</red>"));
        }
    }

    /**
     * Check if a player has a pending power line connection
     */
    public boolean hasPendingConnection(Player player) {
        return playerConnectionStates.containsKey(player.getUniqueId());
    }

    /**
     * Get the pending connection state for a player
     */
    public PowerLineConnectionState getPendingConnection(Player player) {
        return playerConnectionStates.get(player.getUniqueId());
    }

    /**
     * Create the visual power line connection using rotated chain item displays
     * Creates realistic sagging with multiple chain segments
     */
    private void createLeashConnection(PowerLine powerLine) {
        World world = powerLine.getPoint1().getWorld();
        if (world == null) return;

        Location start = powerLine.getPoint1();
        Location end = powerLine.getPoint2();
        double distance = start.distance(end);

        // Calculate sagging - the middle point should be lower
        // Sagging amount based on distance (realistic catenary curve approximation)
        double sagAmount = Math.min(distance * 0.15, 3.0); // Max 3 blocks of sag

        // Create many infinitesimal points along the line (one every 0.1 blocks for ultra-smooth visuals)
        List<Entity> chainEntities = new ArrayList<>();

        // Calculate number of points - one every 0.1 blocks (5x more dense)
        int totalPoints = Math.max(20, (int) (distance / 0.1));

        for (int i = 0; i < totalPoints; i++) {
            double ratio = (double) i / (totalPoints - 1);

            // Linear interpolation between start and end
            double x = start.getX() + (end.getX() - start.getX()) * ratio;
            double y = start.getY() + (end.getY() - start.getY()) * ratio;
            double z = start.getZ() + (end.getZ() - start.getZ()) * ratio;

            // Apply catenary curve for sagging (parabolic approximation)
            double sagFactor = 4 * ratio * (1 - ratio);
            y -= sagAmount * sagFactor;

            Location pointLocation = new Location(world, x, y, z);

            // Spawn small chain item display at this point
            org.bukkit.entity.ItemDisplay chainDisplay = (org.bukkit.entity.ItemDisplay) world.spawnEntity(
                pointLocation,
                EntityType.ITEM_DISPLAY
            );

            // Set chain item
            ItemStack chainItem = new ItemStack(Material.GRAY_WOOL);
            chainDisplay.setItemStack(chainItem);

            // Configure display properties
            chainDisplay.setGravity(false);
            chainDisplay.setInvulnerable(true);
            chainDisplay.setPersistent(true);
            chainDisplay.addScoreboardTag("townycivs_powerline");
            chainDisplay.setViewRange(1.0f);
            chainDisplay.setBrightness(new org.bukkit.entity.Display.Brightness(15, 15)); // Full bright

            // Get the transformation and apply very small scaling
            org.bukkit.util.Transformation transformation = chainDisplay.getTransformation();

            // Make each chain very small to look like a point in a continuous line
            transformation.getScale().set(0.08f, 0.08f, 0.08f);

            chainDisplay.setTransformation(transformation);
            chainDisplay.setInterpolationDuration(0);

            chainEntities.add(chainDisplay);
        }

        // Store first and last entity UUIDs
        if (!chainEntities.isEmpty()) {
            powerLine.setEntity1Uuid(chainEntities.get(0).getUniqueId());
            powerLine.setEntity2Uuid(chainEntities.get(chainEntities.size() - 1).getUniqueId());
        }
    }

    /**
     * Remove a power line and its visual entities
     */
    public void removePowerLine(UUID powerLineUuid) {
        PowerLine powerLine = powerLines.remove(powerLineUuid);
        if (powerLine == null) return;

        World world = powerLine.getPoint1().getWorld();
        if (world != null) {
            // Remove all armor stands along the power line
            // Get all entities between the two points and remove tagged ones
            Location point1 = powerLine.getPoint1();
            Location point2 = powerLine.getPoint2();

            // Expand the bounding box slightly to catch all entities
            double minX = Math.min(point1.getX(), point2.getX()) - 5;
            double maxX = Math.max(point1.getX(), point2.getX()) + 5;
            double minY = Math.min(point1.getY(), point2.getY()) - 5;
            double maxY = Math.max(point1.getY(), point2.getY()) + 5;
            double minZ = Math.min(point1.getZ(), point2.getZ()) - 5;
            double maxZ = Math.max(point1.getZ(), point2.getZ()) + 5;

            // Remove all townycivs_powerline entities in the area
            for (Entity entity : world.getEntities()) {
                if (entity.getScoreboardTags().contains("townycivs_powerline")) {
                    Location loc = entity.getLocation();
                    if (loc.getX() >= minX && loc.getX() <= maxX &&
                        loc.getY() >= minY && loc.getY() <= maxY &&
                        loc.getZ() >= minZ && loc.getZ() <= maxZ) {
                        entity.remove();
                    }
                }
            }
        }

        // Update grid connections
        PowerGrid grid = null;
        for (PowerGrid g : powerGrids.values()) {
            if (g.getConnectors().contains(powerLine.getStructure1())) {
                grid = g;
                break;
            }
        }
        if (grid != null) {
            grid.disconnect(powerLine.getStructure1(), powerLine.getStructure2());
        }
    }

    /**
     * Restore power line visuals after server restart
     */
    public void restorePowerLineVisuals() {
        for (PowerLine powerLine : powerLines.values()) {
            createLeashConnection(powerLine);
        }
    }

    /**
     * Clean up all power line entities (called on plugin disable)
     */
    public void cleanupAllPowerLines() {
        // Remove all entities with the townycivs_powerline tag from all worlds
        for (World world : TownyCivs.INSTANCE.getServer().getWorlds()) {
            List<Entity> toRemove = new ArrayList<>();
            for (Entity entity : world.getEntities()) {
                if (entity.getScoreboardTags().contains("townycivs_powerline")) {
                    toRemove.add(entity);
                }
            }
            toRemove.forEach(Entity::remove);
        }
    }

    /**
     * Clean up orphaned power line entities (entities without valid power lines)
     * Call this on startup or periodically to remove entities from crashed/improperly shutdown servers
     */
    public void cleanupOrphanedEntities() {
        int removed = 0;
        for (World world : TownyCivs.INSTANCE.getServer().getWorlds()) {
            List<Entity> toRemove = new ArrayList<>();

            for (Entity entity : world.getEntities()) {
                // Remove any invisible bats (from old implementation)
                if (entity.getType() == EntityType.BAT && entity.isInvisible()) {
                    toRemove.add(entity);
                    continue;
                }

                // Remove orphaned power line entities
                if (entity.getScoreboardTags().contains("townycivs_powerline")) {
                    boolean found = false;

                    // Check if this entity belongs to any active power line
                    for (PowerLine line : powerLines.values()) {
                        if (entity.getUniqueId().equals(line.getEntity1Uuid()) ||
                            entity.getUniqueId().equals(line.getEntity2Uuid())) {
                            found = true;
                            break;
                        }
                    }

                    if (!found) {
                        toRemove.add(entity);
                    }
                }
            }

            removed += toRemove.size();
            toRemove.forEach(Entity::remove);
        }

        if (removed > 0) {
            TownyCivs.INSTANCE.getLogger().info("[PowerService] Cleaned up " + removed + " orphaned power line entities");
        }
    }

    /**
     * Handle lightning rod removal or block changes
     * Call this when a lightning rod block is broken in a structure area
     */
    public void handleLightningRodChange(Location rodLocation) {
        // Find structures near this location that might be affected
        for (PowerGrid grid : powerGrids.values()) {
            for (UUID structureUuid : grid.getAllConnections().keySet()) {
                Optional<LoadedStructure> structureOpt = findStructureByUUID(structureUuid);
                if (structureOpt.isPresent()) {
                    LoadedStructure structure = structureOpt.get();
                    // If the rod location is within 10 blocks of structure center, validate connections
                    if (structure.center.distance(rodLocation) <= 10) {
                        validatePowerLinesForStructure(structureUuid);
                    }
                }
            }
        }
    }

    /**
     * Enhanced cleanup that also validates powerlines
     */
    public void performFullCleanup() {
        cleanupOrphanedEntities();
        validateAllPowerLines();
        plugin.getLogger().info("[PowerService] Performed full cleanup and validation");
    }

    /**
     * Remove all power lines connected to a specific structure
     * Called when a structure is destroyed or removed
     */
    public void removeAllPowerLinesForStructure(UUID structureUuid) {
        List<UUID> linesToRemove = new ArrayList<>();

        for (PowerLine line : powerLines.values()) {
            if (line.connectsStructure(structureUuid)) {
                linesToRemove.add(line.getUuid());
            }
        }

        for (UUID lineUuid : linesToRemove) {
            removePowerLine(lineUuid);
        }

        if (!linesToRemove.isEmpty()) {
            TownyCivs.INSTANCE.getLogger().info("[PowerService] Removed " + linesToRemove.size() + " power lines for structure " + structureUuid);
        }
    }

    /**
     * Emergency cleanup - removes ALL persistent item displays and invisible bats
     * Use this if things get messy during development/testing
     */
    public void emergencyCleanup() {
        int removed = 0;
        for (World world : TownyCivs.INSTANCE.getServer().getWorlds()) {
            List<Entity> toRemove = new ArrayList<>();

            for (Entity entity : world.getEntities()) {
                // Remove all item displays (including power lines)
                if (entity.getType() == EntityType.ITEM_DISPLAY) {
                    toRemove.add(entity);
                }
                // Remove all invisible bats
                if (entity.getType() == EntityType.BAT && entity.isInvisible()) {
                    toRemove.add(entity);
                }
            }

            removed += toRemove.size();
            toRemove.forEach(Entity::remove);
        }

        // Clear all power line tracking
        powerLines.clear();

        TownyCivs.INSTANCE.getLogger().warning("[PowerService] EMERGENCY CLEANUP: Removed " + removed + " entities and cleared all power lines");
    }

    /**
     * Check if a consumer structure is connected to the power grid (has a path to a generator or storage)
     */
    public boolean isStructureConnectedToPowerGrid(UUID structureUuid, UUID townUuid) {
        return isStructureConnectedToPowerGrid(structureUuid, townUuid, new HashSet<>());
    }

    /**
     * Internal recursive method with visited tracking to prevent circular loops
     */
    private boolean isStructureConnectedToPowerGrid(UUID structureUuid, UUID townUuid, Set<UUID> visited) {
        PowerGrid grid = powerGrids.get(townUuid);
        if (grid == null) {
            System.out.println("[PowerService] No power grid found for town " + townUuid);
            return false;
        }

        // Prevent circular recursion
        if (visited.contains(structureUuid)) {
            System.out.println("[PowerService] Circular reference detected for structure " + structureUuid);
            return false;
        }
        visited.add(structureUuid);

        // Debug: Show what's registered in the grid
        System.out.println("[PowerService] Checking structure " + structureUuid + " for town " + townUuid);
        System.out.println("[PowerService] Grid generators: " + grid.getGenerators());
        System.out.println("[PowerService] Grid consumers: " + grid.getConsumers());
        System.out.println("[PowerService] Grid connectors: " + grid.getConnectors());
        System.out.println("[PowerService] Grid storage: " + grid.getStorage());

        // Get all structures this consumer is connected to
        Set<UUID> connections = grid.getConnections(structureUuid);
        System.out.println("[PowerService] Structure " + structureUuid + " connections: " + connections);
        if (connections.isEmpty()) {
            System.out.println("[PowerService] No connections found for structure " + structureUuid);
            return false;
        }

        // Check if any connected structure is a generator or storage
        for (UUID connectedUuid : connections) {
            System.out.println("[PowerService] Checking connected structure: " + connectedUuid);

            // Check if it's a generator
            if (grid.getGenerators().contains(connectedUuid)) {
                System.out.println("[PowerService] Found connected generator: " + connectedUuid);
                return true;
            }
            // Check if it's storage with power
            if (grid.getStorage().contains(connectedUuid)) {
                double stored = grid.getCurrentStoredEnergy();
                System.out.println("[PowerService] Found connected storage: " + connectedUuid + " with " + stored + " power");
                if (stored > 0) {
                    return true;
                }
            }
            // Recursively check if connected structure is connected to a generator/storage
            if (isStructureConnectedToPowerGrid(connectedUuid, townUuid, visited)) {
                System.out.println("[PowerService] Found connection to power source through: " + connectedUuid);
                return true;
            }
        }

        System.out.println("[PowerService] No power source found for structure " + structureUuid);
        return false;
    }

    /**
     * Get all power lines for a town
     */
    public List<PowerLine> getPowerLinesForTown(UUID townUuid) {
        PowerGrid grid = powerGrids.get(townUuid);
        if (grid == null) return Collections.emptyList();

        List<PowerLine> townLines = new ArrayList<>();
        for (PowerLine line : powerLines.values()) {
            if (grid.getConnectors().contains(line.getStructure1())) {
                townLines.add(line);
            }
        }
        return townLines;
    }

    /**
     * Check if a structure can be connected with power lines
     * Returns true if structure has power generation, consumption, storage, or is a power connector
     */
    public boolean isPowerConnector(LoadedStructure structure) {
        // Check if structure has a lightning rod (required for power connections)
        if (findLightningRodLocation(structure) == null) {
            return false;
        }

        // Check tags for explicit power connector
        if (structure.structureDef.tags != null) {
            if (structure.structureDef.tags.contains("PowerConnector") ||
                structure.structureDef.tags.contains("Power") ||
                structure.structureDef.tags.contains("PowerGenerator") ||
                structure.structureDef.tags.contains("PowerConsumer") ||
                structure.structureDef.tags.contains("PowerStorage")) {
                return true;
            }
        }

        // Check if structure has power generation in production
        if (structure.structureDef.production != null) {
            for (var production : structure.structureDef.production) {
                if (production.mechanic.id().equals("power_generation") ||
                    production.mechanic.id().equals("power_storage")) {
                    return true;
                }
            }
        }

        // Check if structure has power consumption in upkeep
        if (structure.structureDef.upkeep != null) {
            for (var upkeep : structure.structureDef.upkeep) {
                if (upkeep.mechanic.id().equals("power_consumption")) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Validate if a powerline is still valid (both structures exist and have lightning rods)
     */
    public boolean isPowerLineValid(PowerLine powerLine) {
        // Check if both structures still exist
        Optional<LoadedStructure> structure1Opt = findStructureByUUID(powerLine.getStructure1());
        Optional<LoadedStructure> structure2Opt = findStructureByUUID(powerLine.getStructure2());

        if (structure1Opt.isEmpty() || structure2Opt.isEmpty()) {
            return false;
        }

        LoadedStructure structure1 = structure1Opt.get();
        LoadedStructure structure2 = structure2Opt.get();

        // Check if both structures still have lightning rods
        if (findLightningRodLocation(structure1) == null || findLightningRodLocation(structure2) == null) {
            return false;
        }

        // Check if both structures are still power-capable
        if (!isPowerConnector(structure1) || !isPowerConnector(structure2)) {
            return false;
        }

        return true;
    }

    /**
     * Validate all powerlines and remove invalid ones
     * Should be called periodically or when structures change
     */
    public void validateAllPowerLines() {
        List<UUID> invalidPowerLines = new ArrayList<>();

        for (PowerLine powerLine : powerLines.values()) {
            if (!isPowerLineValid(powerLine)) {
                invalidPowerLines.add(powerLine.getUuid());
            }
        }

        int removed = 0;
        for (UUID powerLineUuid : invalidPowerLines) {
            removePowerLine(powerLineUuid);
            removed++;
        }

        if (removed > 0) {
            plugin.getLogger().info("[PowerService] Removed " + removed + " invalid power lines during validation");
        }
    }

    /**
     * Validate powerlines connected to a specific structure
     * Called when a structure is modified or its lightning rod status changes
     */
    public void validatePowerLinesForStructure(UUID structureUuid) {
        List<UUID> linesToRemove = new ArrayList<>();

        for (PowerLine line : powerLines.values()) {
            if (line.connectsStructure(structureUuid) && !isPowerLineValid(line)) {
                linesToRemove.add(line.getUuid());
            }
        }

        for (UUID lineUuid : linesToRemove) {
            removePowerLine(lineUuid);
        }

        if (!linesToRemove.isEmpty()) {
            plugin.getLogger().info("[PowerService] Removed " + linesToRemove.size() + " invalid power lines for structure " + structureUuid);
        }
    }

    /**
     * Schedule periodic powerline validation
     * Call this during plugin startup to run validation every few minutes
     */
    public void startPeriodicValidation() {
        // Run validation every 5 minutes (6000 ticks)
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            validateAllPowerLines();
        }, 6000L, 6000L);

        plugin.getLogger().info("[PowerService] Started periodic powerline validation (every 5 minutes)");
    }

    /**
     * State for tracking a player's in-progress power line connection
     */
    public static class PowerLineConnectionState {
        public final UUID sourceStructureUuid;
        public final UUID townUuid;
        public final Location sourceLocation;
        public final long startTime;

        public PowerLineConnectionState(UUID sourceStructureUuid, UUID townUuid, Location sourceLocation) {
            this.sourceStructureUuid = sourceStructureUuid;
            this.townUuid = townUuid;
            this.sourceLocation = sourceLocation;
            this.startTime = System.currentTimeMillis();
        }
    }

    /**
     * Result of a power line connection attempt
     */
    public enum PowerLineResult {
        SUCCESS,
        NO_START_POINT,
        DIFFERENT_TOWNS,
        SAME_STRUCTURE,
        TOO_FAR,
        ALREADY_CONNECTED,
        NOT_A_CONNECTOR,
        ALREADY_HAS_CONNECTION,
        INCOMPATIBLE_STRUCTURES
    }

    /**
     * Get detailed power information for a town
     */
    public PowerStatus getPowerStatus(UUID townUuid) {
        PowerGrid grid = getPowerGrid(townUuid);
        double rawGeneration = currentTickGeneration.getOrDefault(townUuid, 0.0);
        double effectiveGeneration = Math.min(rawGeneration, grid.getMaxPowerCapacity());
        double consumption = currentTickConsumption.getOrDefault(townUuid, 0.0);
        double stored = grid.getCurrentStoredEnergy();
        double capacity = grid.getMaxPowerCapacity();
        double storageCapacity = grid.getTotalStorageCapacity();

        return new PowerStatus(townUuid, rawGeneration, effectiveGeneration, consumption, 
                              stored, capacity, storageCapacity);
    }

    /**
     * Check if town is at risk of power shortage
     */
    public boolean isPowerCritical(UUID townUuid) {
        PowerGrid grid = getPowerGrid(townUuid);
        double available = getAvailablePower(townUuid);
        double consumption = currentTickConsumption.getOrDefault(townUuid, 0.0);

        // Critical if available power is less than 10% of consumption needs
        return available < (consumption * 0.1);
    }

    /**
     * Get power efficiency ratio (how much of generation capacity is being used effectively)
     */
    public double getPowerEfficiency(UUID townUuid) {
        PowerGrid grid = getPowerGrid(townUuid);
        double capacity = grid.getMaxPowerCapacity();
        if (capacity <= 0) return 1.0;

        double effectiveGeneration = grid.getEffectivePowerGeneration();
        return effectiveGeneration / capacity;
    }

    /**
     * Initialize the power service
     * Call this during plugin startup
     */
    public void initialize() {
        // Clean up any orphaned entities from previous runs
        performFullCleanup();

        // Start periodic validation
        startPeriodicValidation();

        plugin.getLogger().info("[PowerService] Power service initialized with automatic cleanup and validation");
    }

    /**
     * Power status information for a town
     */
    public static class PowerStatus {
        public final UUID townUuid;
        public final double rawGeneration;
        public final double effectiveGeneration;
        public final double consumption;
        public final double storedEnergy;
        public final double generationCapacity;
        public final double storageCapacity;
        public final double wastedPower;
        public final double availablePower;
        public final boolean isOverCapacity;

        public PowerStatus(UUID townUuid, double rawGeneration, double effectiveGeneration, 
                          double consumption, double storedEnergy, double generationCapacity, 
                          double storageCapacity) {
            this.townUuid = townUuid;
            this.rawGeneration = rawGeneration;
            this.effectiveGeneration = effectiveGeneration;
            this.consumption = consumption;
            this.storedEnergy = storedEnergy;
            this.generationCapacity = generationCapacity;
            this.storageCapacity = storageCapacity;
            this.wastedPower = Math.max(0, rawGeneration - effectiveGeneration);
            this.availablePower = effectiveGeneration + storedEnergy - consumption;
            this.isOverCapacity = rawGeneration > generationCapacity;
        }

        @Override
        public String toString() {
            return String.format("Power[%s]: Gen %.1f/%.1f (%.1f wasted), Consumption %.1f, Stored %.1f/%.1f, Available %.1f", 
                townUuid, effectiveGeneration, generationCapacity, wastedPower, 
                consumption, storedEnergy, storageCapacity, availablePower);
        }
    }
}
