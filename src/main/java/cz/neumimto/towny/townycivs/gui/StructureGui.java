package cz.neumimto.towny.townycivs.gui;

import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Town;
import cz.neumimto.towny.townycivs.*;
import cz.neumimto.towny.townycivs.config.ConfigurationService;
import cz.neumimto.towny.townycivs.config.Structure;
import cz.neumimto.towny.townycivs.db.Storage;
import cz.neumimto.towny.townycivs.gui.api.GuiCommand;
import cz.neumimto.towny.townycivs.gui.api.GuiConfig;
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
public class StructureGui extends TCGui {
    @Inject
    private SubclaimService subclaimService;

    @Inject
    private ConfigurationService configurationService;

    @Inject
    private StructureService structureService;

    @Inject
    private ManagementService managementService;

    public StructureGui() {
        super("Region.conf", TownyCivs.INSTANCE.getDataFolder().toPath());
    }

    @Override
    protected String getTitle(CommandSender commandSender, GuiConfig guiConfig, String param) {
        Town town = TownyAPI.getInstance().getResident((Player) commandSender).getTownOrNull();
        Optional<LoadedStructure> structure = structureService.findStructureByUUID(UUID.fromString(param));
        return town.getName() + " - " + structure.get().structureDef.name;
    }

    public void display(Player player, String structureId) {
        ChestGui chestGui = loadGui(player, structureId);
        chestGui.show(player);
    }

    @Override
    public Map<String, List<GuiCommand>> getPaneData(CommandSender commandSender, String param) {
        Player player = (Player) commandSender;
        Town town = TownyAPI.getInstance().getResident(player).getTownOrNull();
        Map<String, List<GuiCommand>> map = new HashMap<>();
        TownContext townContext = new TownContext();
        townContext.town = town;

        UUID structureUUID = UUID.fromString(param);
        Optional<LoadedStructure> structureOpt = structureService.findStructureByUUID(structureUUID);

        if (structureOpt.isEmpty()) {
            commandSender.sendMessage(Component.text("§cError: Structure not found for ID" + structureUUID));
            return Collections.emptyMap(); // stop early
        }

        LoadedStructure structure = structureOpt.get();
        Region region = subclaimService.getRegion(structureUUID);

        if (region == null) {
            commandSender.sendMessage(Component.text("§cError: Region not found for structure ID" + structureUUID));
            return Collections.emptyMap();
        }

        townContext.structure = structure.structureDef;
        townContext.loadedStructure = structure;




        StructureAndCount count = structureService.findTownStructureById(town, structure.structureDef);
        ItemStack structureInfoStack = structureService.toItemStack(structure.structureDef, count.count);

        map.put("Structure", List.of(new GuiCommand(structureInfoStack, e -> e.setCancelled(true))));

        MiniMessage mm = MiniMessage.miniMessage();
        ItemStack editMode = new ItemStack(managementService.isBeingEdited(structure) ? Material.RED_WOOL : Material.GREEN_WOOL);
        editMode.editMeta(itemMeta -> {
            var lore = new ArrayList<Component>();

            String editModeS = null;
            if (managementService.isBeingEdited(structure)) {
                editModeS = "<red>Active<red>";
            } else {
                editModeS = "<green>Inactive<green>";
            }
            itemMeta.displayName(mm.deserialize("<gold>Edit mode</gold> : " + editModeS + " You are in Structure GUI mode, cannot edit the region directly."));

            lore.add(Component.empty());
            lore.add(mm.deserialize("<red>Active<red><white>- structure is disabled & its region may be edited</white>"));
            lore.add(mm.deserialize("<green>Inactive<green><white>- structure is enabled & its region may not be edited</white>"));
            itemMeta.lore(lore);
        });
        map.put("EditModeToggle", List.of(new GuiCommand(editMode, e -> {
            e.setCancelled(true);
        })));

        ItemStack delete = new ItemStack(Material.BARRIER);
        delete.editMeta(itemMeta -> {
            itemMeta.displayName(mm.deserialize("<red>Cannot delete as you are in Structure GUI mode.</red>"));
        });
        map.put("Delete", List.of(new GuiCommand(delete, e -> {
            e.setCancelled(true);
        })));


        ItemStack remBlocks = new ItemStack(Material.IRON_AXE);
        remBlocks.editMeta(itemMeta -> {
            itemMeta.displayName(mm.deserialize("<yellow>Remaining build requirements</yellow>"));
            itemMeta.addItemFlags(ItemFlag.values());
        });
        map.put("RemainingBlocks", List.of(new GuiCommand(remBlocks, e -> {
            e.setCancelled(true);
            ChestGui chestGui = remainingBlocksGui(subclaimService.getRegion(structure));
            chestGui.show(e.getWhoClicked());
        })));

        ItemStack location = new ItemStack(Material.COMPASS);
        Location loc = subclaimService.getRegion(structure).loadedStructure.center;
        location.editMeta(itemMeta -> {
            itemMeta.displayName(mm.deserialize("<aqua>Location: " + "X: " + loc.getBlockX() + " Y: " + loc.getBlockY() + " Z: " + loc.getBlockZ() + "</aqua>"));
        });
        map.put("Location", List.of(new GuiCommand(location, e -> e.setCancelled(true))));

        ItemStack status = null;
        List<Structure.LoadedPair<Mechanic<Object>, Object>> upkeep = structure.structureDef.upkeep;
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

        map.put("Status", List.of(new GuiCommand(status, e -> e.setCancelled(true))));
        return map;
    }

    @NotNull
    private ChestGui remainingBlocksGui(Region region) {
        MiniMessage mm = MiniMessage.miniMessage();
        ChestGui chestGui = new ChestGui(6, "Remaining Blocks");

        StaticPane staticPane = new StaticPane(9, 6);
        chestGui.addPane(staticPane);
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
}
