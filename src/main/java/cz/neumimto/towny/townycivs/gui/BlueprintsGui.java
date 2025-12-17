package cz.neumimto.towny.townycivs.gui;

import com.github.stefvanschie.inventoryframework.adventuresupport.ComponentHolder;
import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.gui.type.AnvilGui;
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui;
import com.github.stefvanschie.inventoryframework.pane.OutlinePane;
import com.github.stefvanschie.inventoryframework.pane.Pane;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Town;
import cz.neumimto.towny.townycivs.ManagementService;
import cz.neumimto.towny.townycivs.StructureService;
import cz.neumimto.towny.townycivs.TownyCivs;
import cz.neumimto.towny.townycivs.config.ConfigurationService;
import cz.neumimto.towny.townycivs.config.Structure;
import cz.neumimto.towny.townycivs.mechanics.Mechanic;
import cz.neumimto.towny.townycivs.mechanics.Mechanics;
import cz.neumimto.towny.townycivs.mechanics.TownContext;
import cz.neumimto.towny.townycivs.mechanics.common.DoubleWrapper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;
import java.util.stream.Collectors;

@Singleton
public class BlueprintsGui {

    private static final int ITEMS_PER_PAGE = 28; // 7x4 grid (slots 1-7 on rows 2-5)
    private static final int GUI_ROWS = 6;

    @Inject
    private StructureService structureService;

    @Inject
    private ConfigurationService configurationService;

    @Inject
    private ManagementService managementService;

    public void display(Player player) {
        BlueprintGuiSession session = BlueprintGuiSession.getSession(player.getUniqueId());
        createAndShowGui(player, session);
    }

    /**
     * Clears all player sessions. Called during reload.
     */
    public void clearCache() {
        BlueprintGuiSession.clearAllSessions();
    }

    /**
     * Clears the session for a specific player.
     */
    public void clearCache(java.util.UUID uuid) {
        BlueprintGuiSession.clearSession(uuid);
    }

    /**
     * Reloads the GUI configuration. Since this GUI is dynamically generated,
     * this method just clears all sessions to reset player state.
     */
    public void reloadGuiConfig() {
        // This GUI is dynamically generated, no config file to reload
        // Just clear sessions so players get fresh state
        clearCache();
    }

    private void createAndShowGui(Player player, BlueprintGuiSession session) {
        var resident = TownyAPI.getInstance().getResident(player);
        if (resident == null) {
            player.sendMessage(Component.text("You are not registered as a resident!", NamedTextColor.RED));
            return;
        }

        Town town = resident.getTownOrNull();
        if (town == null) {
            player.sendMessage(Component.text("You must be in a town to access blueprints!", NamedTextColor.RED));
            return;
        }

        // Get and filter/sort structures
        List<StructureEntry> structures = getFilteredAndSortedStructures(player, town, session);

        int totalPages = Math.max(1, (int) Math.ceil((double) structures.size() / ITEMS_PER_PAGE));

        // Ensure current page is valid
        if (session.getCurrentPage() >= totalPages) {
            session.setCurrentPage(totalPages - 1);
        }

        // Create title
        String title = createTitle(town, session, totalPages);
        ChestGui gui = new ChestGui(GUI_ROWS, ComponentHolder.of(MiniMessage.miniMessage().deserialize(title)));
        gui.setOnGlobalClick(event -> event.setCancelled(true));

        // Create border pane
        OutlinePane borderPane = new OutlinePane(0, 0, 9, GUI_ROWS, Pane.Priority.LOWEST);
        borderPane.addItem(new GuiItem(createBorderItem()));
        borderPane.setRepeat(true);
        gui.addPane(borderPane);

        // Create content pane for blueprints (7x4 grid in the middle)
        StaticPane contentPane = new StaticPane(1, 1, 7, 4, Pane.Priority.NORMAL);

        int startIndex = session.getCurrentPage() * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, structures.size());

        int slot = 0;
        for (int i = startIndex; i < endIndex; i++) {
            StructureEntry entry = structures.get(i);
            int x = slot % 7;
            int y = slot / 7;

            GuiItem guiItem = createBlueprintItem(player, town, entry);
            contentPane.addItem(guiItem, x, y);
            slot++;
        }
        gui.addPane(contentPane);

        // Create control pane (bottom row)
        StaticPane controlPane = new StaticPane(0, 5, 9, 1, Pane.Priority.HIGH);

        // Back button (slot 0)
        controlPane.addItem(createBackButton(player, session, totalPages), 0, 0);

