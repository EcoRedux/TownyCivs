package cz.neumimto.towny.townycivs.mechanics;

import cz.neumimto.towny.townycivs.mechanics.common.DoubleWrapper;
import cz.neumimto.towny.townycivs.mechanics.common.PowerStorageConfig;
import cz.neumimto.towny.townycivs.power.PowerGrid;
import cz.neumimto.towny.townycivs.power.PowerService;
import cz.neumimto.towny.townycivs.TownyCivs;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Power storage mechanic - structures store excess power for later use.
 * Batteries charge when generation exceeds consumption.
 * Batteries discharge when consumption exceeds generation.
 *
 * Config example:
 * Production: [
 *   {
 *     Mechanic: power_storage
 *     Value: 1000  # Storage capacity in power units
 *   }
 * ]
 */
public class PowerStorage implements Mechanic<PowerStorageConfig> {

    @Override
    public String id() {
        return Mechanics.POWER_STORAGE;
    }

    @Override
    public boolean check(TownContext townContext, PowerStorageConfig configContext) {
        PowerService powerService = TownyCivs.injector.getInstance(PowerService.class);
        PowerGrid grid = powerService.getPowerGrid(townContext.loadedStructure.town);
        return grid != null && !grid.getConnections(townContext.loadedStructure.uuid).isEmpty();
    }

    @Override
    public void postAction(TownContext townContext, PowerStorageConfig config) {
        // Register structure with specific rates
        // Default rates to 10% of capacity if not specified
        double charge = (config.chargeRate < 0) ? config.capacity * 0.1 : config.chargeRate;
        double discharge = (config.dischargeRate < 0) ? config.capacity * 0.1 : config.dischargeRate;

        PowerService powerService = TownyCivs.injector.getInstance(PowerService.class);
        powerService.registerPowerStorage(
                townContext.loadedStructure,
                config.capacity,
                charge,
                discharge
        );
    }

    @Override
    public PowerStorageConfig getNew() {
        return new PowerStorageConfig();
    }




    @Override
    public List<ItemStack> getGuiItems(TownContext townContext, PowerStorageConfig configContext) {
        MiniMessage mm = MiniMessage.miniMessage();
        PowerService powerService = TownyCivs.injector.getInstance(PowerService.class);
        PowerGrid grid = powerService.getPowerGrid(townContext.loadedStructure.town);

        double currentStored = powerService.getStoredEnergy(townContext.loadedStructure.town);
        double maxCapacity = powerService.getTotalStorageCapacity(townContext.loadedStructure.town);
        boolean isConnected = grid != null && !grid.getConnections(townContext.loadedStructure.uuid).isEmpty();

        ItemStack batteryItem = new ItemStack(isConnected ? Material.REDSTONE_BLOCK : Material.OBSIDIAN);
        batteryItem.editMeta(meta -> {
            meta.displayName(mm.deserialize(isConnected ? "<aqua>🔋 Power Storage</aqua>" : "<red>🔋 Power Storage (Disconnected)</red>"));
            List<Component> lore = new ArrayList<>();

            // CHANGED: configContext.value -> configContext.capacity
            lore.add(mm.deserialize("<yellow>Capacity: " + configContext.capacity + " power</yellow>"));

            if (isConnected) {
                double percent = maxCapacity > 0 ? (currentStored / maxCapacity) * 100 : 0;
                lore.add(mm.deserialize("<green>Total Battery Storage: " + String.format("%.1f", currentStored) + "</green>"));
                lore.add(mm.deserialize("<gray>(" + String.format("%.1f", percent) + "% full)</gray>"));
            } else {
                lore.add(mm.deserialize("<red>⚠ Offline</red>"));
            }
            meta.lore(lore);
        });
        return Collections.singletonList(batteryItem);
    }
}
