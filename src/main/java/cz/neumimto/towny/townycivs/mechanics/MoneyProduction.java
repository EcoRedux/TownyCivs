package cz.neumimto.towny.townycivs.mechanics;

import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.object.economy.BankAccount;
import cz.neumimto.towny.townycivs.mechanics.common.DoubleWrapper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MoneyProduction implements Mechanic<DoubleWrapper> {
    @Override
    public String id() {
        return Mechanics.MONEY_PRODUCTION;
    }

    @Override
    public boolean check(TownContext townContext, DoubleWrapper configContext) {
        Town town = townContext.town;
        BankAccount account = town.getAccount();
        double amount = configContext.value;

        if (amount <= 0) {
            return false;
        }

        double maxBalance = town.getAccount().getBalanceCap();
        return account.getHoldingBalance() + amount <= maxBalance;
    }

    @Override
    public void postAction(TownContext townContext, DoubleWrapper configContext) {
        townContext.town.getAccount().deposit(configContext.value, "TownyCivs -" + townContext.loadedStructure.structureId + "- money production");
    }

    @Override
    public DoubleWrapper getNew() {
        return new DoubleWrapper();
    }

    @Override
    public List<ItemStack> getGuiItems(TownContext townContext, DoubleWrapper configContext) {
        MiniMessage mm = MiniMessage.miniMessage();

        ItemStack moneyItem = new ItemStack(Material.GOLD_NUGGET);
        moneyItem.editMeta(itemMeta -> {
            itemMeta.displayName(mm.deserialize("<gold>Money Production</gold>"));
            List<Component> lore = new ArrayList<>();
            lore.add(mm.deserialize("<yellow>Produces: <green>$" + configContext.value + "</green></yellow>"));
            lore.add(mm.deserialize("<gray>Deposited into town bank</gray>"));
            itemMeta.lore(lore);
        });

        return Collections.singletonList(moneyItem);
    }
}
