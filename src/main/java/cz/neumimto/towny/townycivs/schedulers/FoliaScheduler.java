package cz.neumimto.towny.townycivs.schedulers;

import com.palmergames.bukkit.towny.TownyUniverse;
import com.palmergames.bukkit.towny.object.Town;
import cz.neumimto.towny.townycivs.StructureInventoryService;
import cz.neumimto.towny.townycivs.StructureService;
import cz.neumimto.towny.townycivs.TownyCivs;
import cz.neumimto.towny.townycivs.config.ConfigurationService;
import cz.neumimto.towny.townycivs.config.Structure;
import cz.neumimto.towny.townycivs.db.Storage;
import cz.neumimto.towny.townycivs.mechanics.Mechanic;
import cz.neumimto.towny.townycivs.mechanics.Mechanics;
import cz.neumimto.towny.townycivs.mechanics.TownContext;
import cz.neumimto.towny.townycivs.mechanics.common.DoubleWrapper;
import cz.neumimto.towny.townycivs.model.LoadedStructure;
import cz.neumimto.towny.townycivs.power.PowerGrid;
import cz.neumimto.towny.townycivs.power.PowerService;
import eu.decentsoftware.holograms.api.DHAPI;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;

@Singleton
public class FoliaScheduler implements Runnable, Listener {

    @Inject
    private StructureService structureService;

    @Inject
    private StructureInventoryService inventoryService;

    @Inject
    private PowerService powerService;

    private Set<UUID> forceSaveNextTick = new HashSet<>();

    private long lastAutosaveTime = System.currentTimeMillis();

    @Override
    public void run() {
        if (!TownyCivs.schedulerEnabled) {
            return;
        }

        if (System.currentTimeMillis() - lastAutosaveTime > 300000) {
            lastAutosaveTime = System.currentTimeMillis();

            // Run the save ASYNCHRONOUSLY using MorePaperLib
            // This prevents the server from lagging while writing files
            TownyCivs.MORE_PAPER_LIB.scheduling().asyncScheduler().run(() -> {
                TownyCivs.INSTANCE.getLogger().info("[PowerService] Autosaving power grids...");
                for (Town town : TownyUniverse.getInstance().getTowns()) {
                    powerService.saveTownPower(town.getUUID());
                }
            });
        }

        // Tick power line visuals
        powerService.tickVisuals();

        // 1. Process the results of the previous tick and clear the maps for the new one.
        powerService.resetTickPower();

        // 2. Process each town's grid independently.
        for (Map.Entry<UUID, Set<LoadedStructure>> entry : structureService.getAllStructuresByTown().entrySet()) {
            Town town = TownyUniverse.getInstance().getTown(entry.getKey());
            if (town == null) {
                continue;
            }

            Set<LoadedStructure> townStructures = entry.getValue();

            // --- PHASE 1: CONSTANT POWER GENERATION ---
            // All active generators contribute their power every scheduler cycle.
            for (LoadedStructure structure : townStructures) {
                if (structure.editMode.get()) continue;

                for (Structure.LoadedPair<Mechanic<Object>, Object> prod : structure.structureDef.production) {
                    if (prod.mechanic.id().equals(Mechanics.POWER_GENERATION)) {
                        powerService.addPowerGeneration(structure, ((DoubleWrapper) prod.configValue).value);
                    }
                }
            }

            // --- PHASE 2: STRUCTURE TICKS (Upkeep/Production) ---
            // Process structures whose individual production timers are up.
            for (LoadedStructure structure : townStructures) {
                if (shouldTick(structure)) {
                    structure.nextTickTime = System.currentTimeMillis() + (long) structure.structureDef.period * 1000;
                    structure.lastTickTime = System.currentTimeMillis();

                    TownContext townContext = createTownContext(town, structure);
                    scheduleStructureTick(structure, townContext);
                }
            }
        }
    }

    private boolean shouldTick(LoadedStructure structure) {
        return structure.nextTickTime <= System.currentTimeMillis()
                && !structure.editMode.get()
                && structure.structureDef.period > 0;
    }

    private TownContext createTownContext(Town town, LoadedStructure structure) {
        TownContext townContext = new TownContext();
        townContext.town = town;
        townContext.structure = structure.structureDef;
        townContext.loadedStructure = structure;
        return townContext;
    }

