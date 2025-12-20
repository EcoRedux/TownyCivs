package cz.neumimto.towny.townycivs.mechanics;

import cz.neumimto.towny.townycivs.mechanics.common.DoubleWrapper;
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
public class PowerStorage implements Mechanic<DoubleWrapper> {

    @Override
    public String id() {
        return Mechanics.POWER_STORAGE;
    }

    @Override
    public boolean check(TownContext townContext, DoubleWrapper configContext) {
        // Power storage is always available
        return true;
    }

    @Override
    public void postAction(TownContext townContext, DoubleWrapper configContext) {
        // Register this structure as a battery with the power grid
        PowerService powerService = TownyCivs.injector.getInstance(PowerService.class);
        powerService.registerPowerStorage(townContext.loadedStructure, configContext.value);
    }

    @Override
    public DoubleWrapper getNew() {
        return new DoubleWrapper();
    }

    @Override
    public List<ItemStack> getGuiItems(TownContext townContext, DoubleWrapper configContext) {
        MiniMessage mm = MiniMessage.miniMessage();
        PowerService powerService = TownyCivs.injector.getInstance(PowerService.class);

        double currentStored = powerService.getStoredEnergy(townContext.loadedStructure.town);
        double maxCapacity = powerService.getTotalStorageCapacity(townContext.loadedStructure.town);
        double percentFull = maxCapacity > 0 ? (currentStored / maxCapacity) * 100 : 0;

        ItemStack batteryItem = new ItemStack(Material.REDSTONE_BLOCK);
        batteryItem.editMeta(meta -> {
            meta.displayName(mm.deserialize("<aqua>🔋 Power Storage</aqua>"));
            List<Component> lore = new ArrayList<>();
            lore.add(mm.deserialize("<yellow>Capacity: " + configContext.value + " power</yellow>"));
            lore.add(mm.deserialize("<green>Town Storage: " + String.format("%.1f", currentStored) + " / " + String.format("%.0f", maxCapacity) + " power</green>"));
            lore.add(mm.deserialize("<gray>(" + String.format("%.1f", percentFull) + "% full)</gray>"));
            lore.add(Component.empty());
            lore.add(mm.deserialize("<gray>Charges when generation > consumption</gray>"));
            lore.add(mm.deserialize("<gray>Discharges when consumption > generation</gray>"));
            meta.lore(lore);
        });

        return Collections.singletonList(batteryItem);
    }
}

