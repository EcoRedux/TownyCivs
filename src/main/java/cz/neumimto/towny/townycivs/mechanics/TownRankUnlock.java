package cz.neumimto.towny.townycivs.mechanics;

import cz.neumimto.towny.townycivs.mechanics.common.DoubleWrapper;

public class TownRankUnlock implements Mechanic<DoubleWrapper> {
    //todo later, specific structures are needed to level up towns
    @Override
    public boolean check(TownContext townContext, DoubleWrapper configContext) {
        return townContext.town.getLevelNumber() >= configContext.value;
    }

    @Override
    public String id() {
        return Mechanics.TOWN_RANK_REQUIREMENT;
    }

    @Override
    public DoubleWrapper getNew() {
        return new DoubleWrapper();
    }
}
