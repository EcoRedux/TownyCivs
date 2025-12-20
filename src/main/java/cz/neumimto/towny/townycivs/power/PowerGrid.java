package cz.neumimto.towny.townycivs.power;

import com.palmergames.bukkit.towny.object.Town;
import cz.neumimto.towny.townycivs.model.LoadedStructure;
import org.bukkit.Location;

import java.util.*;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Town;
import cz.neumimto.towny.townycivs.TownyCivs;

/**
 * Represents a power grid for a town.
 * Contains all power-related structures and their connections.
 */
public class PowerGrid {

    private final UUID townUuid;
    private final Set<UUID> generators = new HashSet<>();      // Structures that generate power
    private final Set<UUID> consumers = new HashSet<>();       // Structures that consume power
    private final Set<UUID> storage = new HashSet<>();         // Structures that store power (batteries)
    private final Set<UUID> connectors = new HashSet<>();      // Power line structures (towers)
    private final Map<UUID, Set<UUID>> connections = new HashMap<>(); // Power line connections between structures

    private double totalGeneration = 0;
    private double totalConsumption = 0;
    private double totalStorageCapacity = 0;
    private double currentStoredEnergy = 0;

    public PowerGrid(UUID townUuid) {
        this.townUuid = townUuid;
    }

    public UUID getTownUuid() {
        return townUuid;
    }

    public void addGenerator(UUID structureUuid) {
        generators.add(structureUuid);
    }

    public void addConsumer(UUID structureUuid) {
        consumers.add(structureUuid);
    }

    public void addStorage(UUID structureUuid) {
        storage.add(structureUuid);
    }

    public void addConnector(UUID structureUuid) {
        connectors.add(structureUuid);
        connections.putIfAbsent(structureUuid, new HashSet<>());
    }

    public void removeStructure(UUID structureUuid) {
        generators.remove(structureUuid);
        consumers.remove(structureUuid);
        storage.remove(structureUuid);
        connectors.remove(structureUuid);
        connections.remove(structureUuid);
        // Remove from all connection sets
        connections.values().forEach(set -> set.remove(structureUuid));
    }

    /**
     * Connect two power structures with a power line
     * Any structure with power capabilities (generation/consumption/storage/connector) can be connected
     */
    public boolean connect(UUID structure1, UUID structure2) {
        // Ensure both structures have connection entries
        connections.putIfAbsent(structure1, new HashSet<>());
        connections.putIfAbsent(structure2, new HashSet<>());

        // Add bidirectional connection
        connections.get(structure1).add(structure2);
        connections.get(structure2).add(structure1);
        return true;
    }

    /**
     * Disconnect two power structures
     */
    public void disconnect(UUID structure1, UUID structure2) {
        if (connections.containsKey(structure1)) {
            connections.get(structure1).remove(structure2);
        }
        if (connections.containsKey(structure2)) {
            connections.get(structure2).remove(structure1);
        }
    }

    /**
     * Get all structures connected to a given structure
     */
    public Set<UUID> getConnections(UUID structureUuid) {
        return connections.getOrDefault(structureUuid, Collections.emptySet());
    }

    /**
     * Get the maximum power generation capacity for this town based on its level
     */
    public double getMaxPowerCapacity() {
        try {
            Town town = TownyAPI.getInstance().getTown(townUuid);
            if (town == null) return 0; // Default to Settlement

            Integer townLevel = town.getLevelNumber();
            return getPowerCapacityFromConfig(townLevel);
        } catch (Exception e) {
            return getPowerCapacityFromConfig(1); // Fallback
        }
    }

    /**
     * Get power capacity limit from configuration
     */
    private double getPowerCapacityFromConfig(Integer townLevel) {
        try {
            Double capacity = TownyCivs.INSTANCE.configurationService.config.powerCapacity.get(townLevel);
            if (capacity != null) {
                return capacity;
            }
        } catch (Exception e) {
            // Fall through to hardcoded fallback
        }

        // Fallback values if config is missing or null
        return switch (townLevel) {
            case 0 -> 0.0;
            case 1 -> 100.0;
            case 2 -> 250.0;
            case 3 -> 500.0;
            case 4 -> 1000.0;
            case 5 -> 2000.0;
            case 6 -> 4000.0;
            case 7 -> 7500.0;
            case 8 -> 15000.0;
            default -> 100.0;
        };
    }

    /**
     * Check if adding this much power generation would exceed town capacity
     */
    public boolean canAddPowerGeneration(double additionalPower) {
        return (totalGeneration + additionalPower) <= getMaxPowerCapacity();
    }

    /**
     * Get remaining power capacity
     */
    public double getRemainingCapacity() {
        return Math.max(0, getMaxPowerCapacity() - totalGeneration);
    }

    /**
     * Get effective power generation (capped by town level)
     */
    public double getEffectivePowerGeneration() {
        return Math.min(totalGeneration, getMaxPowerCapacity());
    }

    /**
     * Check if the grid has enough power for all consumers
     */
    public boolean hasSufficientPower() {
        double effectivePower = getEffectivePowerGeneration();
        double availablePower = effectivePower + Math.min(currentStoredEnergy, getMaxDischargeRate());
        return availablePower >= totalConsumption;
    }

    /**
     * Get power deficit (negative) or surplus (positive)
     */
    public double getPowerBalance() {
        return getEffectivePowerGeneration() - totalConsumption;
    }

    /**
     * Check if town is at or near power capacity limit
     */
    public boolean isNearCapacityLimit() {
        double capacity = getMaxPowerCapacity();
        return totalGeneration >= (capacity * 0.9); // 90% of capacity
    }

    private double getMaxDischargeRate() {
        // TODO: Calculate from storage structures
        return 100.0;
    }

    // Getters and setters
    public double getTotalGeneration() { return totalGeneration; }
    public void setTotalGeneration(double totalGeneration) { this.totalGeneration = totalGeneration; }

    public double getTotalConsumption() { return totalConsumption; }
    public void setTotalConsumption(double totalConsumption) { this.totalConsumption = totalConsumption; }

    public double getTotalStorageCapacity() { return totalStorageCapacity; }
    public void setTotalStorageCapacity(double totalStorageCapacity) { this.totalStorageCapacity = totalStorageCapacity; }

    public double getCurrentStoredEnergy() { return currentStoredEnergy; }
    public void setCurrentStoredEnergy(double currentStoredEnergy) {
        this.currentStoredEnergy = Math.max(0, Math.min(currentStoredEnergy, totalStorageCapacity));
    }

    public Set<UUID> getGenerators() { return Collections.unmodifiableSet(generators); }
    public Set<UUID> getConsumers() { return Collections.unmodifiableSet(consumers); }
    public Set<UUID> getStorage() { return Collections.unmodifiableSet(storage); }
    public Set<UUID> getConnectors() { return Collections.unmodifiableSet(connectors); }
    public Map<UUID, Set<UUID>> getAllConnections() { return Collections.unmodifiableMap(connections); }
}

