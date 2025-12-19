package cz.neumimto.towny.townycivs.mechanics;

import com.palmergames.bukkit.towny.TownyMessaging;
import cz.neumimto.towny.townycivs.mechanics.common.DoubleWrapper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

class Price implements Mechanic<DoubleWrapper> {


    @Override
    public boolean check(TownContext townContext, DoubleWrapper configContext) {
        return townContext.town.getAccount().getHoldingBalance() >= configContext.value;
    }

    @Override
    public void postAction(TownContext townContext, DoubleWrapper configContext) {
        townContext.town.getAccount().withdraw(configContext.value, "TownyCivs - bought " + townContext.structure.id);
    }

    @Override
    public void nokmessage(TownContext townContext, DoubleWrapper configuration) {
        townContext.player.sendMessage(MiniMessage.miniMessage().deserialize("<gold>[TownyCivs]</gold> <red>Town does not have enough balance to pay</red><aqua> " + configuration.value + "</aqua><red>."));
    }

    @Override
    public void okmessage(TownContext townContext, DoubleWrapper configuration) {
        TownyMessaging.sendPrefixedTownMessage(townContext.town, townContext.player.getName() + " bought " + townContext.structure.name);
    }

    @Override
    public String id() {
        return Mechanics.PRICE;
    }

    @Override
    public DoubleWrapper getNew() {
        return new DoubleWrapper();
    }

    @Override
    public List<ItemStack> getUpgradeRequirementGuiItems(TownContext townContext, DoubleWrapper configContext) {
        MiniMessage mm = MiniMessage.miniMessage();
        boolean isMet = check(townContext, configContext);

        ItemStack priceItem = new ItemStack(isMet ? Material.GOLD_INGOT : Material.COAL);
        priceItem.editMeta(meta -> {
            meta.displayName(mm.deserialize(isMet ? "<green>✓ Price</green>" : "<red>✗ Price</red>"));
            List<Component> lore = new ArrayList<>();
            lore.add(mm.deserialize("<yellow>Cost: $" + configContext.value + "</yellow>"));
            double balance = townContext.town.getAccount().getHoldingBalance();
            lore.add(mm.deserialize("<gray>Town Balance: $" + String.format("%.2f", balance) + "</gray>"));
            lore.add(isMet ? mm.deserialize("<green>Requirement met!</green>") : mm.deserialize("<red>Not enough funds!</red>"));
            meta.lore(lore);
        });

        return Collections.singletonList(priceItem);
    }
}
