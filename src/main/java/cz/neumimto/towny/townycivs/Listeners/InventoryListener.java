package cz.neumimto.towny.townycivs.Listeners;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.TownyUniverse;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.object.TownBlock;
import com.palmergames.bukkit.towny.object.WorldCoord;
import cz.neumimto.towny.townycivs.ItemService;
import cz.neumimto.towny.townycivs.StructureInventoryService;
import cz.neumimto.towny.townycivs.StructureService;
import cz.neumimto.towny.townycivs.SubclaimService;
import cz.neumimto.towny.townycivs.model.LoadedStructure;
import cz.neumimto.towny.townycivs.tutorial.TutorialManager;
import cz.neumimto.towny.townycivs.tutorial.TutorialStep;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Optional;
import java.util.Set;

@Singleton
public class InventoryListener implements Listener {

    @Inject
    private StructureInventoryService sis;

    @Inject
    private StructureService ss;

    @Inject
    private SubclaimService sus;

    @Inject
    private ItemService itemService;

    @Inject
    private TutorialManager tutorialManager;

    // Materials that count as "tools" for tutorial step
    private static final Set<Material> HOE_MATERIALS = Set.of(
        Material.WOODEN_HOE, Material.STONE_HOE, Material.IRON_HOE,
        Material.GOLDEN_HOE, Material.DIAMOND_HOE, Material.NETHERITE_HOE
    );

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player p) {
            // Only close if the inventory being closed is the structure inventory
            StructureInventoryService.StructAndInv structAndInv = sis.getPlayerStructAndInv(p.getUniqueId());
            if (structAndInv != null && structAndInv.inventory().equals(event.getInventory())) {
                sis.closeInventory(p);
            }
        }
    }

    @EventHandler
    public void onInventoryOpen(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (event.getClickedBlock() != null
                && (event.getClickedBlock().getType() == Material.CHEST
                || event.getClickedBlock().getType() == Material.BARREL
                || event.getClickedBlock().getType() == Material.TRAPPED_CHEST
        )) {
            Location location = event.getClickedBlock().getLocation();

            Resident resident = TownyUniverse.getInstance().getResident(player.getUniqueId());
            if (resident.getTownOrNull() == null) {
                return;
            }

            TownBlock tb = TownyUniverse.getInstance().getTownBlockOrNull(WorldCoord.parseWorldCoord(location));
            if (tb == null) {
                return;
            }

            if (tb.getTownOrNull() != resident.getTownOrNull()) {
                return;
            }

            Optional<LoadedStructure> structureAt = sus.getStructureAt(location);
            if (structureAt.isEmpty()) {
                return;
            }

            LoadedStructure structure = structureAt.get();
            sis.openInventory(player, location, structure);
        }
    }

    @EventHandler
    public void onItemClick(InventoryClickEvent event) {
        ItemStack currentItem = event.getCurrentItem();
        if (currentItem == null) {
            return;
        }
        if (itemService.isInventoryBlocker(currentItem)) {
            event.setCancelled(true);
            return;
        }

        // Tutorial tracking for structure inventories
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        Resident resident = TownyAPI.getInstance().getResident(player);
        if (resident == null || !resident.hasTown() || !resident.isMayor()) {
            return;
        }

        Town town = resident.getTownOrNull();
        if (town == null || !tutorialManager.isTutorialActive(town)) {
            return;
        }

        TutorialStep step = tutorialManager.getTutorialStep(town);

        // Check if player is interacting with a structure inventory
        StructureInventoryService.StructAndInv structAndInv = sis.getPlayerStructAndInv(player.getUniqueId());

        if (structAndInv == null) {
            return;
        }

        // Step 12: Check if adding tools (hoe or bone meal) to structure
        if (step == TutorialStep.ADD_TOOLS_TO_STRUCTURE) {
            InventoryAction action = event.getAction();
            ItemStack cursor = event.getCursor();

            // Check if placing item into structure inventory (cursor has the item being placed)
            if (action == InventoryAction.PLACE_ALL || action == InventoryAction.PLACE_ONE ||
                action == InventoryAction.PLACE_SOME || action == InventoryAction.SWAP_WITH_CURSOR) {

                if (cursor != null && (HOE_MATERIALS.contains(cursor.getType()) || cursor.getType() == Material.BONE_MEAL)) {
                    // Player is adding a hoe or bone meal - advance tutorial after the click completes
                    cz.neumimto.towny.townycivs.TownyCivs.MORE_PAPER_LIB.scheduling().entitySpecificScheduler(player)
                        .runDelayed(() -> tutorialManager.onToolsAddedToStructure(town, player), null, 1L);
                    return;
                }
            }

            // Also check if using shift-click to move items from player inventory to structure
            if (action == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
                // When shift-clicking, currentItem is the item being moved
                if (event.getRawSlot() >= event.getView().getTopInventory().getSize()) {
                    // Clicking in bottom inventory (player inv) to move to top (structure)
                    if (HOE_MATERIALS.contains(currentItem.getType()) || currentItem.getType() == Material.BONE_MEAL) {
                        cz.neumimto.towny.townycivs.TownyCivs.MORE_PAPER_LIB.scheduling().entitySpecificScheduler(player)
                            .runDelayed(() -> tutorialManager.onToolsAddedToStructure(town, player), null, 1L);
                        return;
                    }
                }
            }

            // If player is collecting wheat while on step 12, they might have already added tools before
            // Allow them to skip to collecting wheat
            if (event.getRawSlot() < event.getView().getTopInventory().getSize()) {
                if (action == InventoryAction.PICKUP_ALL || action == InventoryAction.PICKUP_HALF ||
                    action == InventoryAction.PICKUP_ONE || action == InventoryAction.PICKUP_SOME ||
                    action == InventoryAction.MOVE_TO_OTHER_INVENTORY) {

                    if (currentItem.getType() == Material.WHEAT ||
                        currentItem.getType() == Material.WHEAT_SEEDS ||
                        currentItem.getType() == Material.BREAD) {
                        // Player already has production, skip step 12 and complete step 13
                        cz.neumimto.towny.townycivs.TownyCivs.MORE_PAPER_LIB.scheduling().entitySpecificScheduler(player)
                            .runDelayed(() -> {
                                tutorialManager.onToolsAddedToStructure(town, player); // Complete step 12
                                tutorialManager.onProductionCollected(town, player);   // Complete step 13
                            }, null, 1L);
                        return;
                    }
                }
            }
        }

        // Step 13: Check if collecting production (wheat) from structure
        if (step == TutorialStep.COLLECT_PRODUCTION) {
            InventoryAction action = event.getAction();

            // Check if taking item from structure inventory (top inventory)
            if (event.getRawSlot() < event.getView().getTopInventory().getSize()) {
                if (action == InventoryAction.PICKUP_ALL || action == InventoryAction.PICKUP_HALF ||
                    action == InventoryAction.PICKUP_ONE || action == InventoryAction.PICKUP_SOME ||
                    action == InventoryAction.MOVE_TO_OTHER_INVENTORY) {

                    // Check for wheat or any crop production
                    if (currentItem.getType() == Material.WHEAT ||
                        currentItem.getType() == Material.WHEAT_SEEDS ||
                        currentItem.getType() == Material.BREAD) {
                        // Player is collecting wheat - advance tutorial after the click completes
                        cz.neumimto.towny.townycivs.TownyCivs.MORE_PAPER_LIB.scheduling().entitySpecificScheduler(player)
                            .runDelayed(() -> tutorialManager.onProductionCollected(town, player), null, 1L);
                    }
                }
            }
        }
    }
}
