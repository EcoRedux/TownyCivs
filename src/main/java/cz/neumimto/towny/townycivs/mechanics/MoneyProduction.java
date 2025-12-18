package cz.neumimto.towny.townycivs.mechanics;

import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.object.economy.BankAccount;
import cz.neumimto.towny.townycivs.mechanics.common.DoubleWrapper;

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

}
