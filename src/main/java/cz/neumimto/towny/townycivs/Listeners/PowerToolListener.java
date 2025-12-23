
package cz.neumimto.towny.townycivs.Listeners;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import cz.neumimto.towny.townycivs.ItemService;
import cz.neumimto.towny.townycivs.SubclaimService;
import cz.neumimto.towny.townycivs.model.LoadedStructure;
import cz.neumimto.towny.townycivs.model.Region;
import cz.neumimto.towny.townycivs.power.PowerService;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Optional;

/**
 * Handles Power Tool interactions for connecting power lines between power structures.
 */
@Singleton
public class PowerToolListener implements Listener {

    @Inject
    private ItemService itemService;

    @Inject
    private PowerService powerService;

    @Inject
    private SubclaimService subclaimService;

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack itemInUse = player.getInventory().getItemInMainHand();
        MiniMessage mm = MiniMessage.miniMessage();

        // Check if using power tool
        ItemService.StructureTool toolType = itemService.getItemType(itemInUse);
        if (toolType != ItemService.StructureTool.POWER_TOOL) {
            return;
        }

        // Only handle right clicks
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_AIR) {
            return;
        }

        event.setCancelled(true);

        // Shift + right click cancels pending connection
        if (player.isSneaking()) {
            powerService.cancelPowerLineConnection(player);
            return;
        }

        // Must click on a block
        if (event.getClickedBlock() == null) {
            if (powerService.hasPendingConnection(player)) {
                player.sendMessage(mm.deserialize("<gold>[TownyCivs]</gold> <yellow>You have a pending power line connection. Right-click another power structure or Shift+Right-click to cancel.</yellow>"));
            } else {
                player.sendMessage(mm.deserialize("<gold>[TownyCivs]</gold> <red>Right-click on a power structure to start a connection.</red>"));
            }
            return;
        }

        // Check if player is in a town
        Resident resident = TownyAPI.getInstance().getResident(player);
        if (resident == null || !resident.hasTown()) {
            player.sendMessage("§6[TownyCivs] §cYou must be in a town to use the power tool.");
            return;
        }

        Town town = resident.getTownOrNull();
        if (town == null) {
            return;
        }

        // Find if there's a power structure at the clicked location
        Location clickedLocation = event.getClickedBlock().getLocation();
        Optional<LoadedStructure> structureOpt = subclaimService.getStructureAt(clickedLocation);

        if (structureOpt.isEmpty()) {
            player.sendMessage("§6[TownyCivs] §cNo structure found at this location.");
            return;
        }

        LoadedStructure structure = structureOpt.get();

        // Check if this is a power-capable structure
        if (!powerService.isPowerConnector(structure)) {
            player.sendMessage(mm.deserialize("<gold>[TownyCivs]</gold> <red>This structure doesn't have power capabilities or is missing a lightning rod.</red>"));
            return;
        }

        // Check if structure belongs to player's town
        if (!structure.town.equals(town.getUUID())) {
            player.sendMessage(mm.deserialize("<gold>[TownyCivs]</gold> <red>This structure belongs to a different town.</red>"));
            return;
        }

        // Check if clicked block is any type of lightning rod
        String blockName = event.getClickedBlock().getType().name();
        if (!blockName.contains("LIGHTNING_ROD")) {
            player.sendMessage(mm.deserialize("<gold>[TownyCivs]</gold> <red>You must click on a lightning rod to connect power lines.</red>"));
            return;
        }



        // Handle power line connection
        if (powerService.hasPendingConnection(player)) {
            // Complete the connection
            PowerService.PowerLineResult result = powerService.completePowerLineConnection(player, structure, clickedLocation);

            switch (result) {
                case SUCCESS:
                    player.sendMessage(mm.deserialize("<gold>[TownyCivs]</gold> <green>⚡ Power line connected! <yellow>" + structure.structureDef.name + "</yellow> is now connected to the power grid.</green>"));
                    break;
                case SAME_STRUCTURE:
                    player.sendMessage(mm.deserialize("<gold>[TownyCivs]</gold> <red>Cannot connect a structure to itself.</red>"));
                    break;
                case DIFFERENT_TOWNS:
                    player.sendMessage(mm.deserialize("<gold>[TownyCivs]</gold> <red>Cannot connect structures from different towns.</red>"));
                    break;
                case DISCONNECTED: // <--- Handle the new result
                    player.sendMessage(mm.deserialize("<gold>[TownyCivs]</gold> <yellow>⚡ Power line disconnected.</yellow>"));
                    player.playSound(player.getLocation(), Sound.ITEM_LEAD_BREAK, 1.0f, 1.0f);
                    break;
                case TOO_FAR:
                    player.sendMessage(mm.deserialize("<gold>[TownyCivs]</gold> <red>Structures are too far apart. Maximum distance is 50 blocks.</red>"));
                    break;
                case ALREADY_CONNECTED:
                    player.sendMessage(mm.deserialize("<gold>[TownyCivs]</gold> <red>These structures are already connected.</red>"));
                    break;
                case ALREADY_HAS_CONNECTION:
                    player.sendMessage(mm.deserialize("<gold>[TownyCivs]</gold> <red>⚡ Connection failed! Generators and consumers can only connect to ONE structure.</red>"));
                    break;
                case INCOMPATIBLE_STRUCTURES:
                    player.sendMessage(mm.deserialize("<gold>[TownyCivs]</gold> <red>⚡ Incompatible connection! Generators and consumers must connect through a utility pole.</red>"));
                    break;
                case NOT_A_CONNECTOR:
                    player.sendMessage(mm.deserialize("<gold>[TownyCivs]</gold> <red>This structure cannot be connected to power lines.</red>"));
                    break;
                case INSUFFICIENT_RESOURCES: // <--- Handle the new result
                    // We can't calculate the exact missing amount here easily without recalculating distance,
                    // so we give a generic helpful message.
                    player.sendMessage(mm.deserialize("<gold>[TownyCivs]</gold> <red>Not enough materials! You need <yellow>1 Copper Ingot</yellow> per block of distance.</red>"));
                    break;
                default:
                    player.sendMessage(mm.deserialize("<gold>[TownyCivs]</gold> <red>Failed to connect power line.</red>"));
                    break;
            }
        } else {
            // Start a new connection
            powerService.startPowerLineConnection(player, structure, clickedLocation);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Cancel any pending connections when player leaves
        powerService.cancelPowerLineConnection(event.getPlayer());
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        // Check if a lightning rod was broken
        if (event.getBlock().getType().name().contains("LIGHTNING_ROD")) {
            // Handle potential powerline invalidation
            powerService.handleLightningRodChange(event.getBlock().getLocation());
        }
    }
}

