package cz.neumimto.towny.townycivs.gui;

import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import cz.neumimto.towny.townycivs.*;
import cz.neumimto.towny.townycivs.config.ConfigurationService;
import cz.neumimto.towny.townycivs.config.Structure;
import cz.neumimto.towny.townycivs.db.Storage;
import cz.neumimto.towny.townycivs.gui.api.GuiCommand;
import cz.neumimto.towny.townycivs.gui.api.GuiConfig;
import cz.neumimto.towny.townycivs.mechanics.ItemUpkeep;
import cz.neumimto.towny.townycivs.mechanics.Mechanic;
import cz.neumimto.towny.townycivs.mechanics.Mechanics;
import cz.neumimto.towny.townycivs.mechanics.TownContext;
import cz.neumimto.towny.townycivs.mechanics.common.DoubleWrapper;
import cz.neumimto.towny.townycivs.model.LoadedStructure;
import cz.neumimto.towny.townycivs.model.Region;
import cz.neumimto.towny.townycivs.model.StructureAndCount;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BundleMeta;
import org.jetbrains.annotations.NotNull;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;

@Singleton
public class RegionGui extends TCGui {

    @Inject
    private SubclaimService subclaimService;

    @Inject
    private ConfigurationService configurationService;

    @Inject
    private StructureService structureService;

    @Inject
    private ManagementService managementService;

    @Inject
    private cz.neumimto.towny.townycivs.tutorial.TutorialManager tutorialManager;

    // Track which players have the region GUI open and which region
    private final Map<UUID, UUID> openRegionGuis = new HashMap<>();
    private int refreshTaskId = -1;

    public RegionGui() {
        super("Region.conf", TownyCivs.INSTANCE.getDataFolder().toPath());
        startRefreshTask();
    }

    /**
     * Remove a player from region GUI tracking
     */
    public void removePlayerFromTracking(UUID playerId) {
        openRegionGuis.remove(playerId);
    }

    /**
     * Start a task that refreshes open GUIs every second (20 ticks)
     */
    private void startRefreshTask() {
        if (refreshTaskId != -1) {
            return; // Already running
        }

        // Use Bukkit scheduler for repeating task
        refreshTaskId = TownyCivs.INSTANCE.getServer().getScheduler().scheduleSyncRepeatingTask(
            TownyCivs.INSTANCE,
            () -> {
                // Refresh all open region GUIs
                for (Map.Entry<UUID, UUID> entry : new HashMap<>(openRegionGuis).entrySet()) {
                    UUID playerUuid = entry.getKey();
                    UUID regionUuid = entry.getValue();

                    Player player = TownyCivs.INSTANCE.getServer().getPlayer(playerUuid);
                    if (player == null || !player.isOnline()) {
                        openRegionGuis.remove(playerUuid);
                        continue;
                    }

                    // Refresh the GUI
                    Region region = subclaimService.getRegion(regionUuid);
                    if (region != null) {
                        updateGuiSilently(player, region);
                    }
                }
            },
            20L, // Initial delay: 1 second
            20L  // Repeat every: 1 second
        );
    }

    /**
     * Updates the GUI items without closing and reopening the inventory
     */
    private void updateGuiSilently(Player player, Region region) {
        try {
            // Get the current inventory title as plain text
            String currentTitle = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(player.getOpenInventory().title());

            // Calculate expected title to verify player is still in the correct GUI
            Town town = TownyAPI.getInstance().getResident(player).getTownOrNull();
            if (town == null) {
                openRegionGuis.remove(player.getUniqueId());
                return;
            }

            Structure structureById = configurationService.findStructureById(region.structureId).orElse(null);
            if (structureById == null) {
                openRegionGuis.remove(player.getUniqueId());
                return;
            }

            String rawTitle = town.getPrefix() + " " + town.getName() + " - " + structureById.name;
            String expectedTitle = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(MiniMessage.miniMessage().deserialize(rawTitle));

            if (!currentTitle.equals(expectedTitle)) {
                // Title mismatch - player is not in the region GUI (could be sub-GUI, chest, etc.)
                openRegionGuis.remove(player.getUniqueId());
                return;
            }

            // Recreate the GUI data
            Map<String, List<GuiCommand>> paneData = getPaneData(player, region.uuid.toString());

            // Update only the status item in the player's current view
            List<GuiCommand> statusCommands = paneData.get("Status");
            if (statusCommands != null && !statusCommands.isEmpty()) {
                ItemStack statusItem = statusCommands.get(0).getItem();
                // Update the status slot (slot 13 based on Region.conf)
                player.getOpenInventory().getTopInventory().setItem(13, statusItem);
            }
        } catch (Exception e) {
            // If update fails, just skip this cycle
            openRegionGuis.remove(player.getUniqueId());
        }
    }

