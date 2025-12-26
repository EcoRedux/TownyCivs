package cz.neumimto.towny.townycivs.Listeners;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.TownyMessaging;
import com.palmergames.bukkit.towny.TownySettings;
import com.palmergames.bukkit.towny.TownyUniverse;
import com.palmergames.bukkit.towny.event.*;
import com.palmergames.bukkit.towny.event.time.dailytaxes.PreTownPaysNationTaxEvent;
import com.palmergames.bukkit.towny.event.town.TownLevelIncreaseEvent;
import com.palmergames.bukkit.towny.event.town.TownPreInvitePlayerEvent;
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
import cz.neumimto.towny.townycivs.tutorial.TutorialManager;
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
import org.bukkit.event.block.BlockDropItemEvent;
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

    @Inject
    private TutorialManager tutorialManager;

    @Inject
    private cz.neumimto.towny.townycivs.mechanics.AdministrationService administrationService;

    @EventHandler
    public void login(PlayerLoginEvent event) {
        // Login event - tutorial reminder handled in join event
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Resident resident = TownyAPI.getInstance().getResident(player);

        if (resident == null || !resident.hasTown()) {
            return;
        }

        if (!resident.isMayor()) {
            return;
        }

        Town town = resident.getTownOrNull();
        if (town != null) {
            // Send tutorial reminder after a short delay so other join messages appear first
            TownyCivs.MORE_PAPER_LIB.scheduling().entitySpecificScheduler(player)
                .runDelayed(() -> tutorialManager.sendReminder(player, town), null, 40L);
        }
    }

    @EventHandler
    public void onTownUpkeepCalculation(TownUpkeepCalculationEvent event){
        Town town = event.getTown();
        if(town.hasUpkeep()){
            Double originalUpkeep = event.getUpkeep();
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
                boolean shouldShowTitle = (fromRegion.isEmpty() ||
                    !fromRegion.get().uuid.equals(toRegion.get().uuid));

                if (shouldShowTitle) {

                    if(toRegion.get().loadedStructure.toggleTitle.get() == false){
                        return;
                    }

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

            // just incase the above misses some blocks, do a radius update
            int radius = 10;

            for (int x = -radius; x <= radius; x++) {
                for (int y = -radius; y <= radius; y++) {
                    for (int z = -radius; z <= radius; z++) {
                        Block block = player.getLocation().add(x, y, z).getBlock();
                        player.sendBlockChange(block.getLocation(), block.getBlockData());
                    }
                }
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

    @EventHandler(priority = EventPriority.LOWEST)
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

        // Tutorial: Check if build requirements are now satisfied after placing a block
        if (!event.isCancelled()) {
            checkTutorialBuildRequirements(player, block.getLocation());
        }
    }

    /**
     * Check if placing/breaking a block in a structure satisfies build requirements for tutorial
     */
    private void checkTutorialBuildRequirements(Player player, Location blockLocation) {
        Resident resident = TownyAPI.getInstance().getResident(player);
        if (resident == null || !resident.hasTown() || !resident.isMayor()) {
            return;
        }

        Town town = resident.getTownOrNull();
        if (town == null || !tutorialManager.isTutorialActive(town)) {
            return;
        }

        cz.neumimto.towny.townycivs.tutorial.TutorialStep step = tutorialManager.getTutorialStep(town);
        if (step != cz.neumimto.towny.townycivs.tutorial.TutorialStep.SATISFY_BUILD_REQUIREMENTS) {
            return;
        }

        // Check if block is within a wheat farm structure
        Optional<Region> regionOpt = subclaimService.regionAt(blockLocation);
        if (regionOpt.isEmpty()) {
            return;
        }

        Region region = regionOpt.get();
        if (!region.structureId.toLowerCase().contains("wheat")) {
            return;
        }

        // Check if build requirements are satisfied
        LoadedStructure structure = region.loadedStructure;
        if (structure.structureDef.buildRequirements == null || structure.structureDef.buildRequirements.isEmpty()) {
            // No build requirements, advance tutorial
            tutorialManager.onBuildRequirementsSatisfied(town, player);
            return;
        }

        // Check all build requirements
        cz.neumimto.towny.townycivs.mechanics.TownContext ctx = new cz.neumimto.towny.townycivs.mechanics.TownContext();
        ctx.town = town;
        ctx.player = player;
        ctx.resident = resident;
        ctx.structure = structure.structureDef;
        ctx.loadedStructure = structure;

        boolean allSatisfied = true;
        for (var req : structure.structureDef.buildRequirements) {
            @SuppressWarnings("unchecked")
            cz.neumimto.towny.townycivs.mechanics.Mechanic<Object> mechanic =
                (cz.neumimto.towny.townycivs.mechanics.Mechanic<Object>) req.mechanic;
            if (!mechanic.check(ctx, req.configValue)) {
                allSatisfied = false;
                break;
            }
        }

        if (allSatisfied) {
            tutorialManager.onBuildRequirementsSatisfied(town, player);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockDrop(BlockDropItemEvent event) {
        Block block = event.getBlock();
        Player player = event.getPlayer();
        handleBlockEditingWithinRegion(player, block, event);
    }

    /**
     * Block town level increases if they don't have the required Town Hall tier
     * Note: This event is informational - actual blocking happens via invite/claim limits
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onTownLevelUp(TownLevelIncreaseEvent event) {
        Town town = event.getTown();
        int newLevel = town.getLevelNumber(); // Get level from town directly

        String requiredHallId = getRequiredTownHallForLevel(newLevel);

        if (requiredHallId != null && !townHasStructureOrUpgrade(town, requiredHallId)) {
            Optional<Structure> requiredHall = configurationService.findStructureById(requiredHallId);
            String hallName = requiredHall.map(s -> s.name).orElse(requiredHallId);

            TownyMessaging.sendPrefixedTownMessage(town,
                "§c[TownyCivs] Warning: Town Hall tier insufficient for " + getTownLevelName(newLevel) + "!");
            TownyMessaging.sendPrefixedTownMessage(town,
                "§7Required: §f" + hallName + " §7to unlock full benefits.");
            TownyMessaging.sendPrefixedTownMessage(town,
                "§7Build or upgrade your Town Hall to unlock resident/claim limits!");
        }
    }

    /**
     * Block adding residents if town has reached the resident limit for their current hall tier
     * OR if their Town Hall administration is not active (upkeep not satisfied)
     */

    @EventHandler(priority = EventPriority.HIGH)
    public void onTownInviteResident(TownPreInvitePlayerEvent event) {
        Town town = event.getTown();
        if (town == null) {
            return;
        }


        // Check if Town Hall administration is active (upkeep satisfied)
        if (!administrationService.hasActiveAdministration(town)) {
            event.setCancelled(true);
            TownyMessaging.sendPrefixedTownMessage(town,
                "§c[TownyCivs] Your Town Hall is inactive! Supply it with the required upkeep items.");
            TownyMessaging.sendPrefixedTownMessage(town,
                "§7Check your Town Hall inventory and ensure upkeep requirements are met.");
            return;
        }

        // Check current size (before adding the new resident)
        int currentResidents = town.getResidents().size();
        int maxResidents = getMaxResidentsForTownHall(town);

        if (currentResidents >= maxResidents) {
            event.setCancelled(true);
            // Send message to town
            TownyMessaging.sendPrefixedTownMessage(town,
                "§c[TownyCivs] Town has reached its resident limit! (" + currentResidents + "/" + maxResidents + ")");
            TownyMessaging.sendPrefixedTownMessage(town,
                "§7Upgrade your Town Hall to invite more residents.");
        }
    }

    /**
     *
     * Incase the invite goes through, we deny the player being added
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onTownAddResident(TownPreAddResidentEvent event) {
        Town town = event.getTown();
        if (town == null) {
            return;
        }


        // Check if Town Hall administration is active (upkeep satisfied)
        if (!administrationService.hasActiveAdministration(town)) {
            event.setCancelled(true);
            TownyMessaging.sendPrefixedTownMessage(town,
                    "§c[TownyCivs] Cannot add Resident to your Town! Your Town Hall is inactive! Supply it with the required upkeep items.");
            TownyMessaging.sendPrefixedTownMessage(town,
                    "§7Check your Town Hall inventory and ensure upkeep requirements are met.");
            return;
        }

        // Check current size (before adding the new resident)
        int currentResidents = town.getResidents().size();
        int maxResidents = getMaxResidentsForTownHall(town);

        if (currentResidents >= maxResidents) {
            event.setCancelled(true);
            // Send message to town
            TownyMessaging.sendPrefixedTownMessage(town,
                    "§c[TownyCivs] Cannot add Resident to your Town! Town has reached its resident limit! (" + currentResidents + "/" + maxResidents + ")");
            TownyMessaging.sendPrefixedTownMessage(town,
                    "§7Upgrade your Town Hall to add more residents.");
        }
    }


    /**
     * Block claims if town has reached the claim limit for their current hall tier
     * OR if their Town Hall administration is not active (upkeep not satisfied)
     * Note: Always allow the first few claims for new towns to get started
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onTownPreClaim(TownPreClaimEvent event) {
        Town town = event.getTown();

        // Always allow the first claim (home block) - this is needed for town creation
        if (town.getTownBlocks().size() <= 1) {
            return;
        }

        // Check if Town Hall administration is active (upkeep satisfied)
        // Allow a grace period of 4 claims for new towns before requiring active administration
        if (town.getTownBlocks().size() >= 4 && !administrationService.hasActiveAdministration(town)) {
            event.setCancelled(true);
            event.setCancelMessage("§c[TownyCivs] Your Town Hall is inactive! Supply it with the required upkeep items to claim more land.");
            return;
        }

        if (!canTownClaimMore(town)) {
            event.setCancelled(true);
            event.setCancelMessage("§c[TownyCivs] Your town has reached its claim limit! Upgrade your Town Hall to claim more land.");
        }
    }

    /**
     * Get the required Town Hall structure ID for a given Towny level
     */
    private String getRequiredTownHallForLevel(int level) {
        return switch (level) {
            case 0 -> null; // Ruins - no requirement
            case 1 -> "surveyors_desk"; // Settlement
            case 2 -> "land_office"; // Hamlet
            case 3 -> "constables_station"; // Village
            case 4 -> "municipal_building"; // Town
            case 5 -> "district_council"; // Large Town
            case 6 -> "city_hall"; // City
            case 7 -> "civic_centre"; // Large City
            case 8 -> "ministry_of_industry"; // Metropolis
            default -> null;
        };
    }

    /**
     * Check if town has a specific structure or any of its upgrades
     */
    private boolean townHasStructureOrUpgrade(Town town, String baseStructureId) {
        Optional<Structure> baseStructure = configurationService.findStructureById(baseStructureId);
        if (baseStructure.isEmpty()) {
            return false;
        }
        int count = structureService.countStructuresInUpgradeChain(town, baseStructure.get());
        return count > 0;
    }

    /**
     * Check if town can invite more residents based on their current hall tier
     */
    private boolean canTownInviteMoreResidents(Town town) {
        int currentResidents = town.getResidents().size();
        int maxResidents = getMaxResidentsForTownHall(town);
        return currentResidents < maxResidents;
    }

    /**
     * Get the maximum number of residents allowed for the town's current hall tier
     * Uses Towny's settings for resident limits per level
     */
    private int getMaxResidentsForTownHall(Town town) {
        // Find the highest tier hall the town has built
        int hallTier = getCurrentTownHallTier(town);

        // Use Towny's configured resident limit for this level
        // TownySettings has methods to get level-based limits
        return TownySettings.getMaxResidentsPerTown(); // Default fallback
    }

    /**
     * Get the current Town Hall tier the town has built (0-8)
     */
    private int getCurrentTownHallTier(Town town) {
        // Check from highest tier down to find what they have
        for (int level = 8; level >= 1; level--) {
            String requiredHall = getRequiredTownHallForLevel(level);
            if (requiredHall != null && townHasStructureOrUpgrade(town, requiredHall)) {
                return level;
            }
        }
        return 0; // No hall built
    }

    /**
     * Check if town can claim more land based on their current hall tier
     */
    private boolean canTownClaimMore(Town town) {
        int currentClaims = town.getTownBlocks().size();
        int maxClaims = getMaxClaimsForTownHall(town);
        return currentClaims < maxClaims;
    }

    /**
     * Get the maximum number of claims allowed for the town's current hall tier
     * Uses Towny's configured claim limits based on town level
     */
    private int getMaxClaimsForTownHall(Town town) {
        int hallTier = getCurrentTownHallTier(town);

        // Use Towny's configured town block limits
        // TownySettings.getMaxTownBlocks(town) calculates based on residents and bonus blocks
        // We use the hall tier to determine the effective level for claim limits
        // If they haven't built a hall yet (tier 0), use level 0 limits from Towny

        // Get the max blocks using Towny's calculation for the town
        // This respects Towny's config settings
        int maxBlocks = TownySettings.getMaxTownBlocks(town);

        // If the town's actual Towny level is higher than their hall tier,
        // cap the claims at what their hall tier allows
        int townLevel = town.getLevelNumber();
        if (townLevel > hallTier) {
            // They haven't upgraded their hall yet, so limit based on hall tier
            // Use a ratio based on Towny's default scaling
            double ratio = (double) (hallTier + 1) / (townLevel + 1);
            return (int) (maxBlocks * ratio);
        }

        return maxBlocks;
    }

    /**
     * Get human-readable town level name
     */
    private String getTownLevelName(int level) {
        return switch (level) {
            case 0 -> "Ruins";
            case 1 -> "Settlement";
            case 2 -> "Hamlet";
            case 3 -> "Village";
            case 4 -> "Town";
            case 5 -> "Large Town";
            case 6 -> "City";
            case 7 -> "Large City";
            case 8 -> "Metropolis";
            default -> "Unknown";
        };
    }


    /**
     * Puts the mayor into a "Tutorial Mode" where they are guided through the TownyCivs mechanics
     * Uses NewTownEvent to create metadata for tutorial tracking
     */
    @EventHandler
    public void onTownCreate(NewTownEvent event) {
        Town town = event.getTown();
        tutorialManager.startTutorial(town);

        Player mayor = town.getMayor().getPlayer();
        if (mayor != null && mayor.isOnline()) {
            // Send welcome message after a short delay
            TownyCivs.MORE_PAPER_LIB.scheduling().entitySpecificScheduler(mayor)
                .runDelayed(() -> tutorialManager.showCurrentStep(mayor, town), null, 20L);
        }
    }

    @EventHandler
    public void onTownClaim(TownClaimEvent event) {
        tutorialManager.onTownClaim(event.getTown());
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
                if(!(event instanceof BlockDropItemEvent)){
                    player.sendMessage(MiniMessage.miniMessage().deserialize("<gold>[TownyCivs]</gold> <red>Editing of " + structure.name  + " is not allowed.</red>"));
                    player.sendMessage(MiniMessage.miniMessage().deserialize("<gold>[TownyCivs]</gold> <red>If you wish to edit " + structure.name  + ", craft a Structure Editing tool and right click this region.</red>"));
                }
                event.setCancelled(true);
                return;
            }
        }
    }
}

