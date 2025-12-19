package cz.neumimto.towny.townycivs.mechanics;

import cz.neumimto.towny.townycivs.mechanics.common.DoubleWrapper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

class TownUpkeep implements Mechanic<DoubleWrapper> {

    @Override
    public boolean check(TownContext townContext, DoubleWrapper configContext) {
        return townContext.town.getAccount().getHoldingBalance() >= configContext.value;
    }

    @Override
    public void postAction(TownContext townContext, DoubleWrapper configContext) {
        townContext.town.getAccount().withdraw(configContext.value, "townycivs - upkeep " + townContext.structure.id);
    }

    @Override
    public String id() {
        return Mechanics.TOWN_UPKEEP;
    }

    @Override
    public DoubleWrapper getNew() {
        return new DoubleWrapper();
    }

    @Override
    public List<ItemStack> getUpkeepGuiItems(TownContext townContext, DoubleWrapper configContext) {
        MiniMessage mm = MiniMessage.miniMessage();

        ItemStack moneyItem = new ItemStack(Material.PAPER);
        moneyItem.editMeta(itemMeta -> {
            itemMeta.displayName(mm.deserialize("<gold>Money Upkeep</gold>"));
            List<Component> lore = new ArrayList<>();
            lore.add(mm.deserialize("<yellow>Cost: $" + configContext.value + "</yellow>"));
            itemMeta.lore(lore);
        });

        return Collections.singletonList(moneyItem);
    }
}
