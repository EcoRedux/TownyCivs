package cz.neumimto.towny.townycivs.mechanics;

import cz.neumimto.towny.townycivs.mechanics.common.DoubleWrapper;
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
 * Power generation mechanic - structures produce power for the town grid.
 *
 * Config example:
 * Production: [
 *   {
 *     Mechanic: power_generation
 *     Value: 50
 *   }
 * ]
 */
public class PowerGeneration implements Mechanic<DoubleWrapper> {

    @Override
    public String id() {
        return Mechanics.POWER_GENERATION;
    }

    @Override
    public boolean check(TownContext townContext, DoubleWrapper configContext) {
        PowerService powerService = TownyCivs.injector.getInstance(PowerService.class);

        // 2. Get the town's power grid
        PowerGrid grid = powerService.getPowerGrid(townContext.loadedStructure.town);

        // 3. Check if this specific structure has any connections (lines)
        // If the set of connected UUIDs is empty, it's isolated.
        if (grid == null || grid.getConnections(townContext.loadedStructure.uuid).isEmpty()) {
            return false;
        }
        return true;
    }

    @Override
    public void postAction(TownContext townContext, DoubleWrapper configContext) {
        // Add power to town grid
        PowerService powerService = TownyCivs.injector.getInstance(PowerService.class);
        powerService.addPowerGeneration(townContext.loadedStructure, configContext.value);
    }

    @Override
    public DoubleWrapper getNew() {
        return new DoubleWrapper();
    }

    @Override
    public List<ItemStack> getGuiItems(TownContext townContext, DoubleWrapper configContext) {
        MiniMessage mm = MiniMessage.miniMessage();

        // Check connection status for GUI tooltip
        PowerService powerService = TownyCivs.injector.getInstance(PowerService.class);
        PowerGrid grid = powerService.getPowerGrid(townContext.loadedStructure.town);
        boolean isConnected = grid != null && !grid.getConnections(townContext.loadedStructure.uuid).isEmpty();

        ItemStack powerItem = new ItemStack(isConnected ? Material.GLOWSTONE : Material.RED_STAINED_GLASS);
        powerItem.editMeta(meta -> {
            meta.displayName(mm.deserialize(isConnected ? "<yellow>⚡ Power Generation</yellow>" : "<red>⚡ Power Generation (Disconnected)</red>"));
            List<Component> lore = new ArrayList<>();
            lore.add(mm.deserialize("<green>Produces: " + configContext.value + " power/cycle</green>"));

            if (!isConnected) {
                lore.add(Component.empty());
                lore.add(mm.deserialize("<red>⚠ Not connected to grid!</red>"));
                lore.add(mm.deserialize("<gray>Power is being wasted.</gray>"));
            }

            meta.lore(lore);
        });

        return Collections.singletonList(powerItem);
    }
}