    public void display(Player player, Region region) {
        // Track tutorial progress - structure GUI opened
        com.palmergames.bukkit.towny.object.Resident resident = TownyAPI.getInstance().getResident(player);
        if (resident != null && resident.hasTown() && resident.isMayor()) {
            com.palmergames.bukkit.towny.object.Town town = resident.getTownOrNull();
            if (town != null) {
                // Pass structure ID to detect if player already has a wheat farm
                tutorialManager.onStructureGuiOpened(town, player, region.structureId);
            }
        }

        // Track that this player has the region GUI open
        openRegionGuis.put(player.getUniqueId(), region.uuid);

        ChestGui chestGui = loadGui(player, region.uuid.toString());
        chestGui.setOnClose(event -> onGuiClose(player));
        chestGui.show(player);
    }

    /**
     * Call this when a player closes the region GUI
     */
    public void onGuiClose(Player player) {
        openRegionGuis.remove(player.getUniqueId());
    }

    @Override
    protected String getTitle(CommandSender commandSender, GuiConfig guiConfig, String param) {
        Town town = TownyAPI.getInstance().getResident((Player) commandSender).getTownOrNull();

        Region region = subclaimService.getRegion(UUID.fromString(param));
        Structure structureById = configurationService.findStructureById(region.structureId).get();


        return town.getPrefix() + " " + town.getName() + " - " + structureById.name;
    }

