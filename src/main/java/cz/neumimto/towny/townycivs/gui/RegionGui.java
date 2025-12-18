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
import cz.neumimto.towny.townycivs.mechanics.TownContext;
import cz.neumimto.towny.townycivs.model.LoadedStructure;
import cz.neumimto.towny.townycivs.model.Region;
import cz.neumimto.towny.townycivs.model.StructureAndCount;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
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

    public RegionGui() {
        super("Region.conf", TownyCivs.INSTANCE.getDataFolder().toPath());
    }

    public void display(Player player, Region region) {
        ChestGui chestGui = loadGui(player, region.uuid.toString());
        chestGui.show(player);
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
            ChestGui chestGui = remainingBlocksGui(region);
            chestGui.show(e.getWhoClicked());
        })));

        ItemStack location = new ItemStack(Material.COMPASS);
        Location loc = region.loadedStructure.center;
        location.editMeta(itemMeta -> {
                    itemMeta.displayName(mm.deserialize("<aqua>Location: " + "X: " + loc.getBlockX() + " Y: " + loc.getBlockY() + " Z: " + loc.getBlockZ() + "</aqua>"));
        });
        map.put("Location", List.of(new GuiCommand(location, e -> e.setCancelled(true))));

        ItemStack status = new ItemStack(Material.LIME_WOOL);
        status.editMeta(itemMeta -> {
            itemMeta.displayName(mm.deserialize("<green>Your Structure is running smoothly.</green>"));
        });

        List<Structure.LoadedPair<Mechanic<Object>, Object>> upkeep = region.loadedStructure.structureDef.upkeep;
        for (Structure.LoadedPair<Mechanic<Object>, Object> m : upkeep) {
            if(!m.mechanic.check(townContext, m.configValue)){
                status = new ItemStack(Material.RED_WOOL);
                status.editMeta(itemMeta -> {
                    itemMeta.displayName(mm.deserialize("<red>Your Structure has missing upkeep!</red>"));
                });
                break;
            }else{
                status = new ItemStack(Material.LIME_WOOL);
                status.editMeta(itemMeta -> {
                    itemMeta.displayName(mm.deserialize("<green>Your Structure is running smoothly.</green>"));
                });
            }


        }

        map.put("Status", List.of(new GuiCommand(status, e -> {
            e.setCancelled(true);
            if(e.isLeftClick()){
                ChestGui chestGui = upkeepGui(region);
                chestGui.show(e.getWhoClicked());
            }else if(e.isRightClick()){
                ChestGui chestGui = productionGui(region);
                chestGui.show(e.getWhoClicked());
            }

        })));

        ItemStack upgrade = new ItemStack(Material.AIR);

        // Check if structure has an upgrade path defined
        String upgradePath = townContext.loadedStructure.structureDef.upgradePath;
        if (upgradePath != null && !upgradePath.isEmpty()) {
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
        }

        map.put("Upgrade", List.of(new GuiCommand(upgrade, e -> {
            e.setCancelled(true);
            String path = townContext.loadedStructure.structureDef.upgradePath;
            if (path == null || path.isEmpty()) {
                return; // No upgrade path defined
            }
            if(e.isLeftClick()){
                ChestGui chestGui = upgradeRequirementsGui(region, townContext);
                chestGui.show(e.getWhoClicked());
            } else if(e.isRightClick()){
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
            display((Player) e.getWhoClicked(), region);
        }), 0, 3);

        int x = 0;
        int y = 0;

        List<Structure.LoadedPair<Mechanic<Object>, Object>> upkeep = region.loadedStructure.structureDef.upkeep;

        List<Structure.LoadedPair<Mechanic<Object>, Object>> production = region.loadedStructure.structureDef.production;

        for (Structure.LoadedPair<Mechanic<Object>, Object> m : upkeep) {
            String mechanicId = m.mechanic.id();

            if (mechanicId.equals(cz.neumimto.towny.townycivs.mechanics.Mechanics.UPKEEP)) {
                cz.neumimto.towny.townycivs.mechanics.common.ItemList itemList =
                    (cz.neumimto.towny.townycivs.mechanics.common.ItemList) m.configValue;

                boolean requireAll = itemList.requireAll != null ? itemList.requireAll : true;

                if (!requireAll) {
                    ItemStack bundleStack = new ItemStack(Material.BUNDLE);
                    bundleStack.editMeta(itemMeta -> {
                        BundleMeta bundleMeta = (BundleMeta) itemMeta;
                        bundleMeta.displayName(mm.deserialize("<aqua>Alternative Options (ANY ONE)</aqua>"));
                        var lore = new ArrayList<Component>();
                        lore.add(mm.deserialize("<gray>Any one of these items works:</gray>"));

                        // Add each item to the bundle
                        for (cz.neumimto.towny.townycivs.mechanics.common.ItemList.ConfigItem configItem : itemList.configItems) {
                            ItemStack itemStack = configItem.toItemStack();
                            bundleMeta.addItem(itemStack);

                            // Add info to lore
                            String itemName = itemStack.getType().name();
                            if (configItem.consumeItem != null && configItem.consumeItem) {
                                int amount = configItem.consumeAmount != null ? configItem.consumeAmount : 1;
                                lore.add(mm.deserialize("<white>• " + itemName + " <red>(Consumed: " + amount + ")</red></white>"));
                            } else if (configItem.damageAmount != null) {
                                lore.add(mm.deserialize("<white>• " + itemName + " <gold>(Damage: " + configItem.damageAmount + ")</gold></white>"));
                            } else {
                                lore.add(mm.deserialize("<white>• " + itemName + "</white>"));
                            }
                        }
                        bundleMeta.lore(lore);
                    });
                    staticPane.addItem(new GuiItem(bundleStack, e -> e.setCancelled(true)), x, y);
                    x++;
                    if (x == 9) {
                        x = 0;
                        y++;
                    }
                } else {
                    for (cz.neumimto.towny.townycivs.mechanics.common.ItemList.ConfigItem configItem : itemList.configItems) {
                        ItemStack itemStack = configItem.toItemStack();
                        itemStack.editMeta(itemMeta -> {
                            var lore = new ArrayList<Component>();
                            lore.add(mm.deserialize("<yellow>Required Item</yellow>"));
                            if (configItem.consumeItem != null && configItem.consumeItem) {
                                lore.add(mm.deserialize("<red>Consumed: " + (configItem.consumeAmount != null ? configItem.consumeAmount : 1) + "x</red>"));
                            }
                            if (configItem.damageAmount != null) {
                                lore.add(mm.deserialize("<gold>Damage: " + configItem.damageAmount + "</gold>"));
                            }
                            itemMeta.lore(lore);
                        });
                        staticPane.addItem(new GuiItem(itemStack, e -> e.setCancelled(true)), x, y);
                        x++;
                        if (x == 9) {
                            x = 0;
                            y++;
                        }
                    }
                }
            }

            if (mechanicId.equals(cz.neumimto.towny.townycivs.mechanics.Mechanics.TOWN_UPKEEP)) {
                // MoneyUpkeep - show as paper item
                cz.neumimto.towny.townycivs.mechanics.common.DoubleWrapper doubleWrapper = (cz.neumimto.towny.townycivs.mechanics.common.DoubleWrapper) m.configValue;

                ItemStack moneyItem = new ItemStack(Material.PAPER);
                moneyItem.editMeta(itemMeta -> {
                    itemMeta.displayName(mm.deserialize("<gold>Money Upkeep</gold>"));
                    var lore = new ArrayList<Component>();
                    lore.add(mm.deserialize("<yellow>Cost: $" + doubleWrapper.value + "</yellow>"));
                    itemMeta.lore(lore);
                });
                staticPane.addItem(new GuiItem(moneyItem, e -> e.setCancelled(true)), x, y);
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
            display((Player) e.getWhoClicked(), region);
        }), 0, 3);

        int x = 0;
        int y = 0;

        List<Structure.LoadedPair<Mechanic<Object>, Object>> production = region.loadedStructure.structureDef.production;

        for (Structure.LoadedPair<Mechanic<Object>, Object> m : production) {
            String mechanicId = m.mechanic.id();

            if (mechanicId.equals(cz.neumimto.towny.townycivs.mechanics.Mechanics.ITEM_PRODUCTION)) {
                // ItemProduction - show items that will be produced
                cz.neumimto.towny.townycivs.mechanics.common.ItemList itemList =
                    (cz.neumimto.towny.townycivs.mechanics.common.ItemList) m.configValue;

                for (cz.neumimto.towny.townycivs.mechanics.common.ItemList.ConfigItem configItem : itemList.configItems) {
                    ItemStack itemStack = configItem.toItemStack();
                    itemStack.editMeta(itemMeta -> {
                        var lore = new ArrayList<Component>();
                        lore.add(mm.deserialize("<yellow>Produced Item</yellow>"));
                        itemMeta.lore(lore);
                    });
                    staticPane.addItem(new GuiItem(itemStack, e -> e.setCancelled(true)), x, y);
                    x++;
                    if (x == 9) {
                        x = 0;
                        y++;
                    }
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

        for (Structure.LoadedPair<Mechanic<?>, ?> req : upgradeReqs) {
            String mechanicId = req.mechanic.id();

            // Skip the upgrade mechanic since we now use upgradePath field
            if (mechanicId.equals(cz.neumimto.towny.townycivs.mechanics.Mechanics.UPGRADE)) {
                continue;
            }

            @SuppressWarnings("unchecked")
            Mechanic<Object> mechanic = (Mechanic<Object>) req.mechanic;
            boolean isMet = mechanic.check(townContext, req.configValue);

            ItemStack reqItem;

            // Price requirement
            if (mechanicId.equals(cz.neumimto.towny.townycivs.mechanics.Mechanics.PRICE)) {
                cz.neumimto.towny.townycivs.mechanics.common.DoubleWrapper price =
                    (cz.neumimto.towny.townycivs.mechanics.common.DoubleWrapper) req.configValue;

                reqItem = new ItemStack(isMet ? Material.GOLD_INGOT : Material.COAL);
                reqItem.editMeta(meta -> {
                    meta.displayName(mm.deserialize(isMet ? "<green>✓ Price</green>" : "<red>✗ Price</red>"));
                    var lore = new ArrayList<Component>();
                    lore.add(mm.deserialize("<yellow>Cost: $" + price.value + "</yellow>"));
                    double balance = townContext.town.getAccount().getHoldingBalance();
                    lore.add(mm.deserialize("<gray>Town Balance: $" + String.format("%.2f", balance) + "</gray>"));
                    lore.add(isMet ? mm.deserialize("<green>Requirement met!</green>") : mm.deserialize("<red>Not enough funds!</red>"));
                    meta.lore(lore);
                });
            }
            // Town level requirement
            else if (mechanicId.equals(cz.neumimto.towny.townycivs.mechanics.Mechanics.TOWN_RANK) ||
                     mechanicId.equals(cz.neumimto.towny.townycivs.mechanics.Mechanics.TOWN_RANK_REQUIREMENT)) {
                cz.neumimto.towny.townycivs.mechanics.common.DoubleWrapper level =
                    (cz.neumimto.towny.townycivs.mechanics.common.DoubleWrapper) req.configValue;

                reqItem = new ItemStack(isMet ? Material.EXPERIENCE_BOTTLE : Material.GLASS_BOTTLE);
                reqItem.editMeta(meta -> {
                    meta.displayName(mm.deserialize(isMet ? "<green>✓ Town Level</green>" : "<red>✗ Town Level</red>"));
                    var lore = new ArrayList<Component>();
                    lore.add(mm.deserialize("<yellow>Required Level: " + (int) level.value + "</yellow>"));
                    lore.add(isMet ? mm.deserialize("<green>Requirement met!</green>") : mm.deserialize("<red>Town level too low!</red>"));
                    meta.lore(lore);
                });
            }
            // Permission requirement
            else if (mechanicId.equals(cz.neumimto.towny.townycivs.mechanics.Mechanics.PERMISSION)) {
                cz.neumimto.towny.townycivs.mechanics.common.StringWrapper perm =
                    (cz.neumimto.towny.townycivs.mechanics.common.StringWrapper) req.configValue;

                reqItem = new ItemStack(isMet ? Material.NAME_TAG : Material.BARRIER);
                reqItem.editMeta(meta -> {
                    meta.displayName(mm.deserialize(isMet ? "<green>✓ Permission</green>" : "<red>✗ Permission</red>"));
                    var lore = new ArrayList<Component>();
                    lore.add(mm.deserialize("<yellow>Required: " + perm.value + "</yellow>"));
                    lore.add(isMet ? mm.deserialize("<green>You have permission!</green>") : mm.deserialize("<red>Missing permission!</red>"));
                    meta.lore(lore);
                });
            }
            // Structure requirement
            else if (mechanicId.equals(cz.neumimto.towny.townycivs.mechanics.Mechanics.STRUCTURE)) {
                cz.neumimto.towny.townycivs.mechanics.common.StringWrapper structureReq =
                    (cz.neumimto.towny.townycivs.mechanics.common.StringWrapper) req.configValue;

                reqItem = new ItemStack(isMet ? Material.SMITHING_TABLE : Material.CRAFTING_TABLE);
                reqItem.editMeta(meta -> {
                    meta.displayName(mm.deserialize(isMet ? "<green>✓ Structure Required</green>" : "<red>✗ Structure Required</red>"));
                    var lore = new ArrayList<Component>();
                    lore.add(mm.deserialize("<yellow>Requires: " + structureReq.value + "</yellow>"));
                    lore.add(isMet ? mm.deserialize("<green>Structure exists!</green>") : mm.deserialize("<red>Build this structure first!</red>"));
                    meta.lore(lore);
                });
            }
            // Generic mechanic
            else {
                reqItem = new ItemStack(isMet ? Material.LIME_DYE : Material.RED_DYE);
                reqItem.editMeta(meta -> {
                    meta.displayName(mm.deserialize(isMet ? "<green>✓ " + mechanicId + "</green>" : "<red>✗ " + mechanicId + "</red>"));
                    var lore = new ArrayList<Component>();
                    lore.add(mm.deserialize("<gray>Mechanic: " + mechanicId + "</gray>"));
                    lore.add(isMet ? mm.deserialize("<green>Requirement met!</green>") : mm.deserialize("<red>Requirement not met!</red>"));
                    meta.lore(lore);
                });
            }

            staticPane.addItem(new GuiItem(reqItem, e -> e.setCancelled(true)), x, y);
            x++;
            if (x == 9) {
                x = 0;
                y++;
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
