package cz.neumimto.towny.townycivs.mechanics;

import cz.neumimto.towny.townycivs.StructureInventoryService;
import cz.neumimto.towny.townycivs.TownyCivs;
import cz.neumimto.towny.townycivs.mechanics.common.ItemList;

public class ItemUpkeep implements Mechanic<ItemList> {

    @Override
    public boolean check(TownContext townContext, ItemList configContext) {
        return TownyCivs.injector.getInstance(StructureInventoryService.class)
                .checkUpkeep(townContext, configContext);
    }

    @Override
    public void postAction(TownContext townContext, ItemList configContext) {
        TownyCivs.injector.getInstance(StructureInventoryService.class)
                .processUpkeep(townContext.loadedStructure, configContext);
    }

    @Override
    public String id() {
        return Mechanics.UPKEEP;
    }


    @Override
    public ItemList getNew() {
        return new ItemList();
    }
}