    @Override
    public Map<String, List<GuiCommand>> getPaneData(CommandSender commandSender, String param) {
        Player player = (Player) commandSender;
        Town town = TownyAPI.getInstance().getResident(player).getTownOrNull();
        Map<String, List<GuiCommand>> map = new HashMap<>();
        TownContext townContext = new TownContext();
        townContext.town = town;
        townContext.player = player;
        townContext.resident = TownyAPI.getInstance().getResident(player);
        Region region = subclaimService.getRegion(UUID.fromString(param));
        townContext.structure = region.loadedStructure.structureDef;
        townContext.loadedStructure = region.loadedStructure;


        StructureAndCount count = structureService.findTownStructureById(town, region.loadedStructure.structureDef);
        ItemStack structureInfoStack = structureService.toItemStack(region.loadedStructure.structureDef, count.count);

        map.put("Structure", List.of(new GuiCommand(structureInfoStack, e -> e.setCancelled(true))));

        MiniMessage mm = MiniMessage.miniMessage();
        ItemStack editMode = new ItemStack(managementService.isBeingEdited(region.loadedStructure) ? Material.RED_WOOL : Material.GREEN_WOOL);
        editMode.editMeta(itemMeta -> {
            var lore = new ArrayList<Component>();

            String editModeS = null;
            if (managementService.isBeingEdited(region.loadedStructure)) {
                editModeS = "<red>Active<red>";
            } else {
                editModeS = "<green>Inactive<green>";
            }
            itemMeta.displayName(mm.deserialize("<gold>Edit mode</gold> : " + editModeS));

            lore.add(Component.empty());
            lore.add(mm.deserialize("<red>Active<red><white>- structure is disabled & its region may be edited</white>"));
            lore.add(mm.deserialize("<green>Inactive<green><white>- structure is enabled & its region may not be edited</white>"));
            itemMeta.lore(lore);
        });
        map.put("EditModeToggle", List.of(new GuiCommand(editMode, e -> {
            e.setCancelled(true);
            boolean prev = managementService.isBeingEdited(region.loadedStructure);
            managementService.toggleEditMode(region.loadedStructure, (Player) e.getWhoClicked());
            if (prev == managementService.isBeingEdited(region.loadedStructure)) {
                e.getWhoClicked().closeInventory(InventoryCloseEvent.Reason.PLUGIN);
                return;
            }

            Storage.scheduleSave(region.loadedStructure);
            display((Player) e.getWhoClicked(), region);

        })));

        ItemStack delete = new ItemStack(Material.BARRIER);
        delete.editMeta(itemMeta -> {
            itemMeta.displayName(mm.deserialize("<red>Delete</red>"));
        });
        map.put("Delete", List.of(new GuiCommand(delete, e -> {
            Set<Location> locations = managementService.prepareVisualBox((Player) e.getWhoClicked(), region.loadedStructure.center, region.loadedStructure.structureDef.area);
            Set<Location> center = Collections.singleton(region.loadedStructure.center);

            for (Location loc : locations) {
                Block block = loc.getBlock();
                player.sendBlockChange(loc, block.getBlockData());
            }

            for (Location loc : center) {
                Block block = loc.getBlock();
                player.sendBlockChange(loc, block.getBlockData());
            }
            structureService.delete(region, (Player) e.getWhoClicked());
            e.setCancelled(true);
            player.getOpenInventory().close();
        })));


        ItemStack remBlocks = new ItemStack(Material.IRON_AXE);
        remBlocks.editMeta(itemMeta -> {
            itemMeta.displayName(mm.deserialize("<yellow>Remaining build requirements</yellow>"));
            itemMeta.addItemFlags(ItemFlag.values());
        });
        map.put("RemainingBlocks", List.of(new GuiCommand(remBlocks, e -> {
            e.setCancelled(true);
            // Stop tracking when opening sub-GUI
            openRegionGuis.remove(((Player) e.getWhoClicked()).getUniqueId());
            ChestGui chestGui = remainingBlocksGui(region);
            chestGui.show(e.getWhoClicked());
        })));

        ItemStack location = new ItemStack(Material.COMPASS);
        Location loc = region.loadedStructure.center;
        location.editMeta(itemMeta -> {
                    itemMeta.displayName(mm.deserialize("<aqua>Location: " + "X: " + loc.getBlockX() + " Y: " + loc.getBlockY() + " Z: " + loc.getBlockZ() + "</aqua>"));
        });
        map.put("Location", List.of(new GuiCommand(location, e -> e.setCancelled(true))));

        // Calculate ticks remaining until next production
        long currentTime = System.currentTimeMillis();
        long timeUntilNextTick;



        // Use nextTickTime if available, otherwise calculate from lastTickTime
        if (region.loadedStructure.nextTickTime > 0) {
            timeUntilNextTick = region.loadedStructure.nextTickTime - currentTime;
        } else if (region.loadedStructure.lastTickTime > 0) {
            long tickPeriodMs = region.loadedStructure.structureDef.period * 50L;
            long timeSinceLastTick = currentTime - region.loadedStructure.lastTickTime;
            timeUntilNextTick = tickPeriodMs - timeSinceLastTick;
        } else {
            // Structure just placed, assume full period
            timeUntilNextTick = region.loadedStructure.structureDef.period * 50L;
        }

        long secondsRemaining = Math.max(0, timeUntilNextTick / 1000L);

        ItemStack status = new ItemStack(Material.LIME_WOOL);
        status.editMeta(itemMeta -> {
            itemMeta.displayName(mm.deserialize("<green>Your Structure is running smoothly.</green>"));
            var lore = new ArrayList<Component>();
            lore.add(mm.deserialize("<gray>Next production in: <yellow>" + secondsRemaining + "s</yellow></gray>"));
            itemMeta.lore(lore);
        });

        List<Structure.LoadedPair<Mechanic<Object>, Object>> upkeep = region.loadedStructure.structureDef.upkeep;
        for (Structure.LoadedPair<Mechanic<Object>, Object> m : upkeep) {
            if(!m.mechanic.check(townContext, m.configValue)){
                status = new ItemStack(Material.RED_WOOL);
                status.editMeta(itemMeta -> {
                    itemMeta.displayName(mm.deserialize("<red>Your Structure has missing upkeep!</red>"));
                    var lore = new ArrayList<Component>();
                    lore.add(mm.deserialize("<gray>Production paused due to missing upkeep</gray>"));
                    lore.add(mm.deserialize("<gray>Would process in: <dark_gray>" + secondsRemaining + "s</dark_gray></gray>"));
                    itemMeta.lore(lore);
                });
                break;
            }else{
                status = new ItemStack(Material.LIME_WOOL);
                status.editMeta(itemMeta -> {
                    itemMeta.displayName(mm.deserialize("<green>Your Structure is running smoothly.</green>"));
                    var lore = new ArrayList<Component>();
                    lore.add(mm.deserialize("<gray>Next production in: <yellow>" + secondsRemaining + "s</yellow></gray>"));
                    itemMeta.lore(lore);
                });
            }


        }

        map.put("Status", List.of(new GuiCommand(status, e -> {
            e.setCancelled(true);
        })));



        ItemStack production = new ItemStack(Material.CHEST);
        production.editMeta(itemMeta -> {
            itemMeta.displayName(mm.deserialize("<green>Structure Input/Output.</green>"));
            var lore = new ArrayList<Component>();
            lore.add(mm.deserialize("<gray>Left Click to check Input, Right Click to see Output.</gray>"));
            itemMeta.lore(lore);
        });

        map.put("Production", List.of(new GuiCommand(production, e -> {
            e.setCancelled(true);
            Player clickPlayer = (Player) e.getWhoClicked();
            if(e.isLeftClick()){
                // Stop tracking when opening sub-GUI
                openRegionGuis.remove(clickPlayer.getUniqueId());
                ChestGui chestGui = upkeepGui(region);
                chestGui.show(clickPlayer);
            }else if(e.isRightClick()){
                // Stop tracking when opening sub-GUI
                openRegionGuis.remove(clickPlayer.getUniqueId());
                ChestGui chestGui = productionGui(region);
                chestGui.show(clickPlayer);
            }
        }

        )));

        ItemStack upgrade = new ItemStack(Material.AIR);

        // Check if structure has an upgrade path defined
        String upgradePath = townContext.loadedStructure.structureDef.upgradePath;
        boolean canShowUpgrade = false;

        if (upgradePath != null && !upgradePath.isEmpty()) {
            // When upgrading, we're REPLACING the current structure (same UUID), not adding a new one
            // The total count in the upgrade chain stays the same after upgrading
            // Example: 2 coal_mine → upgrade 1 → 1 coal_mine + 1 advanced_coal_mine = still 2 total

            Structure baseStructure = townContext.loadedStructure.structureDef;
            var targetStructureOpt = configurationService.findStructureById(upgradePath);
            boolean blockedByCount = false;

            // Only check MaxCount if the target structure has a limit
            if (targetStructureOpt.isPresent()) {
                Structure targetStructure = targetStructureOpt.get();

                if (targetStructure.maxCount != null && targetStructure.maxCount > 0) {
                    // Find the root of the upgrade chain to check against
                    // For coal_mine → advanced_coal_mine, the root is coal_mine
                    Structure rootStructure = baseStructure;
                    if (baseStructure.upgradeFrom != null && !baseStructure.upgradeFrom.isEmpty()) {
                        // Current structure is already an upgrade, find its base
                        var baseOpt = configurationService.findStructureById(baseStructure.upgradeFrom);
                        if (baseOpt.isPresent()) {
                            rootStructure = baseOpt.get();
                        }
                    }

                    // Count total structures in this upgrade chain
                    int chainCount = structureService.countStructuresInUpgradeChain(town, rootStructure);

                    // After upgrading, the chain count stays the same (we're replacing, not adding)
                    // Only block if we've somehow already exceeded the limit
                    if (chainCount > targetStructure.maxCount) {
                        blockedByCount = true;
                    }
                }
            }

            canShowUpgrade = !blockedByCount;

            if (canShowUpgrade) {
                upgrade = new ItemStack(Material.DIAMOND);
                upgrade.editMeta(itemMeta -> {
                    itemMeta.displayName(mm.deserialize("<green>Upgrade your structure</green>"));
                    var lore = new ArrayList<Component>();
                    lore.add(mm.deserialize("<gray>Upgrades to: <aqua>" + upgradePath + "</aqua></gray>"));
                    lore.add(Component.empty());
                    lore.add(mm.deserialize("<gray>Left-click: View upgrade requirements</gray>"));
                    lore.add(mm.deserialize("<gray>Right-click: Confirm upgrade</gray>"));
                    itemMeta.lore(lore);
                });
            } else {
                // Show blocked upgrade button
                upgrade = new ItemStack(Material.BARRIER);
                upgrade.editMeta(itemMeta -> {
                    itemMeta.displayName(mm.deserialize("<red>Cannot Upgrade</red>"));
                    var lore = new ArrayList<Component>();
                    lore.add(mm.deserialize("<gray>Upgrades to: <aqua>" + upgradePath + "</aqua></gray>"));
                    lore.add(Component.empty());
                    lore.add(mm.deserialize("<red>Maximum build count reached!</red>"));
                    lore.add(mm.deserialize("<gray>You have too many upgraded structures.</gray>"));
                    itemMeta.lore(lore);
                });
            }
        }

        final boolean finalCanShowUpgrade = canShowUpgrade;
        map.put("Upgrade", List.of(new GuiCommand(upgrade, e -> {
            e.setCancelled(true);
            String path = townContext.loadedStructure.structureDef.upgradePath;
            if (path == null || path.isEmpty()) {
                return; // No upgrade path defined
            }
            if (!finalCanShowUpgrade) {
                player.sendMessage(mm.deserialize("<gold>[TownyCivs]</gold> <red>Cannot upgrade: Maximum build count reached for this upgrade chain!</red>"));
                return;
            }
            if(e.isLeftClick()){
                // Stop tracking when opening sub-GUI
                openRegionGuis.remove(((Player) e.getWhoClicked()).getUniqueId());
                ChestGui chestGui = upgradeRequirementsGui(region, townContext);
                chestGui.show(e.getWhoClicked());
            } else if(e.isRightClick()){
                // Stop tracking when opening sub-GUI
                openRegionGuis.remove(((Player) e.getWhoClicked()).getUniqueId());
                ChestGui chestGui = confirmUpgradeGui(region, townContext);
                chestGui.show(e.getWhoClicked());
            }
        })));

        return map;
    }

