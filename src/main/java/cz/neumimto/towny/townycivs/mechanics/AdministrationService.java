package cz.neumimto.towny.townycivs.mechanics;

import com.palmergames.bukkit.towny.object.Town;
import cz.neumimto.towny.townycivs.model.LoadedStructure;

import javax.inject.Singleton;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service to track which towns have active administration (Town Hall with satisfied upkeep).
 *
 * A town can only invite residents and claim land if:
 * 1. They have a Town Hall structure with the "administration" production mechanic
 * 2. The Town Hall's upkeep requirements are satisfied (e.g., paper in the inventory)
 *
 * When upkeep fails, the administration becomes inactive and the town loses these abilities
 * until they restore the upkeep items.
 */
@Singleton
public class AdministrationService {

    // Map of Town UUID -> AdminStatus (tracks active administration level and structure)
    private final Map<UUID, AdminStatus> activeAdministration = new ConcurrentHashMap<>();

    /**
     * Set the administration status for a town's structure
     */
    public void setAdministrationActive(Town town, LoadedStructure structure, int adminLevel, boolean active) {
        if (town == null) return;

        UUID townUuid = town.getUUID();

        if (active) {
            AdminStatus current = activeAdministration.get(townUuid);
            // Only update if this is a higher level or same structure
            if (current == null || adminLevel >= current.adminLevel || structure.uuid.equals(current.structureUuid)) {
                activeAdministration.put(townUuid, new AdminStatus(structure.uuid, adminLevel, true));
            }
        } else {
            AdminStatus current = activeAdministration.get(townUuid);
            // Only deactivate if this is the same structure
            if (current != null && structure.uuid.equals(current.structureUuid)) {
                activeAdministration.put(townUuid, new AdminStatus(structure.uuid, current.adminLevel, false));
            }
        }
    }

    /**
     * Check if a town has active administration at or above the required level
     */
    public boolean hasActiveAdministration(Town town, int requiredLevel) {
        if (town == null) return false;

        AdminStatus status = activeAdministration.get(town.getUUID());
        if (status == null) {
            return false;
        }

        return status.active && status.adminLevel >= requiredLevel;
    }

    /**
     * Check if a town has any active administration (level 1+)
     */
    public boolean hasActiveAdministration(Town town) {
        return hasActiveAdministration(town, 1);
    }

    /**
     * Get the current active administration level for a town
     * Returns 0 if no active administration
     */
    public int getActiveAdministrationLevel(Town town) {
        if (town == null) return 0;

        AdminStatus status = activeAdministration.get(town.getUUID());
        if (status == null || !status.active) {
            return 0;
        }

        return status.adminLevel;
    }

    /**
     * Called when a structure's upkeep fails - deactivates the administration
     */
    public void onUpkeepFailed(Town town, LoadedStructure structure) {
        if (town == null || structure == null) return;

        AdminStatus status = activeAdministration.get(town.getUUID());
        if (status != null && structure.uuid.equals(status.structureUuid)) {
            setAdministrationActive(town, structure, status.adminLevel, false);
        }
    }

    /**
     * Remove administration tracking when a structure is deleted
     */
    public void onStructureRemoved(Town town, LoadedStructure structure) {
        if (town == null || structure == null) return;

        AdminStatus status = activeAdministration.get(town.getUUID());
        if (status != null && structure.uuid.equals(status.structureUuid)) {
            activeAdministration.remove(town.getUUID());
        }
    }

    /**
     * Clear all administration data for a town (e.g., when town is deleted)
     */
    public void clearTown(Town town) {
        if (town != null) {
            activeAdministration.remove(town.getUUID());
        }
    }

    /**
     * Internal class to track administration status
     */
    private static class AdminStatus {
        final UUID structureUuid;
        final int adminLevel;
        final boolean active;

        AdminStatus(UUID structureUuid, int adminLevel, boolean active) {
            this.structureUuid = structureUuid;
            this.adminLevel = adminLevel;
            this.active = active;
        }
    }
}
