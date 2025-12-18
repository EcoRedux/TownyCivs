package cz.neumimto.towny.townycivs.mechanics;


import com.palmergames.bukkit.towny.TownyMessaging;
import cz.neumimto.towny.townycivs.StructureService;
import cz.neumimto.towny.townycivs.TownyCivs;
import cz.neumimto.towny.townycivs.config.Structure;
import cz.neumimto.towny.townycivs.mechanics.common.StringWrapper;

import java.util.Optional;

public class Upgrade implements Mechanic<StringWrapper> {

    @Override
    public boolean check(TownContext townContext, StringWrapper configContext) {
        // Get the upgrade path from the current structure
        String upgradePath = townContext.structure.upgradePath;

        // If configContext has a value, use it (backward compatibility)
        // Otherwise use the upgradePath from the structure
        String targetStructureId = (configContext != null && configContext.value != null && !configContext.value.isEmpty())
            ? configContext.value
            : upgradePath;

        if (targetStructureId == null || targetStructureId.isEmpty()) {
            return false; // No upgrade path defined
        }

        // Check if the target upgrade structure exists
        StructureService structureService = TownyCivs.injector.getInstance(StructureService.class);
        Optional<Structure> newStructureDef = Optional.ofNullable(townContext.structure);

        if (newStructureDef.isEmpty()) {
            return false; // Target structure doesn't exist
        }

        return true; // Target structure exists
    }

    @Override
    public void postAction(TownContext townContext, StringWrapper configContext) {
        // Get the upgrade path from the current structure if configContext is not provided
        String upgradePath = townContext.structure.upgradePath;
        String targetStructureId = (configContext != null && configContext.value != null && !configContext.value.isEmpty())
            ? configContext.value
            : upgradePath;

        if (targetStructureId != null && !targetStructureId.isEmpty()) {
            TownyCivs.injector.getInstance(StructureService.class).changeStructure(townContext.loadedStructure, targetStructureId);
        }
    }

    @Override
    public void nokmessage(TownContext townContext, StringWrapper configuration) {
        if (townContext.player != null) {
            townContext.player.sendMessage(townContext.structure.name + " has no upgrade path or missing requirements.");
        }
    }

    @Override
    public void okmessage(TownContext townContext, StringWrapper configuration) {
        String upgradePath = townContext.structure.upgradePath;
        String targetName = (configuration != null && configuration.value != null) ? configuration.value : upgradePath;
        TownyMessaging.sendPrefixedTownMessage(townContext.town,
            townContext.player.getName() + " has upgraded a structure " + townContext.loadedStructure.structureDef.name + " to " + targetName + ".");
    }

    @Override
    public String id() {
        return Mechanics.UPGRADE;
    }

    @Override
    public StringWrapper getNew() {
        return new StringWrapper();
    }
}
