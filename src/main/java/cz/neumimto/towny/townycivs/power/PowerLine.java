package cz.neumimto.towny.townycivs.power;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Represents a power line connection between two power structures.
 * Uses invisible entities with leashes to create the visual power line effect.
 */
public class PowerLine {

    private final UUID uuid;
    private final UUID structure1;
    private final UUID structure2;
    private final Location point1;
    private final Location point2;

    // The invisible entities that hold the leash
    private UUID entity1Uuid;
    private UUID entity2Uuid;
    // identifier for each power line entity that takes in two structures and their two points
    private final List<UUID> entityUuids = new ArrayList<>();

    private boolean active = true;

    public PowerLine(UUID structure1, UUID structure2, Location point1, Location point2) {
        this.uuid = UUID.randomUUID();
        this.structure1 = structure1;
        this.structure2 = structure2;
        this.point1 = point1.clone();
        this.point2 = point2.clone();
    }

    public UUID getUuid() {
        return uuid;
    }

    public UUID getStructure1() {
        return structure1;
    }

    public UUID getStructure2() {
        return structure2;
    }

    public Location getPoint1() {
        return point1.clone();
    }

    public Location getPoint2() {
        return point2.clone();
    }

    public UUID getEntity1Uuid() {
        return entity1Uuid;
    }

    public void setEntity1Uuid(UUID entity1Uuid) {
        this.entity1Uuid = entity1Uuid;
    }

    public UUID getEntity2Uuid() {
        return entity2Uuid;
    }

    public void setEntity2Uuid(UUID entity2Uuid) {
        this.entity2Uuid = entity2Uuid;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public void addEntityUuid(UUID uuid) {
        this.entityUuids.add(uuid);
    }

    public List<UUID> getEntityUuids() {
        return entityUuids;
    }

    /**
     * Calculate the distance between the two connection points
     */
    public double getDistance() {
        return point1.distance(point2);
    }

    /**
     * Check if a location is one of the endpoints
     */
    public boolean hasEndpoint(Location location) {
        return point1.getBlockX() == location.getBlockX() &&
               point1.getBlockY() == location.getBlockY() &&
               point1.getBlockZ() == location.getBlockZ() ||
               point2.getBlockX() == location.getBlockX() &&
               point2.getBlockY() == location.getBlockY() &&
               point2.getBlockZ() == location.getBlockZ();
    }

    /**
     * Check if this power line connects a specific structure
     */
    public boolean connectsStructure(UUID structureUuid) {
        return structure1.equals(structureUuid) || structure2.equals(structureUuid);
    }
}

