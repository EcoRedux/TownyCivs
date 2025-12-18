package cz.neumimto.towny.townycivs.mechanics;

import cz.neumimto.towny.townycivs.mechanics.common.StringWrapper;

class Permission implements Mechanic<StringWrapper> {

    @Override
    public boolean check(TownContext townContext, StringWrapper configContext) {
        // Try player first, then resident
        if (townContext.player != null) {
            return townContext.player.hasPermission(configContext.value);
        }
        if (townContext.resident != null && townContext.resident.getPlayer() != null) {
            return townContext.resident.getPlayer().hasPermission(configContext.value);
        }
        return false;
    }

    @Override
    public void nokmessage(TownContext townContext, StringWrapper configuration) {
        if (townContext.player != null) {
            townContext.player.sendMessage("You don't have permission to use " + townContext.structure.name);
        } else if (townContext.resident != null && townContext.resident.getPlayer() != null) {
            townContext.resident.getPlayer().sendMessage("You don't have permission to use " + townContext.structure.name);
        }
    }

    @Override
    public String id() {
        return Mechanics.PERMISSION;
    }

    @Override
    public StringWrapper getNew() {
        return new StringWrapper();
    }

}
