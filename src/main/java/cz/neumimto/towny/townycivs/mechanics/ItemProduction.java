package cz.neumimto.towny.townycivs.mechanics;

import cz.neumimto.towny.townycivs.StructureInventoryService;
import cz.neumimto.towny.townycivs.TownyCivs;
import cz.neumimto.towny.townycivs.mechanics.common.ItemList;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ItemProduction implements Mechanic<ItemList> {


    @Override
    public String id() {
        return Mechanics.ITEM_PRODUCTION;
    }

    @Override
    public boolean check(TownContext townContext, ItemList configContext) {
        Set<ItemStack> itemStackSet = new HashSet<>();
        for (ItemList.ConfigItem configItem : configContext.configItems) {
            ItemStack itemStack = configItem.toItemStack();
            itemStackSet.add(itemStack);
        }
        //todo TownyCivs.injector.getInstance(StructureInventoryService.class).canTakeProducedItems(townContext.loadedStructure, itemStackSet);
        return true;
    }

    @Override
    public void postAction(TownContext townContext, ItemList configContext) {

        Set<ItemStack> itemStackSet = new HashSet<>();
        for (ItemList.ConfigItem configItem : configContext.configItems) {
            ItemStack itemStack = configItem.toItemStack();
            itemStackSet.add(itemStack);
        }

        TownyCivs.injector.getInstance(StructureInventoryService.class).addItemProduction(townContext.loadedStructure, itemStackSet);
    }

    @Override
    public ItemList getNew() {
        return new ItemList();
    }

    @Override
    public List<ItemStack> getGuiItems(TownContext townContext, ItemList configContext) {
        List<ItemStack> guiItems = new ArrayList<>();
        MiniMessage mm = MiniMessage.miniMessage();

        for (ItemList.ConfigItem configItem : configContext.configItems) {
            ItemStack itemStack = configItem.toItemStack();
            itemStack.editMeta(itemMeta -> {
                List<Component> lore = new ArrayList<>();
                lore.add(mm.deserialize("<yellow>Produced Item</yellow>"));
                if (itemMeta.hasLore() && itemMeta.lore() != null) {
                    lore.addAll(itemMeta.lore());
                }
                itemMeta.lore(lore);
            });
            guiItems.add(itemStack);
        }

        return guiItems;
    }
}