package cz.neumimto.towny.townycivs.Listeners;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.TownyMessaging;
import com.palmergames.bukkit.towny.TownySettings;
import com.palmergames.bukkit.towny.TownyUniverse;
import com.palmergames.bukkit.towny.event.PreNewDayEvent;
import com.palmergames.bukkit.towny.event.TownUpkeepCalculationEvent;
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
import cz.neumimto.towny.townycivs.model.LoadedStructure;
import cz.neumimto.towny.townycivs.model.Region;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.Material;
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
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;

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
    public void onTownUpkeepCalculation(TownUpkeepCalculationEvent event){
        Town town = event.getTown();
        if(town.hasUpkeep()){
            Double originalUpkeep = TownySettings.getTownUpkeepCost(town);
            for(LoadedStructure structure: structureService.getAllStructures(town)){
                originalUpkeep += structure.structureDef.townUpkeep;
                event.setUpkeep(originalUpkeep);
            }
        }
    }
    @EventHandler
    public void moveEvent(PlayerMoveEvent event) {
        // Only check if moving to a town block
        if(TownyUniverse.getInstance().hasTownBlock(WorldCoord.parseWorldCoord(event.getTo()))){

            // Get regions once to avoid duplicate calls
            Optional<Region> fromRegion = subclaimService.regionAt(event.getFrom());
            Optional<Region> toRegion = subclaimService.regionAt(event.getTo());

            // Only show title if we're entering a different region
            if (toRegion.isPresent()) {
                // If we weren't in any region before, or we were in a different region
                boolean shouldShowTitle = fromRegion.isEmpty() ||
                    !fromRegion.get().uuid.equals(toRegion.get().uuid);

                if (shouldShowTitle) {
                    // Use MiniMessage for better formatting and customization
                    MiniMessage miniMessage = MiniMessage.miniMessage();
                    Component structureName = miniMessage.deserialize(toRegion.get().loadedStructure.structureDef.name);

                    // Use subtitle instead of main title for smaller text, with shorter display times
                    Title title = Title.title(
                        Component.empty(), // Empty main title
                        structureName, // Structure name as subtitle using MiniMessage
                        Title.Times.times(
                            java.time.Duration.ofMillis(250), // Fade in: 0.25 seconds
                            java.time.Duration.ofMillis(1500), // Stay: 1.5 seconds
                            java.time.Duration.ofMillis(250)   // Fade out: 0.25 seconds
                        )
                    );
                    event.getPlayer().showTitle(title);
                }
            }
        }
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
    public void onItemSwitch(PlayerItemHeldEvent event){
        Player player = event.getPlayer();
        int newSlot = event.getNewSlot();
        ItemStack newItem = player.getInventory().getItem(newSlot);
        ItemService.StructureTool itemType = itemService.getItemType(newItem);

        if (itemType == ItemService.StructureTool.EDIT_TOOL) {
            handleEditToolInteraction(player, player.getLocation());
        }else{
            restoreVisuals(player, player.getLocation());
        }
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
        Resident resident = TownyAPI.getInstance().getResident(player);
        Town resTown = resident.getTownOrNull();
        if (resTown == null) {
            return;
        }

        for(LoadedStructure structure : structureService.getAllStructures(resTown)){
            Set<Location> locations = managementService.prepareVisualBox(player, structure.center, structure.structureDef.area);
            Set<Location> center = Collections.singleton(structure.center);
            managementService.sendBlockChange(player, locations, Material.MAGENTA_STAINED_GLASS);
            managementService.sendBlockChange(player, center, Material.ORANGE_STAINED_GLASS);
        }

        Optional<Region> regionOptional = subclaimService.regionAt(location);
        if (regionOptional.isEmpty()) {
            MiniMessage miniMessage = MiniMessage.miniMessage();
            player.sendMessage(miniMessage.deserialize("<gold>[TownyCivs]</gold> <red>No structure at clicked location.</red>"));
            return;
        }

        WorldCoord worldCoord = WorldCoord.parseWorldCoord(location);

        if (worldCoord.getTownOrNull() != resTown) {
            MiniMessage miniMessage = MiniMessage.miniMessage();
            player.sendMessage(miniMessage.deserialize("<gold>[TownyCivs]</gold> <red>This is not in your town!</red>"));
            return;
        }

        Region region = regionOptional.get();
        regionGui.display(player, region);
    }

    private void restoreVisuals(Player player, Location location) {
        Resident resident = TownyAPI.getInstance().getResident(player);
        Town resTown = resident.getTownOrNull();
        if (resTown == null) {
            return;
        }

        for (LoadedStructure structure : structureService.getAllStructures(resTown)) {
            Set<Location> locations = managementService.prepareVisualBox(player, structure.center, structure.structureDef.area);
            Set<Location> center = Collections.singleton(structure.center);

            // Restore the true blocks instead of AIR
            for (Location loc : locations) {
                Block block = loc.getBlock();
                player.sendBlockChange(loc, block.getBlockData());
            }

            for (Location loc : center) {
                Block block = loc.getBlock();
                player.sendBlockChange(loc, block.getBlockData());
            }
        }
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
                    if (managementService.hasEditSession(player, blueprintItem)) {
                        managementService.endSession(player, location, blueprintItem);
                        EquipmentSlot hand = event.getHand();


                        ItemStack itemInUse = event.getItem();
                        if (itemInUse.getAmount() > 1) {
                            itemInUse.setAmount(itemInUse.getAmount() - 1);
                        } else {
                            itemInUse = null;
                        }
                        player.getInventory().setItem(hand, itemInUse);
                    } else {
                        managementService.startNewEditSession(player, blueprintItem.structure, location, blueprintItem);
                        managementService.moveTo(player, location, blueprintItem);
                    }
                } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                    if (managementService.hasEditSession(player, blueprintItem)) {
                        managementService.moveTo(player, location, blueprintItem);
                    } else {
                        managementService.startNewEditSession(player, blueprintItem.structure, location, blueprintItem);
                        managementService.moveTo(player, location, blueprintItem);
                    }
                }
            }else{
                player.showTitle(Title.title(Component.empty(), Component.text("You can only place structures within your town borders").color(TextColor.color(255, 0, 0))));
                player.playSound(Sound.sound(Key.key("entity.villager.no"), Sound.Source.MASTER, 1.0f, 1.0f));
            }
        }
    }

    @EventHandler
    public void dropEvent(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        Item itemDrop = event.getItemDrop();

        Optional<BlueprintItem> blueprintItem = configurationService.getBlueprintItem(itemDrop.getItemStack());
        blueprintItem.ifPresent(blueprintItem1 -> managementService.endSessionWithoutPlacement(player, blueprintItem.get()));
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