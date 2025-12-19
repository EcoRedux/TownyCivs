package cz.neumimto.towny.townycivs.mechanics;

import cz.neumimto.towny.townycivs.StructureService;
import cz.neumimto.towny.townycivs.mechanics.common.StringWrapper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

class MStructure implements Mechanic<StringWrapper> {

    @Inject
    private StructureService structureService;

    @Override
    public boolean check(TownContext townContext, StringWrapper configContext) {
        return structureService.getAllStructuresByTown()
                .values()
                .stream()
                .anyMatch(a -> a.stream().anyMatch(l -> l.structureId.contains(configContext.value)));
    }

    @Override
    public void nokmessage(TownContext townContext, StringWrapper configuration) {
        townContext.resident.getPlayer().sendMessage("You dont have permission to use " + townContext.structure.name);
    }

    @Override
    public String id() {
        return Mechanics.STRUCTURE;
    }

    @Override
    public StringWrapper getNew() {
        return new StringWrapper();
    }

    @Override
    public List<ItemStack> getUpgradeRequirementGuiItems(TownContext townContext, StringWrapper configContext) {
        MiniMessage mm = MiniMessage.miniMessage();
        boolean isMet = check(townContext, configContext);

        ItemStack structItem = new ItemStack(isMet ? Material.SMITHING_TABLE : Material.CRAFTING_TABLE);
        structItem.editMeta(meta -> {
            meta.displayName(mm.deserialize(isMet ? "<green>✓ Structure Required</green>" : "<red>✗ Structure Required</red>"));
            List<Component> lore = new ArrayList<>();
            lore.add(mm.deserialize("<yellow>Requires: " + configContext.value + "</yellow>"));
            lore.add(isMet ? mm.deserialize("<green>Structure exists!</green>") : mm.deserialize("<red>Build this structure first!</red>"));
            meta.lore(lore);
        });

        return Collections.singletonList(structItem);
    }

}
