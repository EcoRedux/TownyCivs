package cz.neumimto.towny.townycivs.power;

import com.palmergames.bukkit.towny.object.Town;
import cz.neumimto.towny.townycivs.StructureService;
import cz.neumimto.towny.townycivs.SubclaimService;
import cz.neumimto.towny.townycivs.TownyCivs;
import cz.neumimto.towny.townycivs.model.LoadedStructure;
import com.palmergames.bukkit.towny.TownyAPI;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.*;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

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

    @Inject
    private cz.neumimto.towny.townycivs.db.Flatfile flatfileStorage;

    /**
     * Add power generation from a structure (called during production tick)
     * Respects town power capacity limits
     */
    public void addPowerGeneration(LoadedStructure structure, double amount) {
        PowerGrid grid = getPowerGrid(structure.town);
        double maxCapacity = grid.getMaxPowerCapacity(); // The hard cap from town level

        double currentTotalGeneration = currentTickGeneration.getOrDefault(structure.town, 0.0);

        // Only add power if the grid is not already at its generation cap
        if (currentTotalGeneration < maxCapacity) {
            double canAdd = maxCapacity - currentTotalGeneration;
            double amountToAdd = Math.min(amount, canAdd);
            currentTickGeneration.merge(structure.town, amountToAdd, Double::sum);
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

        double generatedThisTick = currentTickGeneration.getOrDefault(townUuid, 0.0);
        double consumedThisTick = currentTickConsumption.getOrDefault(townUuid, 0.0);

        // Available = Active Generation + Max Potential Discharge from Batteries - Consumption
        return generatedThisTick + grid.getMaxDischargeRate() - consumedThisTick;
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
    public void registerPowerStorage(LoadedStructure structure, double capacity, double chargeRate, double dischargeRate) {
        PowerGrid grid = getPowerGrid(structure.town);

        // Check if battery already exists to prevent overwriting charge with stale saved data
        boolean alreadyExists = grid.getBatteries().containsKey(structure.uuid);

        grid.addBattery(structure.uuid, capacity, chargeRate, dischargeRate);

        // Restore charge if it was saved AND it's a new registration
        if (!alreadyExists && structure.savedBatteryCharge > 0) {
            PowerGrid.BatteryState state = grid.getBatteries().get(structure.uuid);
            if (state != null) {
                // Clamp charge to capacity (in case config changed)
                state.currentCharge = Math.min(structure.savedBatteryCharge, capacity);
            }
        }
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
        for (UUID townUuid : powerGrids.keySet()) {
            PowerGrid grid = powerGrids.get(townUuid);
            if (grid == null) continue;

            double generated = currentTickGeneration.getOrDefault(townUuid, 0.0);
            double consumed = currentTickConsumption.getOrDefault(townUuid, 0.0);

            grid.setLastTickGeneration(generated);
            grid.setLastTickConsumption(consumed);

            double netPower = generated - consumed;

            if (netPower > 0) {
                // SURPLUS: Batteries act as CONSUMERS
                // Distribute surplus to batteries based on their Charge Rate
                double surplus = netPower;

                for (PowerGrid.BatteryState battery : grid.getBatteries().values()) {
                    if (surplus <= 0) break;

                    // How much empty space is left?
                    double space = battery.maxCapacity - battery.currentCharge;

                    // We can input the minimum of: Available Surplus, Battery Max Charge Rate, Battery Empty Space
                    double input = Math.min(surplus, Math.min(battery.chargeRate, space));

                    if (input > 0) {
                        battery.currentCharge += input;
                        surplus -= input;
                    }
                }

            } else if (netPower < 0) {
                // DEFICIT: Batteries act as GENERATORS
                // Draw from batteries based on their Discharge Rate
                double deficit = Math.abs(netPower);

                for (PowerGrid.BatteryState battery : grid.getBatteries().values()) {
                    if (deficit <= 0) break;

                    // We can output the minimum of: Needed Power, Battery Max Discharge Rate, Battery Current Charge
                    double output = Math.min(deficit, Math.min(battery.dischargeRate, battery.currentCharge));

                    if (output > 0) {
                        battery.currentCharge -= output;
                        deficit -= output;
                    }
                }
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
    public void startPowerLineConnection(Player player, LoadedStructure structure, Location clickedLocation) {
        MiniMessage mm = MiniMessage.miniMessage();
        Location connectionPoint = clickedLocation.clone().add(0.5, 0.5, 0.5);
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
    public PowerLineResult completePowerLineConnection(Player player, LoadedStructure targetStructure, Location clickedLocation) {
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


        PowerGrid grid = getPowerGrid(targetStructure.town);
        if (grid.getConnections(state.sourceStructureUuid).contains(targetStructure.uuid)) {
            // Find the physical line object
            PowerLine existingLine = findPowerLine(state.sourceStructureUuid, targetStructure.uuid);

            if (existingLine != null) {
                removePowerLine(existingLine.getUuid());
                return PowerLineResult.DISCONNECTED; // Return new status
            }

            // Fallback if logic desyncs (shouldn't happen)
            return PowerLineResult.ALREADY_CONNECTED;
        }


        // Get source and target structure definitions first for validation
        Optional<LoadedStructure> sourceOpt = findStructureByUUID(state.sourceStructureUuid);
        if (sourceOpt.isEmpty()) {
            return PowerLineResult.NOT_A_CONNECTOR;
        }
        LoadedStructure sourceStructure = sourceOpt.get();

        // Re-validate that both structures still have lightning rods
        if (!hasLightningRod(sourceStructure)) {
            return PowerLineResult.NOT_A_CONNECTOR;
        }
        if (!hasLightningRod(targetStructure)) {
            return PowerLineResult.NOT_A_CONNECTOR;
        }

        // Re-validate that both structures are still power connectors
        if (!isPowerConnector(sourceStructure) || !isPowerConnector(targetStructure)) {
            return PowerLineResult.NOT_A_CONNECTOR;
        }

        // Check distance
        Location targetPoint = clickedLocation.clone().add(0.5, 0.5, 0.5);
        if (targetPoint == null) {
            targetPoint = targetStructure.center.clone();
        }

        double distance = state.sourceLocation.distance(targetPoint);

        if (distance > MAX_POWER_LINE_DISTANCE) {
            return PowerLineResult.TOO_FAR;
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
        boolean sourceIsTower = sourceRestriction == PowerConnectionRestriction.TOWER;
        boolean targetIsTower = targetRestriction == PowerConnectionRestriction.TOWER;

        if (!sourceIsTower && !targetIsTower) {
            // This blocks:
            // - Storage <-> Storage
            // - Storage <-> Generator
            // - Storage <-> Consumer
            // - Generator <-> Consumer
            return PowerLineResult.INCOMPATIBLE_STRUCTURES;
        }

        int cost = (int) Math.ceil(distance);

        // Only charge survival/adventure players
        if (player.getGameMode() != GameMode.CREATIVE) {
            // Check if player has enough copper
            if (!player.getInventory().contains(Material.COPPER_INGOT, cost)) {
                return PowerLineResult.INSUFFICIENT_RESOURCES;
            }

            // Deduct items
            player.getInventory().removeItem(new ItemStack(Material.COPPER_INGOT, cost));
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
            if (structure.structureDef.tags.contains("PowerStorage")) {
                return PowerConnectionRestriction.STORAGE; // Add this
            }
        }

        // Check if it's a generator
        if (structure.structureDef.production != null) {
            for (var production : structure.structureDef.production) {
                String mechId = production.mechanic.id();
                if (mechId.equals("power_generation")) {
                    return PowerConnectionRestriction.GENERATOR;
                }
                if (mechId.equals("power_storage")) {
                    return PowerConnectionRestriction.STORAGE; // Add this
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
        CONSUMER,  // Can only connect to 1 structure (tower or generator)
        STORAGE // Can only connect to 1 structure (tower)
    }

    /**
     * Find the lightning rod block location in a structure
     * Searches in a 10x10x10 area around the center
     */
    private boolean hasLightningRod(LoadedStructure structure) {
        Location center = structure.center;
        // can i get some debug info here
        System.out.println("[PowerService] Searching for lightning rod in structure " + structure.uuid + " at " + center);
        World world = center.getWorld();
        if (world == null) {
            System.out.println("[PowerService] World is null for structure " + structure.uuid);
            return false;
        }

        // Search for lightning rod in a 10x10x10 area (increased from 5x5x5)
        for (int x = -5; x <= 5; x++) {
            for (int y = -5; y <= 5; y++) {
                for (int z = -5; z <= 5; z++) {
                    Location checkLoc = center.clone().add(x, y, z);
                    if (checkLoc.getBlock().getType().name().contains("LIGHTNING_ROD")) {
                        // Return the center of the lightning rod block (accepts all variants)
                        System.out.println("[PowerService] Found lightning rod at " + checkLoc);
                        return true;
                    }
                }
            }
        }
        return false;
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
            BlockDisplay chainDisplay = (BlockDisplay) world.spawnEntity(
                pointLocation,
                EntityType.BLOCK_DISPLAY
            );

            // Set chain item

            chainDisplay.setBlock(Material.GRAY_WOOL.createBlockData());

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
            powerLine.addEntityUuid(chainDisplay.getUniqueId());
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

        // 1. Remove Visual Entities
        World world = powerLine.getPoint1().getWorld();
        if (world != null) {
            for (UUID entityId : powerLine.getEntityUuids()) {
                Entity entity = world.getEntity(entityId);
                if (entity != null) {
                    entity.remove();
                }
            }
        }

        // 2. Update Logical Grid Connections
        Optional<LoadedStructure> structOpt = findStructureByUUID(powerLine.getStructure1());
        if (structOpt.isPresent()) {
            PowerGrid grid = getPowerGrid(structOpt.get().town);
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
            return false;
        }
        visited.add(structureUuid);

        // A structure is connected if it IS a generator or powered storage
        if (grid.getGenerators().contains(structureUuid)) {
            return true;
        }
        if (grid.getStorage().contains(structureUuid) && grid.getCurrentStoredEnergy() > 0) {
            return true;
        }

        // Or if it's connected TO a structure that is connected
        Set<UUID> connections = grid.getConnections(structureUuid);
        if (connections.isEmpty()) {
            return false;
        }

        for (UUID connectedUuid : connections) {
            if (isStructureConnectedToPowerGrid(connectedUuid, townUuid, visited)) {
                return true;
            }
        }

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
        if (!hasLightningRod(structure)) {
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
        if (!hasLightningRod(structure1) || !hasLightningRod(structure2)) {
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
     * Call this during plugin startup to run validation every 5 minutes
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
        DISCONNECTED,
        NO_START_POINT,
        DIFFERENT_TOWNS,
        SAME_STRUCTURE,
        TOO_FAR,
        ALREADY_CONNECTED,
        NOT_A_CONNECTOR,
        ALREADY_HAS_CONNECTION,
        INCOMPATIBLE_STRUCTURES,
        INSUFFICIENT_RESOURCES
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

    public PowerLine findPowerLine(UUID structure1Uuid, UUID structure2Uuid) {
        // Iterate through all active power lines to find the matching pair
        for (PowerLine line : powerLines.values()) {
            // Check if this line connects both of our target structures
       // We use 'connectsStructure' which you already have in PowerLine.java [cite: 2078]
            if (line.connectsStructure(structure1Uuid) && line.connectsStructure(structure2Uuid)) {
                return line;
            }
        }
        return null; // No connection exists between these two
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

    /**
     * Ticks visual effects for all power lines.
     * Called by FoliaScheduler.
     */
    public void tickVisuals() {
        for (PowerLine line : powerLines.values()) {
            animatePowerLine(line);
        }
    }

    private void animatePowerLine(PowerLine line) {
        // 1. Get the Grid to check power status
        Optional<LoadedStructure> struct1Opt = findStructureByUUID(line.getStructure1());
        if (struct1Opt.isEmpty()) return;

        LoadedStructure struct1 = struct1Opt.get();
        // We still get the grid for other checks, but we won't call getAvailablePower on it
        PowerGrid grid = getPowerGrid(struct1.town);
        if (grid == null) return;

        // 2. Resolve endpoints
        Optional<LoadedStructure> struct2Opt = findStructureByUUID(line.getStructure2());
        if (struct2Opt.isEmpty()) return;
        LoadedStructure struct2 = struct2Opt.get();

        PowerConnectionRestriction type1 = getConnectionRestriction(struct1);
        PowerConnectionRestriction type2 = getConnectionRestriction(struct2);

        Location start = null;
        Location end = null;
        boolean shouldRender = false;

        // LOGIC: Determine direction and if it should glow

        // Case A: Generator -> Network (Always glows if connected)
        if (type1 == PowerConnectionRestriction.GENERATOR) {
            start = line.getPoint1();
            end = line.getPoint2();
            shouldRender = true;
        }
        else if (type2 == PowerConnectionRestriction.GENERATOR) {
            start = line.getPoint2();
            end = line.getPoint1();
            shouldRender = true;
        }
        // Case B: Network -> Consumer (Glows ONLY if grid has power)
        else if (type1 == PowerConnectionRestriction.CONSUMER) {
            start = line.getPoint2(); // From Pole
            end = line.getPoint1();   // To Consumer
            // FIX: Call 'this.getAvailablePower', NOT 'grid.getAvailablePower'
            shouldRender = this.getAvailablePower(struct1.town) > 0;
        }
        else if (type2 == PowerConnectionRestriction.CONSUMER) {
            start = line.getPoint1(); // From Pole
            end = line.getPoint2();   // To Consumer
            // FIX: Call 'this.getAvailablePower', NOT 'grid.getAvailablePower'
            shouldRender = this.getAvailablePower(struct1.town) > 0;
        }
        // Case C: Pole <-> Pole (Optional: Static sparkles to show grid is live)
        else if (type1 == PowerConnectionRestriction.TOWER && type2 == PowerConnectionRestriction.TOWER) {
            // FIX: Call 'this.getAvailablePower'
            if (this.getAvailablePower(struct1.town) > 0 && Math.random() > 0.7) {
                spawnStaticSparkle(line);
            }
            return; // Done for poles
        }

        // 3. Render the particle flow
        if (shouldRender && start != null && end != null) {
            spawnFlowingParticles(start, end);
        }
    }
    private void spawnFlowingParticles(Location from, Location to) {
        World world = from.getWorld();
        if (world == null) return;

        double distance = from.distance(to);

        // Calculate sagging parameters (must match createLeashConnection)
        double sagAmount = Math.min(distance * 0.15, 3.0);

        // Animate based on system time
        double speed = 5.0; // Speed of the spark
        double timeOffset = (System.currentTimeMillis() / 1000.0) * speed;

        // We can spawn multiple particles per tick for a "stream" effect
        // Or just one moving particle. Let's do one moving particle for clarity.
        double positionRatio = (timeOffset % distance) / distance; // 0.0 to 1.0

        // Linear interpolation for X and Z
        double x = from.getX() + (to.getX() - from.getX()) * positionRatio;
        double z = from.getZ() + (to.getZ() - from.getZ()) * positionRatio;

        // Linear interpolation for Y baseline
        double yBase = from.getY() + (to.getY() - from.getY()) * positionRatio;

        // Apply Parabolic Sagging (Catenary approximation)
        // Formula: 4 * r * (1 - r) creates a hump at 0.5
        double sagFactor = 4 * positionRatio * (1 - positionRatio);
        double y = yBase - (sagAmount * sagFactor);

        Location particleLoc = new Location(world, x, y, z);

        // Spawn Yellow "Electric" Spark
        Particle.DustOptions dust = new Particle.DustOptions(Color.YELLOW, 1.0f);
        // Use REDSTONE particle with color data
        world.spawnParticle(Particle.DUST, particleLoc, 1, 0, 0, 0, 0, dust);
    }

    private void spawnStaticSparkle(PowerLine line) {
        // Random point on the wire
        double t = Math.random();
        Location p1 = line.getPoint1();
        Location p2 = line.getPoint2();

        double x = p1.getX() + (p2.getX() - p1.getX()) * t;
        double y = p1.getY() + (p2.getY() - p1.getY()) * t;
        double z = p1.getZ() + (p2.getZ() - p1.getZ()) * t;

        if (p1.getWorld() != null) {
            p1.getWorld().spawnParticle(Particle.WAX_ON, x, y, z, 1, 0, 0, 0, 0);
        }
    }

    private String serializeLoc(Location loc) {
        if (loc == null || loc.getWorld() == null) return "null";
        return loc.getWorld().getName() + ";" + loc.getX() + ";" + loc.getY() + ";" + loc.getZ();
    }

    private Location deserializeLoc(String s) {
        if (s == null || s.equals("null")) return null;
        try {
            String[] parts = s.split(";");
            if (parts.length < 4) return null;

            World world = Bukkit.getWorld(parts[0]);
            if (world == null) return null; // World might be unloaded or deleted

            double x = Double.parseDouble(parts[1]);
            double y = Double.parseDouble(parts[2]);
            double z = Double.parseDouble(parts[3]);

            return new Location(world, x, y, z);
        } catch (Exception e) {
            TownyCivs.logger.warning("Failed to deserialize location: " + s);
            return null;
        }
    }

    public void saveTownPower(UUID townUuid) {
        // FIX: Use .get() directly. Do NOT use getPowerGrid() here.
        // getPowerGrid() creates a new grid if one doesn't exist, which we don't want during saving.
        PowerGrid grid = powerGrids.get(townUuid);

        // 1. If no grid exists in memory, stop.
        if (grid == null) return;

        // 2. If grid is empty (no lines, no batteries), stop.
        // This prevents saving empty "skeleton" files for towns that just placed a pole and broke it.
        List<PowerLine> lines = getPowerLinesForTown(townUuid);
        if (grid.getBatteries().isEmpty() && lines.isEmpty()) {
            return;
        }

        // --- EXISTING SAVE LOGIC ---

        // 1. Save Batteries
        for (Map.Entry<UUID, PowerGrid.BatteryState> entry : grid.getBatteries().entrySet()) {
            structureService.findStructureByUUID(entry.getKey()).ifPresent(struct -> {
                struct.savedBatteryCharge = entry.getValue().currentCharge;
                cz.neumimto.towny.townycivs.db.Storage.saveAll(java.util.Collections.singleton(struct));
            });
        }

        // 2. Save Power Lines
        List<Map<String, String>> serializedLines = new ArrayList<>();
        for (PowerLine line : lines) {
            Map<String, String> map = new HashMap<>();
            map.put("uuid", line.getUuid().toString());
            map.put("s1", line.getStructure1().toString());
            map.put("s2", line.getStructure2().toString());
            map.put("p1", serializeLoc(line.getPoint1()));
            map.put("p2", serializeLoc(line.getPoint2()));
            serializedLines.add(map);
        }

        flatfileStorage.savePowerNetwork(townUuid, serializedLines);
    }

    public void loadTownPower(UUID townUuid) {
        // Clear existing lines for this town before loading
        List<PowerLine> toRemove = new ArrayList<>();
        for (PowerLine line : powerLines.values()) {
            Optional<LoadedStructure> s1 = structureService.findStructureByUUID(line.getStructure1());
            if (s1.isPresent() && s1.get().town.equals(townUuid)) {
                toRemove.add(line);
            }
        }
        for (PowerLine line : toRemove) {
            removePowerLine(line.getUuid());
        }

        // Load from Flatfile
        List<Map<String, String>> data = flatfileStorage.loadPowerNetwork(townUuid);

        for (Map<String, String> entry : data) {
            try {
                UUID s1 = UUID.fromString(entry.get("s1"));
                UUID s2 = UUID.fromString(entry.get("s2"));
                Location p1 = deserializeLoc(entry.get("p1")); // Use helper method
                Location p2 = deserializeLoc(entry.get("p2")); // Use helper method

                PowerLine line = new PowerLine(s1, s2, p1, p2);

                // Restore Logic
                powerLines.put(line.getUuid(), line);
                PowerGrid grid = getPowerGrid(townUuid); // Get grid safely
                grid.connect(s1, s2);

                // Restore Visuals
                createLeashConnection(line);
            } catch (Exception e) {
                TownyCivs.logger.warning("Error loading power line for town " + townUuid);
            }
        }
    }
}
