package cz.neumimto.towny.townycivs.mechanics;


import com.palmergames.bukkit.towny.TownyMessaging;
import cz.neumimto.towny.townycivs.StructureService;
import cz.neumimto.towny.townycivs.TownyCivs;
import cz.neumimto.towny.townycivs.config.ConfigurationService;
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

        // Check if the current structure exists
        Optional<Structure> newStructureDef = Optional.ofNullable(townContext.structure);
        if (newStructureDef.isEmpty()) {
            return false;
        }

        // Get the target structure and check MaxCount
        ConfigurationService configService = TownyCivs.injector.getInstance(ConfigurationService.class);
        Optional<Structure> targetStructureOpt = configService.findStructureById(targetStructureId);

        if (targetStructureOpt.isEmpty()) {
            return false; // Target structure not found in config
        }

        Structure targetStructure = targetStructureOpt.get();

        // Check MaxCount for the target structure
        if (targetStructure.maxCount != null && targetStructure.maxCount > 0) {
            StructureService structureService = TownyCivs.injector.getInstance(StructureService.class);
            int currentCount = structureService.findTownStructureById(townContext.town, targetStructure).count;

            if (currentCount >= targetStructure.maxCount) {
                return false; // Would exceed MaxCount for target structure
            }
        }

        return true; // All checks passed
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
            String upgradePath = townContext.structure.upgradePath;
            String targetStructureId = (configuration != null && configuration.value != null && !configuration.value.isEmpty())
                ? configuration.value
                : upgradePath;

            if (targetStructureId == null || targetStructureId.isEmpty()) {
                townContext.player.sendMessage("§c" + townContext.structure.name + " has no upgrade path defined.");
                return;
            }

            ConfigurationService configService = TownyCivs.injector.getInstance(ConfigurationService.class);
            Optional<Structure> targetStructureOpt = configService.findStructureById(targetStructureId);

            if (targetStructureOpt.isEmpty()) {
                townContext.player.sendMessage("§cUpgrade target '" + targetStructureId + "' does not exist.");
                return;
            }

            Structure targetStructure = targetStructureOpt.get();

            // Check if MaxCount is the issue
            if (targetStructure.maxCount != null && targetStructure.maxCount > 0) {
                StructureService structureService = TownyCivs.injector.getInstance(StructureService.class);
                int currentCount = structureService.findTownStructureById(townContext.town, targetStructure).count;
                if (currentCount >= targetStructure.maxCount) {
                    townContext.player.sendMessage("§cCannot upgrade to " + targetStructure.name + ". Maximum count reached (" + currentCount + "/" + targetStructure.maxCount + ").");
                    return;
                }
            }

            townContext.player.sendMessage("§c" + townContext.structure.name + " cannot be upgraded. Requirements not met.");
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
