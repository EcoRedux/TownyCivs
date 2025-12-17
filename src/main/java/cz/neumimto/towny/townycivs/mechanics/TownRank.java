package cz.neumimto.towny.townycivs.mechanics;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.TownyUniverse;
import cz.neumimto.towny.townycivs.mechanics.common.DoubleWrapper;
import net.kyori.adventure.text.minimessage.MiniMessage;

class TownRank implements Mechanic<DoubleWrapper> {

    @Override
    public boolean check(TownContext townContext, DoubleWrapper configContext) {
        return townContext.town.getLevelNumber() >= configContext.value;
    }

    @Override
    public void nokmessage(TownContext townContext, DoubleWrapper configuration) {
        townContext.player.sendMessage(MiniMessage.miniMessage().deserialize("<gold>[TownyCivs]</gold> <red>Town Level is not high enough to build</red><aqua> " + townContext.structure.name + "</aqua><red>."));
    }

    @Override
    public String id() {
        return Mechanics.TOWN_RANK;
    }

    @Override
    public DoubleWrapper getNew() {
        return new DoubleWrapper();
    }
}
