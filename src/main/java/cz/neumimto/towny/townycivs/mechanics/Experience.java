package cz.neumimto.towny.townycivs.mechanics;

import cz.neumimto.towny.townycivs.mechanics.common.DoubleWrapper;

public class Experience implements Mechanic<DoubleWrapper> {
    @Override
    public boolean check(TownContext townContext, DoubleWrapper configContext) {
        return townContext.player.getExp() >= configContext.value;
    }

    @Override
    public void nokmessage(TownContext townContext, DoubleWrapper configuration) {
        townContext.player.sendMessage("§6[TownyCivs] §cNot enough experience to build §b" + townContext.structure.name + "§c. You need at least §b" + configuration.value + "§c experience.");
    }

    public void postAction(TownContext townContext, DoubleWrapper configContext) {
        float newExp = (float) (townContext.player.getExp() - configContext.value);
        if (newExp < 0) {
            newExp = 0;
        }
        townContext.player.setExp(newExp);
    }

    @Override
    public String id() {
        return Mechanics.EXPERIENCE;
    }

    @Override
    public DoubleWrapper getNew() {
        return new DoubleWrapper();
    }
}