    private ChestGui upkeepGui(Region region) {
        MiniMessage mm = MiniMessage.miniMessage();
        ChestGui chestGui = new ChestGui(4, "Upkeep");

        StaticPane staticPane = new StaticPane(9, 4);
        chestGui.addPane(staticPane);

        // Back button in bottom-left corner
        ItemStack backButton = new ItemStack(Material.ARROW);
        backButton.editMeta(meta -> meta.displayName(mm.deserialize("<green>← Back to Structure</green>")));
        staticPane.addItem(new GuiItem(backButton, e -> {
            e.setCancelled(true);
            // Re-track when going back to main GUI
            openRegionGuis.put(((Player) e.getWhoClicked()).getUniqueId(), region.uuid);
            display((Player) e.getWhoClicked(), region);
        }), 0, 3);

        int x = 0;
        int y = 0;

        List<Structure.LoadedPair<Mechanic<Object>, Object>> upkeep = region.loadedStructure.structureDef.upkeep;

        // Create town context for mechanics to use
        TownContext townContext = new TownContext();
        townContext.town = TownyAPI.getInstance().getTown(region.loadedStructure.town);
        townContext.loadedStructure = region.loadedStructure;
        townContext.structure = region.loadedStructure.structureDef;

        // Let each mechanic provide its own GUI items
        for (Structure.LoadedPair<Mechanic<Object>, Object> m : upkeep) {
            @SuppressWarnings("unchecked")
            Mechanic<Object> mechanic = m.mechanic;
            Object configValue = m.configValue;

            // Get upkeep GUI items from the mechanic itself
            List<ItemStack> guiItems = mechanic.getUpkeepGuiItems(townContext, configValue);

            // Add all items to the GUI
            for (ItemStack itemStack : guiItems) {
                staticPane.addItem(new GuiItem(itemStack, e -> e.setCancelled(true)), x, y);
                x++;
                if (x == 9) {
                    x = 0;
                    y++;
                }
            }
        }
        return chestGui;
    }