    private void scheduleStructureTick(LoadedStructure structure, TownContext townContext) {
        UUID playerViewingInventory = inventoryService.getPlayerViewingInventory(structure);
        if (playerViewingInventory != null) {
            Player player = Bukkit.getPlayer(playerViewingInventory);
            if (player != null) {
                TownyCivs.MORE_PAPER_LIB.scheduling().entitySpecificScheduler(player)
                        .run(() -> handleStructureTick(structure, townContext),
                                () -> TownyCivs.MORE_PAPER_LIB.scheduling().asyncScheduler().run(() -> handleStructureTick(structure, townContext)));
                return;
            }
        }
        TownyCivs.MORE_PAPER_LIB.scheduling().asyncScheduler().run(() -> handleStructureTick(structure, townContext));
    }

    private void handleStructureTick(LoadedStructure structure, TownContext ctx) {
        // CHECK UPKEEP
        for (Structure.LoadedPair<Mechanic<Object>, Object> m : structure.structureDef.upkeep) {
            if (!m.mechanic.check(ctx, m.configValue)) {
                handleTickFailure(structure);
                syncBatteryCharge(structure);
                Storage.scheduleSave(structure);
                return;
            }
        }

        // CHECK PRODUCTION REQUIREMENTS
        for (Structure.LoadedPair<Mechanic<Object>, Object> m : structure.structureDef.production) {
            if (m.mechanic.id().equals(Mechanics.POWER_GENERATION)) continue; // Generation is constant, no per-tick check needed.
            if (!m.mechanic.check(ctx, m.configValue)) {
                syncBatteryCharge(structure);
                Storage.scheduleSave(structure);
                return;
            }
        }

        // EXECUTE UPKEEP ACTIONS
        for (Structure.LoadedPair<Mechanic<Object>, Object> m : structure.structureDef.upkeep) {
            m.mechanic.postAction(ctx, m.configValue);
        }

        // EXECUTE PRODUCTION ACTIONS
        for (Structure.LoadedPair<Mechanic<Object>, Object> m : structure.structureDef.production) {
            if (m.mechanic.id().equals(Mechanics.POWER_GENERATION)) continue; // Generation is constant, no per-tick action needed.
            m.mechanic.postAction(ctx, m.configValue);
        }

        // Handle saving
        structure.unsavedTickCount++;
        if (structure.unsavedTickCount % structure.structureDef.saveEachNTicks == 0 || forceSaveNextTick.contains(structure.uuid)) {
            if (!inventoryService.anyInventoryIsBeingAccessed(structure)) {
                syncBatteryCharge(structure);
                Storage.scheduleSave(structure);
                structure.unsavedTickCount = 0;
                forceSaveNextTick.remove(structure.uuid);
            } else {
                forceSaveNextTick.add(structure.uuid);
            }
        }
    }

    private void syncBatteryCharge(LoadedStructure structure) {
        PowerGrid grid = powerService.getPowerGrid(structure.town);
        if (grid != null) {
            var batteries = grid.getBatteries();
            if (batteries.containsKey(structure.uuid)) {
                structure.savedBatteryCharge = batteries.get(structure.uuid).currentCharge;
            }
        }
    }

    private void handleTickFailure(LoadedStructure structure) {
        if (structure.containerLocations.isEmpty()) {
            return;
        }

        Location structureLocation = structure.containerLocations.iterator().next();
        Sound anvilSound = Sound.sound(Key.key("minecraft:block.anvil.land"), Sound.Source.BLOCK, 1.0f, 0.5f);
        String hologramId = "Missing_Requirements_" + structure.uuid;

        if (DHAPI.getHologram(hologramId) != null) {
            DHAPI.removeHologram(hologramId);
        }

        for (Location loc : structure.containerLocations) {
            Location centeredLocation = loc.clone().add(0.5, 1.2, 0.5);

            DHAPI.createHologram(hologramId, centeredLocation, Collections.singletonList("&c&l! &7Missing Upkeep &c&l!"));

            TownyCivs.MORE_PAPER_LIB.scheduling().asyncScheduler().runDelayed(() -> {
                if (DHAPI.getHologram(hologramId) != null) {
                    DHAPI.removeHologram(hologramId);
                }
            }, java.time.Duration.ofSeconds(10));

            if (structureLocation.getWorld() != null) {
                for (Player player : structureLocation.getWorld().getPlayers()) {
                    if (player.getLocation().distance(structureLocation) <= 16.0) {
                        player.playSound(anvilSound);
                    }
                }
            }
        }
    }
}