        // Search button (slot 1) - Spyglass
        controlPane.addItem(createSearchButton(player, session), 1, 0);

        // Clear search button (slot 2) - only if search is active
        if (session.hasSearchFilter()) {
            controlPane.addItem(createClearSearchButton(player, session), 2, 0);
        }

        // Toggle sort mode button (slot 4 - center)
        controlPane.addItem(createToggleButton(player, session), 4, 0);

        // Page info (slot 6)
        controlPane.addItem(createPageInfoItem(session.getCurrentPage() + 1, totalPages), 6, 0);

        // Next button (slot 8)
        controlPane.addItem(createNextButton(player, session, totalPages), 8, 0);

        gui.addPane(controlPane);

        gui.show(player);
    }

    private String createTitle(Town town, BlueprintGuiSession session, int totalPages) {
        StringBuilder title = new StringBuilder();
        title.append("<white>").append(town.getName()).append(" - Blueprints");

        if (session.hasSearchFilter()) {
            title.append(" <gray>[Search: ").append(session.getSearchFilter()).append("]");
        }

        return title.toString();
    }

    private List<StructureEntry> getFilteredAndSortedStructures(Player player, Town town, BlueprintGuiSession session) {
        List<StructureEntry> entries = new ArrayList<>();

        for (Structure structure : configurationService.getAll()) {
            TownContext townContext = new TownContext();
            townContext.town = town;
            townContext.resident = TownyAPI.getInstance().getResident(player);
            townContext.player = player;
            townContext.structure = structure;

            boolean canShow = structureService.canShow(townContext);
            int requiredLevel = getRequiredTownLevel(structure);
            int buildCount = structureService.findTownStructureById(town, structure).count;

            entries.add(new StructureEntry(structure, canShow, requiredLevel, buildCount));
        }

        // Apply search filter
        if (session.hasSearchFilter()) {
            String filter = session.getSearchFilter();
            entries = entries.stream()
                    .filter(e -> e.structure.name.toLowerCase().contains(filter) ||
                                 e.structure.id.toLowerCase().contains(filter))
                    .collect(Collectors.toList());
        }

        // Apply sort mode filter/sort
        BlueprintSortMode sortMode = session.getSortMode();

        switch (sortMode) {
            case AVAILABLE:
                entries = entries.stream()
                        .filter(e -> e.canBuy)
                        .sorted(Comparator.comparing(e -> e.structure.name))
                        .collect(Collectors.toList());
                break;
            case ALL:
                entries.sort(Comparator.comparing(e -> e.structure.name));
                break;
            case ALPHABETICAL:
                entries.sort(Comparator.comparing(e -> e.structure.name));
                break;
            case REVERSE_ALPHABETICAL:
                entries.sort((a, b) -> b.structure.name.compareTo(a.structure.name));
                break;
            case LEVEL_ASCENDING:
                entries.sort(Comparator.comparingInt(e -> e.requiredLevel));
                break;
            case LEVEL_DESCENDING:
                entries.sort((a, b) -> Integer.compare(b.requiredLevel, a.requiredLevel));
                break;
            default:
                // Level filters (LEVEL_1 through LEVEL_9)
                if (sortMode.isLevelFilter()) {
                    int levelFilter = sortMode.getLevelFilter();
                    entries = entries.stream()
                            .filter(e -> e.requiredLevel == levelFilter)
                            .sorted(Comparator.comparing(e -> e.structure.name))
                            .collect(Collectors.toList());
                }
                break;
        }

        return entries;
    }

    private int getRequiredTownLevel(Structure structure) {
        if (structure.buyRequirements == null) {
            return 1;
        }

        for (Structure.LoadedPair<Mechanic<?>, ?> requirement : structure.buyRequirements) {
            if (Mechanics.TOWN_RANK.equals(requirement.mechanic.id())) {
                if (requirement.configValue instanceof DoubleWrapper) {
                    return (int) ((DoubleWrapper) requirement.configValue).value;
                }
            }
        }
        return 1; // Default to level 1 if no level requirement found
    }

    private GuiItem createBlueprintItem(Player player, Town town, StructureEntry entry) {
        ItemStack itemStack = structureService.toItemStack(entry.structure, entry.buildCount);

        // Add availability indicator to lore
        ItemMeta meta = itemStack.getItemMeta();
        List<Component> lore = meta.lore();
        if (lore == null) {
            lore = new ArrayList<>();
        }

        lore.add(Component.empty());
        if (entry.canBuy) {
            lore.add(Component.text("✓ Click to purchase", NamedTextColor.GREEN));
        } else {
            lore.add(Component.text("✗ Requirements not met", NamedTextColor.RED));
        }

        if (entry.requiredLevel > 1) {
            lore.add(Component.text("Required Town Level: " + entry.requiredLevel, NamedTextColor.YELLOW));
        }

        meta.lore(lore);
        itemStack.setItemMeta(meta);

        return new GuiItem(itemStack, event -> {
            event.setCancelled(true);

            TownContext townContext = new TownContext();
            townContext.town = town;
            townContext.resident = TownyAPI.getInstance().getResident(player);
            townContext.player = player;
            townContext.structure = entry.structure;

            if (structureService.canBuy(townContext)) {
                ItemStack clone = structureService.buyBlueprint(townContext);
                HumanEntity whoClicked = event.getWhoClicked();
                HashMap<Integer, ItemStack> noFitItems = whoClicked.getInventory().addItem(clone);
                for (Map.Entry<Integer, ItemStack> e : noFitItems.entrySet()) {
                    whoClicked.getLocation().getWorld().dropItemNaturally(whoClicked.getLocation(), e.getValue());
                }
                // Refresh the GUI
                BlueprintGuiSession session = BlueprintGuiSession.getSession(player.getUniqueId());
                createAndShowGui(player, session);
            }
        });
    }

    private ItemStack createBorderItem() {
        ItemStack item = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(" "));
        item.setItemMeta(meta);
        return item;
    }

    private GuiItem createBackButton(Player player, BlueprintGuiSession session, int totalPages) {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();

        if (session.getCurrentPage() > 0) {
            meta.displayName(Component.text("◀ Previous Page", NamedTextColor.GREEN));
            meta.lore(List.of(
                    Component.text("Click to go to page " + session.getCurrentPage(), NamedTextColor.GRAY)
            ));
        } else {
            meta.displayName(Component.text("◀ Previous Page", NamedTextColor.GRAY));
            meta.lore(List.of(
                    Component.text("You are on the first page", NamedTextColor.GRAY)
            ));
        }

        item.setItemMeta(meta);

        return new GuiItem(item, event -> {
            event.setCancelled(true);
            if (session.getCurrentPage() > 0) {
                session.previousPage();
                createAndShowGui(player, session);
            }
        });
    }

    private GuiItem createNextButton(Player player, BlueprintGuiSession session, int totalPages) {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();

        if (session.getCurrentPage() < totalPages - 1) {
            meta.displayName(Component.text("Next Page ▶", NamedTextColor.GREEN));
            meta.lore(List.of(
                    Component.text("Click to go to page " + (session.getCurrentPage() + 2), NamedTextColor.GRAY)
            ));
        } else {
            meta.displayName(Component.text("Next Page ▶", NamedTextColor.GRAY));
            meta.lore(List.of(
                    Component.text("You are on the last page", NamedTextColor.GRAY)
            ));
        }

        item.setItemMeta(meta);

        return new GuiItem(item, event -> {
            event.setCancelled(true);
            if (session.getCurrentPage() < totalPages - 1) {
                session.nextPage();
                createAndShowGui(player, session);
            }
        });
    }

    private GuiItem createSearchButton(Player player, BlueprintGuiSession session) {
        ItemStack item = new ItemStack(Material.SPYGLASS);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("🔍 Search Blueprints", NamedTextColor.YELLOW));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Click to search for blueprints", NamedTextColor.GRAY));
        if (session.hasSearchFilter()) {
            lore.add(Component.empty());
            lore.add(Component.text("Current filter: ", NamedTextColor.WHITE)
                    .append(Component.text(session.getSearchFilter(), NamedTextColor.AQUA)));
        }
        meta.lore(lore);

        item.setItemMeta(meta);

        return new GuiItem(item, event -> {
            event.setCancelled(true);
            openSearchGui(player, session);
        });
    }

    private GuiItem createClearSearchButton(Player player, BlueprintGuiSession session) {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("✗ Clear Search", NamedTextColor.RED));
        meta.lore(List.of(
                Component.text("Current: " + session.getSearchFilter(), NamedTextColor.GRAY),
                Component.text("Click to clear search filter", NamedTextColor.YELLOW)
        ));
        item.setItemMeta(meta);

        return new GuiItem(item, event -> {
            event.setCancelled(true);
            session.clearSearchFilter();
            createAndShowGui(player, session);
        });
    }

    private GuiItem createToggleButton(Player player, BlueprintGuiSession session) {
        ItemStack item = new ItemStack(Material.COMPASS);
        ItemMeta meta = item.getItemMeta();

        BlueprintSortMode currentMode = session.getSortMode();
        BlueprintSortMode nextMode = currentMode.next();

        meta.displayName(Component.text("⚙ Sort: " + currentMode.getDisplayName(), NamedTextColor.GREEN));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(currentMode.getDescription(), NamedTextColor.GRAY));
        lore.add(Component.empty());
        lore.add(Component.text("Left-click: ", NamedTextColor.WHITE)
                .append(Component.text("Next mode (" + nextMode.getDisplayName() + ")", NamedTextColor.YELLOW)));
        lore.add(Component.text("Right-click: ", NamedTextColor.WHITE)
                .append(Component.text("Previous mode", NamedTextColor.YELLOW)));
        lore.add(Component.empty());
        lore.add(Component.text("Available modes:", NamedTextColor.AQUA));

        for (BlueprintSortMode mode : BlueprintSortMode.values()) {
            if (mode == currentMode) {
                lore.add(Component.text(" ▶ " + mode.getDisplayName(), NamedTextColor.GREEN));
            } else {
                lore.add(Component.text("   " + mode.getDisplayName(), NamedTextColor.GRAY));
            }
        }

        meta.lore(lore);
        item.setItemMeta(meta);

        return new GuiItem(item, event -> {
            event.setCancelled(true);
            if (event.isRightClick()) {
                session.setSortMode(currentMode.previous());
            } else {
                session.cycleSortMode();
            }
            createAndShowGui(player, session);
        });
    }

    private GuiItem createPageInfoItem(int currentPage, int totalPages) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Page " + currentPage + "/" + totalPages, NamedTextColor.WHITE));
        item.setItemMeta(meta);

        return new GuiItem(item, event -> event.setCancelled(true));
    }

    private void openSearchGui(Player player, BlueprintGuiSession session) {
        AnvilGui anvilGui = new AnvilGui(ComponentHolder.of(
                Component.text("Search Blueprints", NamedTextColor.DARK_PURPLE)));

        // First slot - search icon with current query
        StaticPane firstPane = new StaticPane(0, 0, 1, 1);
        ItemStack searchItem = new ItemStack(Material.PAPER);
        ItemMeta searchMeta = searchItem.getItemMeta();
        searchMeta.displayName(Component.text(session.hasSearchFilter() ? session.getSearchFilter() : "Type to search...", NamedTextColor.WHITE));
        searchItem.setItemMeta(searchMeta);
        firstPane.addItem(new GuiItem(searchItem, e -> e.setCancelled(true)), 0, 0);
        anvilGui.getFirstItemComponent().addPane(firstPane);

        // Result slot - confirm button
        StaticPane resultPane = new StaticPane(0, 0, 1, 1);
        ItemStack confirmItem = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
        ItemMeta confirmMeta = confirmItem.getItemMeta();
        confirmMeta.displayName(Component.text("✓ Click to Search", NamedTextColor.GREEN));
        confirmMeta.lore(List.of(
                Component.text("Click to apply your search", NamedTextColor.GRAY)
        ));
        confirmItem.setItemMeta(confirmMeta);

        resultPane.addItem(new GuiItem(confirmItem, event -> {
            event.setCancelled(true);
            String searchText = anvilGui.getRenameText();
            if (searchText != null && !searchText.isEmpty() && !searchText.equals("Type to search...")) {
                session.setSearchFilter(searchText);
            }
            createAndShowGui(player, session);
        }), 0, 0);
        anvilGui.getResultComponent().addPane(resultPane);

        anvilGui.setOnClose(event -> {
            // Return to main GUI when anvil is closed
            TownyCivs.MORE_PAPER_LIB.scheduling().entitySpecificScheduler(player)
                    .run(() -> createAndShowGui(player, session), null);
        });

        anvilGui.show(player);
    }

    /**
     * Helper class to hold structure data with computed fields.
     */
    private static class StructureEntry {
        final Structure structure;
        final boolean canBuy;
        final int requiredLevel;
        final int buildCount;

        StructureEntry(Structure structure, boolean canBuy, int requiredLevel, int buildCount) {
            this.structure = structure;
            this.canBuy = canBuy;
            this.requiredLevel = requiredLevel;
            this.buildCount = buildCount;
        }
    }
}