    private ChestGui productionGui(Region region) {
        MiniMessage mm = MiniMessage.miniMessage();
        ChestGui chestGui = new ChestGui(4, "Production");

        StaticPane staticPane = new StaticPane(9, 4);
        chestGui.addPane(staticPane);

        // Back button in bottom-left corner
        ItemStack backButton = new ItemStack(Material.ARROW);
        backButton.editMeta(meta -> meta.displayName(mm.deserialize("<green>← Back to Structure</green>")));
        staticPane.addItem(new GuiItem(backButton, e -> {
            e.setCancelled(true);
            // Re-track when going back to main GUI
            openRegionGuis.put(((Player) e.getWhoClicked()).getUniqueId(), region.uuid);
            display((Player) e.getWhoClicked(), region);
        }), 0, 3);

        int x = 0;
        int y = 0;

        List<Structure.LoadedPair<Mechanic<Object>, Object>> production = region.loadedStructure.structureDef.production;

        // Create town context for mechanics to use
        TownContext townContext = new TownContext();
        townContext.town = TownyAPI.getInstance().getTown(region.loadedStructure.town);
        townContext.loadedStructure = region.loadedStructure;
        townContext.structure = region.loadedStructure.structureDef;


        // Let each mechanic provide its own GUI items
        for (Structure.LoadedPair<Mechanic<Object>, Object> m : production) {
            @SuppressWarnings("unchecked")
            Mechanic<Object> mechanic = m.mechanic;
            Object configValue = m.configValue;

            // Get GUI items from the mechanic itself
            List<ItemStack> guiItems = mechanic.getGuiItems(townContext, configValue);

            // Add all items to the GUI
            for (ItemStack itemStack : guiItems) {
                staticPane.addItem(new GuiItem(itemStack, e -> e.setCancelled(true)), x, y);
                x++;
                if (x == 9) {
                    x = 0;
                    y++;
                }
            }
        }
        return chestGui;
    }

