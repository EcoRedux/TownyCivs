package cz.neumimto.towny.townycivs.mechanics;

import cz.neumimto.towny.townycivs.mechanics.common.DoubleWrapper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Experience implements Mechanic<DoubleWrapper> {
    @Override
    public boolean check(TownContext townContext, DoubleWrapper configContext) {
        return townContext.player.getTotalExperience() >= configContext.value;
    }

    @Override
    public void nokmessage(TownContext townContext, DoubleWrapper configuration) {
        townContext.player.sendMessage("§6[TownyCivs] §cNot enough experience for" + townContext.structure.name + "§c. You need at least §b" + configuration.value + "§c experience.");
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

    @Override
    public List<ItemStack> getUpgradeRequirementGuiItems(TownContext townContext, DoubleWrapper configContext) {
        MiniMessage mm = MiniMessage.miniMessage();
        boolean isMet = check(townContext, configContext);

        ItemStack xpItem = new ItemStack(isMet ? Material.EXPERIENCE_BOTTLE : Material.POTION);
        xpItem.editMeta(meta -> {
            meta.displayName(mm.deserialize(isMet ? "<green>✓ Experience Required</green>" : "<red>✗ Experience Required</red>"));
            List<Component> lore = new ArrayList<>();
            lore.add(mm.deserialize("<yellow>Requires: " + configContext.value + " XP</yellow>"));
            lore.add(mm.deserialize("<gray>Current: " + townContext.player.getTotalExperience() + " XP</gray>"));
            lore.add(isMet ? mm.deserialize("<green>Enough Experience!</green>") : mm.deserialize("<red>Get more Experience first!</red>"));
            meta.lore(lore);
        });

        return Collections.singletonList(xpItem);
    }
}
