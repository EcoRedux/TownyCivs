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
 * Power consumption mechanic - structures require power to function.
 * If power is insufficient, the structure won't produce.
 *
 * Config example:
 * Upkeep: [
 *   {
 *     Mechanic: power_consumption
 *     Value: 25
 *   }
 * ]
 */
public class PowerConsumption implements Mechanic<DoubleWrapper> {

    @Override
    public String id() {
        return Mechanics.POWER_CONSUMPTION;
    }

    @Override
    public boolean check(TownContext townContext, DoubleWrapper configContext) {
        // Check if structure is connected to the power grid
        PowerService powerService = TownyCivs.injector.getInstance(PowerService.class);

        // First check: is the structure physically connected to the grid?
        if (!powerService.isStructureConnectedToPowerGrid(townContext.loadedStructure.uuid, townContext.loadedStructure.town)) {
            return false;
        }

        // Second check: does town have enough power available?
        return powerService.hasSufficientPower(townContext.loadedStructure.town, configContext.value);
    }

    @Override
    public void postAction(TownContext townContext, DoubleWrapper configContext) {
        // Consume power from town grid
        PowerService powerService = TownyCivs.injector.getInstance(PowerService.class);
        powerService.consumePower(townContext.loadedStructure.town, configContext.value);
    }

    @Override
    public void nokmessage(TownContext townContext, DoubleWrapper configuration) {
        if (townContext.player != null) {
            PowerService powerService = TownyCivs.injector.getInstance(PowerService.class);

            // Check if it's a connection issue or power issue
            if (!powerService.isStructureConnectedToPowerGrid(townContext.loadedStructure.uuid, townContext.loadedStructure.town)) {
                townContext.player.sendMessage(MiniMessage.miniMessage().deserialize(
                    "<gold>[TownyCivs]</gold> <red>⚡ Not connected to power grid! Use the power tool to connect this structure to a generator or storage.</red>"
                ));
            } else {
                townContext.player.sendMessage(MiniMessage.miniMessage().deserialize(
                    "<gold>[TownyCivs]</gold> <red>Insufficient power! Structure requires </red><yellow>" +
                    configuration.value + "</yellow><red> power to operate.</red>"
                ));
            }
        }
    }

    @Override
    public DoubleWrapper getNew() {
        return new DoubleWrapper();
    }

    @Override
    public List<ItemStack> getUpkeepGuiItems(TownContext townContext, DoubleWrapper configContext) {
        MiniMessage mm = MiniMessage.miniMessage();

        PowerService powerService = TownyCivs.injector.getInstance(PowerService.class);
        boolean isConnected = powerService.isStructureConnectedToPowerGrid(townContext.loadedStructure.uuid, townContext.loadedStructure.town);
        boolean hasPower = isConnected && powerService.hasSufficientPower(townContext.loadedStructure.town, configContext.value);

        ItemStack powerItem = new ItemStack(hasPower ? Material.REDSTONE_TORCH : Material.SOUL_TORCH);
        powerItem.editMeta(meta -> {
            if (!isConnected) {
                meta.displayName(mm.deserialize("<red>⚡ Power: NOT CONNECTED</red>"));
            } else {
                meta.displayName(mm.deserialize(hasPower ?
                    "<green>⚡ Power: OK</green>" :
                    "<red>⚡ Power: INSUFFICIENT</red>"));
            }

            List<Component> lore = new ArrayList<>();
            lore.add(mm.deserialize("<yellow>Requires: " + configContext.value + " power/cycle</yellow>"));

            if (!isConnected) {
                lore.add(mm.deserialize("<red>Not connected to power grid!</red>"));
                lore.add(mm.deserialize("<gray>Use power tool to connect to generator</gray>"));
            } else {
                double available = powerService.getAvailablePower(townContext.loadedStructure.town);
                lore.add(mm.deserialize("<gray>Available: " + String.format("%.1f", available) + " power</gray>"));

                lore.add(hasPower ?
                    mm.deserialize("<green>Power requirement met!</green>") :
                    mm.deserialize("<red>Build more generators!</red>"));
            }
            meta.lore(lore);
        });

        return Collections.singletonList(powerItem);
    }
}