    @NotNull
    private ChestGui remainingBlocksGui(Region region) {
        MiniMessage mm = MiniMessage.miniMessage();
        ChestGui chestGui = new ChestGui(4, "Remaining Blocks");

        StaticPane staticPane = new StaticPane(9, 4);

        chestGui.addPane(staticPane);

        // Back button in bottom-left corner
        ItemStack backButton = new ItemStack(Material.ARROW);
        backButton.editMeta(meta -> meta.displayName(mm.deserialize("<green>← Back to Structure</green>")));
        staticPane.addItem(new GuiItem(backButton, e -> {
            e.setCancelled(true);
            // Re-track when going back to main GUI
            openRegionGuis.put(((Player) e.getWhoClicked()).getUniqueId(), region.uuid);
            display((Player) e.getWhoClicked(), region);
        }), 0, 3);

        int x = 0;
        int y = 0;
        Map<String, Integer> requirements = region.loadedStructure.structureDef.blocks;

        for (Map.Entry<String, Integer> entry : subclaimService.remainingBlocks(region).entrySet()) {
            String key = entry.getKey();
            Integer remaining = entry.getValue();
            if (key.startsWith("tc:")) {
                if (remaining <= 0) {
                    continue;
                }
                ItemStack itemStack = new ItemStack(Material.BUNDLE);
                itemStack.editMeta(itemMeta -> {
                    String group = key.substring(3);
                    Integer req = requirements.get(key);
                    int current = req - remaining;
                    itemMeta.displayName(mm.deserialize(group + " - " + current + "/" + req + "x"));
                    BundleMeta bundleMeta = (BundleMeta) itemMeta;

                    Collection<Material> blockGroup = Materials.getMaterials(key);
                    for (Material material : blockGroup) {
                        bundleMeta.addItem(new ItemStack(material));
                    }
                });
                staticPane.addItem(new GuiItem(itemStack, ice -> ice.setCancelled(true)), x, y);
            } else if (key.startsWith("!tc:")) {
                if (remaining == 0) {
                    continue;
                }
                ItemStack itemStack = new ItemStack(Material.BUNDLE);
                itemStack.editMeta(itemMeta -> {
                    String group = key.substring(4);
                    Integer req = requirements.get(key);
                    int current = req - remaining;
                    itemMeta.displayName(mm.deserialize(group + " " + current + "/ exactly " + req + "x"));
                    BundleMeta bundleMeta = (BundleMeta) itemMeta;

                    Collection<Material> blockGroup = Materials.getMaterials(key.substring(1));
                    for (Material material : blockGroup) {
                        bundleMeta.addItem(new ItemStack(material));
                    }
                });
                staticPane.addItem(new GuiItem(itemStack, ice -> ice.setCancelled(true)), x, y);
            } else {
                if (remaining <= 0) {
                    continue;
                }
                Material material = Material.matchMaterial(entry.getKey());
                ItemStack itemStack = new ItemStack(material);
                itemStack.editMeta(itemMeta -> {
                    Integer req = requirements.get(key);
                    int current = req - remaining;
                    itemMeta.displayName(mm.deserialize(material.name() + " - " + current + "/" + req + "x"));
                });
                staticPane.addItem(new GuiItem(itemStack, ice -> ice.setCancelled(true)), x, y);
            }
            x++;
            if (x == 8) {
                x = 0;
                y++;
            }
        }
        return chestGui;
    }

