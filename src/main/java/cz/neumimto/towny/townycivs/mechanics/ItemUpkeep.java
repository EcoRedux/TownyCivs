package cz.neumimto.towny.townycivs.mechanics;

import cz.neumimto.towny.townycivs.StructureInventoryService;
import cz.neumimto.towny.townycivs.TownyCivs;
import cz.neumimto.towny.townycivs.mechanics.common.ItemList;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BundleMeta;

import java.util.ArrayList;
import java.util.List;

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

    @Override
    public List<ItemStack> getUpkeepGuiItems(TownContext townContext, ItemList configContext) {
        List<ItemStack> guiItems = new ArrayList<>();
        MiniMessage mm = MiniMessage.miniMessage();

        boolean requireAll = configContext.requireAll != null ? configContext.requireAll : true;

        if (!requireAll) {
            // Create bundle showing alternative options (ANY ONE)
            ItemStack bundleStack = new ItemStack(Material.BUNDLE);
            bundleStack.editMeta(itemMeta -> {
                BundleMeta bundleMeta = (BundleMeta) itemMeta;
                bundleMeta.displayName(mm.deserialize("<aqua>Alternative Options (ANY ONE)</aqua>"));
                List<Component> lore = new ArrayList<>();
                lore.add(mm.deserialize("<gray>Any one of these items works:</gray>"));

                // Add each item to the bundle
                for (ItemList.ConfigItem configItem : configContext.configItems) {
                    ItemStack itemStack = configItem.toItemStack();
                    bundleMeta.addItem(itemStack);

                    // Add info to lore
                    String itemName = itemStack.getType().name();
                    if (configItem.consumeItem != null && configItem.consumeItem) {
                        int amount = configItem.consumeAmount != null ? configItem.consumeAmount : 1;
                        lore.add(mm.deserialize("<white>• " + itemName + " <red>(Consumed: " + amount + ")</red></white>"));
                    } else if (configItem.damageAmount != null) {
                        lore.add(mm.deserialize("<white>• " + itemName + " <gold>(Damage: " + configItem.damageAmount + ")</gold></white>"));
                    } else {
                        lore.add(mm.deserialize("<white>• " + itemName + "</white>"));
                    }
                }
                bundleMeta.lore(lore);
            });
            guiItems.add(bundleStack);
        } else {
            // Require all items - show each individually
            for (ItemList.ConfigItem configItem : configContext.configItems) {
                ItemStack itemStack = configItem.toItemStack();
                itemStack.editMeta(itemMeta -> {
                    List<Component> lore = new ArrayList<>();
                    lore.add(mm.deserialize("<yellow>Required Item</yellow>"));
                    if (configItem.consumeItem != null && configItem.consumeItem) {
                        lore.add(mm.deserialize("<red>Consumed: " + (configItem.consumeAmount != null ? configItem.consumeAmount : 1) + "x</red>"));
                    }
                    if (configItem.damageAmount != null) {
                        lore.add(mm.deserialize("<gold>Damage: " + configItem.damageAmount + "</gold>"));
                    }
                    itemMeta.lore(lore);
                });
                guiItems.add(itemStack);
            }
        }

        return guiItems;
    }
}
