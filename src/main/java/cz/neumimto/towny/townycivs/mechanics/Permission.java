package cz.neumimto.towny.townycivs.mechanics;

import cz.neumimto.towny.townycivs.mechanics.common.StringWrapper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

    @Override
    public List<ItemStack> getUpgradeRequirementGuiItems(TownContext townContext, StringWrapper configContext) {
        MiniMessage mm = MiniMessage.miniMessage();
        boolean isMet = check(townContext, configContext);

        ItemStack permItem = new ItemStack(isMet ? Material.NAME_TAG : Material.BARRIER);
        permItem.editMeta(meta -> {
            meta.displayName(mm.deserialize(isMet ? "<green>✓ Permission</green>" : "<red>✗ Permission</red>"));
            List<Component> lore = new ArrayList<>();
            lore.add(mm.deserialize("<yellow>Required: " + configContext.value + "</yellow>"));
            lore.add(isMet ? mm.deserialize("<green>You have permission!</green>") : mm.deserialize("<red>Missing permission!</red>"));
            meta.lore(lore);
        });

        return Collections.singletonList(permItem);
    }

}