    /**
     * Shows upgrade requirements GUI - displays what is needed to upgrade
     */
    private ChestGui upgradeRequirementsGui(Region region, TownContext townContext) {
        MiniMessage mm = MiniMessage.miniMessage();
        ChestGui chestGui = new ChestGui(4, "Upgrade Requirements");

        StaticPane staticPane = new StaticPane(9, 4);
        chestGui.addPane(staticPane);

        // Back button in bottom-left corner
        ItemStack backButton = new ItemStack(Material.ARROW);
        backButton.editMeta(meta -> meta.displayName(mm.deserialize("<green>← Back to Structure</green>")));
        staticPane.addItem(new GuiItem(backButton, e -> {
            e.setCancelled(true);
            // Re-track when going back to main GUI
            openRegionGuis.put(((Player) e.getWhoClicked()).getUniqueId(), region.uuid);
            display((Player) e.getWhoClicked(), region);
        }), 0, 3);

        // Title item showing upgrade target
        String upgradePath = townContext.loadedStructure.structureDef.upgradePath;
        var targetStructure = configurationService.findStructureById(upgradePath);
        String targetName = targetStructure.map(s -> s.name).orElse(upgradePath);

        ItemStack titleItem = new ItemStack(Material.NETHER_STAR);
        titleItem.editMeta(meta -> {
            meta.displayName(mm.deserialize("<gold>Upgrade Requirements</gold>"));
            var lore = new ArrayList<Component>();
            lore.add(mm.deserialize("<gray>Current: <white>" + townContext.structure.name + "</white></gray>"));
            lore.add(mm.deserialize("<gray>Upgrades to: <aqua>" + targetName + "</aqua></gray>"));
            lore.add(Component.empty());
            lore.add(mm.deserialize("<yellow>Requirements listed below:</yellow>"));
            meta.lore(lore);
        });
        staticPane.addItem(new GuiItem(titleItem, e -> e.setCancelled(true)), 4, 0);

        int x = 0;
        int y = 1;

        List<Structure.LoadedPair<Mechanic<?>, ?>> upgradeReqs = townContext.loadedStructure.structureDef.upgradeRequirements;

        // Handle null or empty requirements
        if (upgradeReqs == null) {
            upgradeReqs = new ArrayList<>();
        }

        // Let each mechanic provide its own GUI items
        for (Structure.LoadedPair<Mechanic<?>, ?> req : upgradeReqs) {
            // Skip the upgrade mechanic since we now use upgradePath field
            if (req.mechanic.id().equals(cz.neumimto.towny.townycivs.mechanics.Mechanics.UPGRADE)) {
                continue;
            }

            @SuppressWarnings("unchecked")
            Mechanic<Object> mechanic = (Mechanic<Object>) req.mechanic;

            // Get upgrade requirement GUI items from the mechanic itself
            List<ItemStack> guiItems = mechanic.getUpgradeRequirementGuiItems(townContext, req.configValue);

            // Add all items to the GUI
            for (ItemStack reqItem : guiItems) {
                staticPane.addItem(new GuiItem(reqItem, e -> e.setCancelled(true)), x, y);
                x++;
                if (x == 9) {
                    x = 0;
                    y++;
                }
            }
        }

        // Confirm button in bottom-right if all requirements met
        boolean allMet = true;
        for (Structure.LoadedPair<Mechanic<?>, ?> req : upgradeReqs) {
            @SuppressWarnings("unchecked")
            Mechanic<Object> mechanic = (Mechanic<Object>) req.mechanic;
            if (!mechanic.check(townContext, req.configValue)) {
                allMet = false;
                System.out.println("Requirement not met: " + mechanic.id());
                break;
            }
        }

        if (allMet) {
            ItemStack confirmBtn = new ItemStack(Material.EMERALD_BLOCK);
            confirmBtn.editMeta(meta -> {
                meta.displayName(mm.deserialize("<green><bold>Click to Upgrade!</bold></green>"));
                var lore = new ArrayList<Component>();
                lore.add(mm.deserialize("<gray>All requirements met!</gray>"));
                lore.add(mm.deserialize("<yellow>Click to proceed with upgrade.</yellow>"));
                meta.lore(lore);
            });
            staticPane.addItem(new GuiItem(confirmBtn, e -> {
                e.setCancelled(true);
                ChestGui confirmGui = confirmUpgradeGui(region, townContext);
                confirmGui.show(e.getWhoClicked());
            }), 8, 3);
        } else {
            ItemStack notReadyBtn = new ItemStack(Material.REDSTONE_BLOCK);
            notReadyBtn.editMeta(meta -> {
                meta.displayName(mm.deserialize("<red>Cannot Upgrade</red>"));
                var lore = new ArrayList<Component>();
                lore.add(mm.deserialize("<gray>Not all requirements are met.</gray>"));
                lore.add(mm.deserialize("<yellow>Complete the requirements above.</yellow>"));
                meta.lore(lore);
            });
            staticPane.addItem(new GuiItem(notReadyBtn, e -> e.setCancelled(true)), 8, 3);
        }

        return chestGui;
    }

