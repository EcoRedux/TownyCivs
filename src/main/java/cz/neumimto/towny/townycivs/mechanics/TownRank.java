package cz.neumimto.towny.townycivs.mechanics;

import cz.neumimto.towny.townycivs.mechanics.common.DoubleWrapper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

    @Override
    public List<ItemStack> getUpgradeRequirementGuiItems(TownContext townContext, DoubleWrapper configContext) {
        MiniMessage mm = MiniMessage.miniMessage();
        boolean isMet = check(townContext, configContext);

        ItemStack levelItem = new ItemStack(isMet ? Material.BELL : Material.IRON_BARS);
        levelItem.editMeta(meta -> {
            meta.displayName(mm.deserialize(isMet ? "<green>✓ Town Level</green>" : "<red>✗ Town Level</red>"));
            List<Component> lore = new ArrayList<>();
            lore.add(mm.deserialize("<yellow>Required Level: " + (int) configContext.value + "</yellow>"));
            lore.add(mm.deserialize("<gray>Current Level: " + townContext.town.getLevelNumber() + "</gray>"));
            lore.add(isMet ? mm.deserialize("<green>Requirement met!</green>") : mm.deserialize("<red>Town level too low!</red>"));
            meta.lore(lore);
        });

        return Collections.singletonList(levelItem);
    }
}
