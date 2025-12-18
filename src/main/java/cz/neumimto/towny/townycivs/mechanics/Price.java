package cz.neumimto.towny.townycivs.mechanics;

import com.palmergames.bukkit.towny.TownyMessaging;
import cz.neumimto.towny.townycivs.mechanics.common.DoubleWrapper;
import net.kyori.adventure.text.minimessage.MiniMessage;

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
}