    /**
     * Shows confirmation GUI for upgrading the structure
     */
    private ChestGui confirmUpgradeGui(Region region, TownContext townContext) {
        MiniMessage mm = MiniMessage.miniMessage();
        ChestGui chestGui = new ChestGui(3, "Confirm Upgrade");

        StaticPane staticPane = new StaticPane(9, 3);
        chestGui.addPane(staticPane);

        // Get upgrade path from structure config
        String upgradeTargetId = townContext.loadedStructure.structureDef.upgradePath;

        // Check if all requirements are met
        List<Structure.LoadedPair<Mechanic<?>, ?>> upgradeReqs = townContext.loadedStructure.structureDef.upgradeRequirements;
        if (upgradeReqs == null) {
            upgradeReqs = new ArrayList<>();
        }

        boolean allMet = true;
        for (Structure.LoadedPair<Mechanic<?>, ?> req : upgradeReqs) {
            // Skip the upgrade mechanic since we now use upgradePath
            if (req.mechanic.id().equals(cz.neumimto.towny.townycivs.mechanics.Mechanics.UPGRADE)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Mechanic<Object> mechanic = (Mechanic<Object>) req.mechanic;
            if (!mechanic.check(townContext, req.configValue)) {
                allMet = false;
            }
        }

        final String finalUpgradeTargetId = upgradeTargetId;
        final boolean canUpgrade = allMet && upgradeTargetId != null && !upgradeTargetId.isEmpty();
        final List<Structure.LoadedPair<Mechanic<?>, ?>> finalUpgradeReqs = upgradeReqs;

        // Info item in the middle
        var targetStructure = upgradeTargetId != null ? configurationService.findStructureById(upgradeTargetId) : Optional.<Structure>empty();
        String targetName = targetStructure.map(s -> s.name).orElse(upgradeTargetId != null ? upgradeTargetId : "Unknown");

        ItemStack infoItem = new ItemStack(Material.NETHER_STAR);
        infoItem.editMeta(meta -> {
            meta.displayName(mm.deserialize("<gold>Upgrade Confirmation</gold>"));
            var lore = new ArrayList<Component>();
            lore.add(Component.empty());
            lore.add(mm.deserialize("<gray>Current: <white>" + townContext.structure.name + "</white></gray>"));
            lore.add(mm.deserialize("<gray>Upgrade to: <aqua>" + targetName + "</aqua></gray>"));
            lore.add(Component.empty());
            if (canUpgrade) {
                lore.add(mm.deserialize("<green>All requirements met!</green>"));
            } else {
                lore.add(mm.deserialize("<red>Requirements not met!</red>"));
            }
            meta.lore(lore);
        });
        staticPane.addItem(new GuiItem(infoItem, e -> e.setCancelled(true)), 4, 0);

        // Cancel button (left side)
        ItemStack cancelBtn = new ItemStack(Material.RED_WOOL);
        cancelBtn.editMeta(meta -> {
            meta.displayName(mm.deserialize("<red><bold>Cancel</bold></red>"));
            var lore = new ArrayList<Component>();
            lore.add(mm.deserialize("<gray>Go back without upgrading.</gray>"));
            meta.lore(lore);
        });
        staticPane.addItem(new GuiItem(cancelBtn, e -> {
            e.setCancelled(true);
            // Re-track when going back to main GUI
            openRegionGuis.put(((Player) e.getWhoClicked()).getUniqueId(), region.uuid);
            display((Player) e.getWhoClicked(), region);
        }), 2, 1);

        // Confirm button (right side)
        if (canUpgrade) {
            ItemStack confirmBtn = new ItemStack(Material.LIME_WOOL);
            confirmBtn.editMeta(meta -> {
                meta.displayName(mm.deserialize("<green><bold>Confirm Upgrade</bold></green>"));
                var lore = new ArrayList<Component>();
                lore.add(mm.deserialize("<gray>Click to upgrade your structure!</gray>"));
                lore.add(Component.empty());
                lore.add(mm.deserialize("<yellow>This action cannot be undone.</yellow>"));
                meta.lore(lore);
            });
            staticPane.addItem(new GuiItem(confirmBtn, e -> {
                e.setCancelled(true);
                Player player = (Player) e.getWhoClicked();

                // Execute post actions (withdraw money, etc.) - skip upgrade mechanic
                for (Structure.LoadedPair<Mechanic<?>, ?> req : finalUpgradeReqs) {
                    if (req.mechanic.id().equals(cz.neumimto.towny.townycivs.mechanics.Mechanics.UPGRADE)) {
                        continue;
                    }
                    @SuppressWarnings("unchecked")
                    Mechanic<Object> mechanic = (Mechanic<Object>) req.mechanic;
                    mechanic.postAction(townContext, req.configValue);
                    mechanic.okmessage(townContext, req.configValue);
                }

                // Perform the upgrade
                boolean success = structureService.changeStructure(townContext.loadedStructure, finalUpgradeTargetId);

                if (success) {
                    player.sendMessage(mm.deserialize("<gold>[TownyCivs]</gold> <green>Structure upgraded successfully to " + targetName + "!</green>"));
                    player.closeInventory();
                } else {
                    player.sendMessage(mm.deserialize("<gold>[TownyCivs]</gold> <red>Failed to upgrade structure. Please try again.</red>"));
                    display(player, region);
                }
            }), 6, 1);
        } else {
            ItemStack disabledBtn = new ItemStack(Material.GRAY_WOOL);
            disabledBtn.editMeta(meta -> {
                meta.displayName(mm.deserialize("<gray><bold>Cannot Upgrade</bold></gray>"));
                var lore = new ArrayList<Component>();
                lore.add(mm.deserialize("<red>Requirements not met!</red>"));
                lore.add(mm.deserialize("<gray>Left-click the upgrade button to see requirements.</gray>"));
                meta.lore(lore);
            });
            staticPane.addItem(new GuiItem(disabledBtn, e -> {
                e.setCancelled(true);
                ChestGui reqGui = upgradeRequirementsGui(region, townContext);
                reqGui.show(e.getWhoClicked());
            }), 6, 1);
        }

        return chestGui;
    }

}
