package cz.neumimto.towny.townycivs.Listeners;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.event.PreNewDayEvent;
import com.palmergames.bukkit.towny.event.time.dailytaxes.PreTownPaysNationTaxEvent;
import com.palmergames.bukkit.towny.listeners.TownyPaperEvents;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.object.WorldCoord;
import cz.neumimto.towny.townycivs.*;
import cz.neumimto.towny.townycivs.config.ConfigurationService;
import cz.neumimto.towny.townycivs.config.PluginConfig;
import cz.neumimto.towny.townycivs.config.Structure;
import cz.neumimto.towny.townycivs.gui.BlueprintsGui;
import cz.neumimto.towny.townycivs.gui.RegionGui;
import cz.neumimto.towny.townycivs.model.BlueprintItem;
import cz.neumimto.towny.townycivs.model.Region;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Optional;

@Singleton
public class TownListener implements Listener {

    @Inject
    private SubclaimService subclaimService;

    @Inject
    private StructureService structureService;

    @Inject
    private ItemService itemService;

    @Inject
    private ManagementService managementService;

    @Inject
    private BlueprintsGui blueprintsGui;

    @Inject
    private ConfigurationService configurationService;

    @Inject
    private RegionGui regionGui;

    @Inject
    private TownService townService;

    @EventHandler
    public void login(PlayerLoginEvent event) {

    }

    @EventHandler
    public void newDayEvent(PreNewDayEvent event) {
        if (configurationService.config.processingType == PluginConfig.ProcessingType.UNTIL_NEXT_DAY) {
            townService.resetPlayerActivity();
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onTax(PreTownPaysNationTaxEvent event) {
        //todo
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemService.StructureTool itemType = itemService.getItemType(event.getItem());

        if (player.hasPermission(Permissions.ROLE_TOWN_ADMINISTRATIVE) && itemType == ItemService.StructureTool.TOWN_TOOL) {
            event.setCancelled(true);
            handleTownBlookInteraction(player);
            return;
        }

        if (itemType == ItemService.StructureTool.EDIT_TOOL) {
            event.setCancelled(true);
            handleEditToolInteraction(player, player.getLocation());
            return;
        }

        Optional<BlueprintItem> blueprintItem = configurationService.getBlueprintItem(event.getItem());

        if (player.hasPermission(Permissions.ROLE_ARCHITECT) && blueprintItem.isPresent()) {
            event.setCancelled(true);
            Location location = null;
            if (event.getClickedBlock() == null) {
                location = player.getLocation();
            } else {
                location = event.getClickedBlock().getLocation();
            }

            handleBlueprintPlacement(event, blueprintItem.get(), location);
            return;
        }

    }

    private void handleEditToolInteraction(Player player, Location location) {
        Optional<Region> regionOptional = subclaimService.regionAt(location);
        if (regionOptional.isEmpty()) {
            MiniMessage miniMessage = MiniMessage.miniMessage();
            player.sendMessage(miniMessage.deserialize("<gold>[Townycivs]</gold> <red>No structure at clicked location</red>"));
            return;
        }

        Resident resident = TownyAPI.getInstance().getResident(player);
        Town resTown = resident.getTownOrNull();
        if (resTown == null) {
            return;
        }

        WorldCoord worldCoord = WorldCoord.parseWorldCoord(location);
        if (worldCoord.getTownOrNull() != resTown) {
            MiniMessage miniMessage = MiniMessage.miniMessage();
            player.sendMessage(miniMessage.deserialize("<gold>[Townycivs]</gold> <red>No structure at clicked location</red>"));
            return;
        }

        Region region = regionOptional.get();
        regionGui.display(player, region);
    }

    private void handleTownBlookInteraction(Player player) {
        blueprintsGui.display(player);
    }

    private void handleBlueprintPlacement(PlayerInteractEvent event, BlueprintItem blueprintItem, Location location) {
        Player player = event.getPlayer();
        Resident resident = TownyAPI.getInstance().getResident(player);
        if (resident.hasTown()) {
            WorldCoord worldCoord = WorldCoord.parseWorldCoord(location);
            if (worldCoord.getTownOrNull() == resident.getTownOrNull()) {

                if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
                    if (managementService.hasEditSession(player)) {
                        managementService.endSession(player, location);
                        EquipmentSlot hand = event.getHand();


                        ItemStack itemInUse = event.getItem();
                        if (itemInUse.getAmount() > 1) {
                            itemInUse.setAmount(itemInUse.getAmount() - 1);
                        } else {
                            itemInUse = null;
                        }
                        player.getInventory().setItem(hand, itemInUse);
                    } else {
                        managementService.startNewEditSession(player, blueprintItem.structure, location);
                        managementService.moveTo(player, location);
                    }
                } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                    if (managementService.hasEditSession(player)) {
                        managementService.moveTo(player, location);
                    } else {
                        managementService.startNewEditSession(player, blueprintItem.structure, location);
                        managementService.moveTo(player, location);
                    }
                }
            }
        }
    }

    @EventHandler
    public void dropEvent(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        Item itemDrop = event.getItemDrop();

        Optional<BlueprintItem> blueprintItem = configurationService.getBlueprintItem(itemDrop.getItemStack());
        blueprintItem.ifPresent(blueprintItem1 -> managementService.endSessionWithoutPlacement(player));
    }

    @EventHandler(priority = EventPriority.LOW)
    public void blockBreakEvent(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();

        handleBlockEditingWithinRegion(player, block, event);
    }

    @EventHandler
    public void blockPlaceEvent(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();

        handleBlockEditingWithinRegion(player, block, event);
    }

    private void handleBlockEditingWithinRegion(Player player, Block block, Cancellable event) {
        Resident resident = TownyAPI.getInstance().getResident(player);
        if (resident == null) {
            return;
        }
        Town town = resident.getTownOrNull();
        if (town == null) {
            return;
        }
        WorldCoord worldCoord = WorldCoord.parseWorldCoord(block);
        Town currentTown = worldCoord.getTownOrNull();
        if (town != currentTown) {
            return;
        }

        Optional<Region> structureAt = subclaimService.regionAt(block.getLocation());
        if (structureAt.isPresent()) {
            Region region = structureAt.get();
            if (!managementService.isBeingEdited(region.loadedStructure)) {
                Structure structure = configurationService.findStructureById(region.structureId).get();
                player.sendMessage(Component.text("Editing of " + structure.name + " is not allowed"));
                player.sendMessage(Component.text("If you wish to edit " + structure.name + " craft an editing tool and righclick within this region"));
                event.setCancelled(true);
                return;
            }
        }
    }
}
