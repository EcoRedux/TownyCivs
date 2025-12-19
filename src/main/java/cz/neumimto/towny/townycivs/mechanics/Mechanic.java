package cz.neumimto.towny.townycivs.mechanics;

import org.bukkit.inventory.ItemStack;

import java.util.Collections;
import java.util.List;

public interface Mechanic<C> {

    boolean check(TownContext townContext, C configContext);

    default void postAction(TownContext townContext, C configContext) {
    }

    default void nokmessage(TownContext townContext, C configuration) {
    }

    default void okmessage(TownContext townContext, C configuration) {
    }

    String id();

    C getNew();

    /**
     * Get GUI representation items for this mechanic in the structure GUI.
     * Return a list of ItemStacks that should be displayed in the GUI.
     * Each mechanic can customize how it appears in the GUI.
     *
     * @param townContext  The town context
     * @param configContext The mechanic's configuration
     * @return List of ItemStacks to display in the GUI (empty list if no GUI representation)
     */
    default List<ItemStack> getGuiItems(TownContext townContext, C configContext) {
        return Collections.emptyList();
    }

    /**
     * Get GUI representation items for this mechanic in the upkeep GUI.
     * Similar to getGuiItems but specifically for upkeep requirements.
     *
     * @param townContext  The town context
     * @param configContext The mechanic's configuration
     * @return List of ItemStacks to display in the upkeep GUI (empty list if no GUI representation)
     */
    default List<ItemStack> getUpkeepGuiItems(TownContext townContext, C configContext) {
        return Collections.emptyList();
    }

    /**
     * Get GUI representation items for this mechanic in the upgrade requirements GUI.
     * Shows if the requirement is met with appropriate visual indicators.
     *
     * @param townContext  The town context
     * @param configContext The mechanic's configuration
     * @return List of ItemStacks to display in the upgrade requirements GUI (empty list if no GUI representation)
     */
    default List<ItemStack> getUpgradeRequirementGuiItems(TownContext townContext, C configContext) {
        return Collections.emptyList();
    }
}
