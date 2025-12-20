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
        // Power generation always succeeds if structure is functional
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

        ItemStack powerItem = new ItemStack(Material.GLOWSTONE);
        powerItem.editMeta(meta -> {
            meta.displayName(mm.deserialize("<yellow>⚡ Power Generation</yellow>"));
            List<Component> lore = new ArrayList<>();
            lore.add(mm.deserialize("<green>Produces: " + configContext.value + " power/cycle</green>"));
            meta.lore(lore);
        });

        return Collections.singletonList(powerItem);
    }
}

