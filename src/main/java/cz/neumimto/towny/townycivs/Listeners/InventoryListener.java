package cz.neumimto.towny.townycivs.Listeners;

import com.palmergames.bukkit.towny.TownyUniverse;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.TownBlock;
import com.palmergames.bukkit.towny.object.WorldCoord;
import cz.neumimto.towny.townycivs.ItemService;
import cz.neumimto.towny.townycivs.StructureInventoryService;
import cz.neumimto.towny.townycivs.StructureService;
import cz.neumimto.towny.townycivs.SubclaimService;
import cz.neumimto.towny.townycivs.model.LoadedStructure;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Optional;

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

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player p) {
            sis.closeInvenotory(p);
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
        }
    }
}
