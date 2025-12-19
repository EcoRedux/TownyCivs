package cz.neumimto.towny.townycivs.mechanics;

import cz.neumimto.towny.townycivs.TownyCivs;
import cz.neumimto.towny.townycivs.mechanics.common.DoubleWrapper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Administration production mechanic.
 * When a structure has this in its Production, it provides administration services to the town.
 * The value represents the "administration level" provided (1 = Settlement, 2 = Hamlet, etc.)
 *
 * The structure must have its upkeep satisfied for the administration to be active.
 * If upkeep fails, the town loses access to invite/claim features until upkeep is restored.
 *
 * Usage in config:
 * Production: [
 *   {
 *     Mechanic: administration
 *     Value: 1
 *   }
 * ]
 */
public class Administration implements Mechanic<DoubleWrapper> {

    @Override
    public String id() {
        return Mechanics.ADMINISTRATION;
    }

    @Override
    public boolean check(TownContext townContext, DoubleWrapper configContext) {
        // Administration production is always "ready" to produce
        // The actual activation depends on upkeep being satisfied
        return true;
    }

    @Override
    public void postAction(TownContext townContext, DoubleWrapper configContext) {
        // When production runs successfully (upkeep was satisfied),
        // mark this structure as providing active administration
        int adminLevel = (int) configContext.value;

        TownyCivs.injector.getInstance(AdministrationService.class)
            .setAdministrationActive(townContext.town, townContext.loadedStructure, adminLevel, true);
    }

    @Override
    public DoubleWrapper getNew() {
        return new DoubleWrapper();
    }

    @Override
    public List<ItemStack> getGuiItems(TownContext townContext, DoubleWrapper configContext) {
        MiniMessage mm = MiniMessage.miniMessage();
        int adminLevel = (int) configContext.value;

        AdministrationService adminService = TownyCivs.injector.getInstance(AdministrationService.class);
        boolean isActive = adminService.hasActiveAdministration(townContext.town, adminLevel);

        ItemStack adminItem = new ItemStack(isActive ? Material.BEACON : Material.GRAY_STAINED_GLASS);
        adminItem.editMeta(itemMeta -> {
            if (isActive) {
                itemMeta.displayName(mm.deserialize("<green>Town Hall - Active</green>"));
            } else {
                itemMeta.displayName(mm.deserialize("<red>Town Hall - Inactive</red>"));
            }

            List<Component> lore = new ArrayList<>();
            lore.add(mm.deserialize("<yellow>Administration Level: <white>" + adminLevel + "</white></yellow>"));
            lore.add(Component.empty());

            if (isActive) {
                lore.add(mm.deserialize("<green>✓ Town Hall is operational</green>"));
                lore.add(mm.deserialize("<gray>• Can invite residents</gray>"));
                lore.add(mm.deserialize("<gray>• Can claim land</gray>"));
            } else {
                lore.add(mm.deserialize("<red>✗ Town Hall is inactive!</red>"));
                lore.add(mm.deserialize("<gray>Supply upkeep items to reactivate</gray>"));
            }

            itemMeta.lore(lore);
        });

        return Collections.singletonList(adminItem);
    }
}
