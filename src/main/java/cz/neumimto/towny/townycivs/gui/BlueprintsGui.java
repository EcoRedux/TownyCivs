package cz.neumimto.towny.townycivs.gui;

import com.electronwill.nightconfig.core.conversion.ObjectConverter;
import com.electronwill.nightconfig.core.file.FileConfig;
import com.electronwill.nightconfig.hocon.HoconParser;
import com.github.stefvanschie.inventoryframework.adventuresupport.ComponentHolder;
import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.gui.type.AnvilGui;
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import com.github.stefvanschie.inventoryframework.pane.Pane;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Town;
import cz.neumimto.towny.townycivs.ManagementService;
import cz.neumimto.towny.townycivs.StructureService;
import cz.neumimto.towny.townycivs.TownyCivs;
import cz.neumimto.towny.townycivs.config.ConfigurationService;
import cz.neumimto.towny.townycivs.config.Structure;
import cz.neumimto.towny.townycivs.gui.api.GuiConfig;
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
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

@Singleton
public class BlueprintsGui {

    private static final String CONFIG_FILE = "BuyBlueprints.conf";

    @Inject
    private StructureService structureService;

    @Inject
    private ConfigurationService configurationService;

    @Inject
    private ManagementService managementService;

    @Inject
    private cz.neumimto.towny.townycivs.tutorial.TutorialManager tutorialManager;

    private GuiConfig guiConfig;
    private int itemsPerPage = 28;
    private List<int[]> contentSlots = new ArrayList<>();
    private Map<String, int[]> controlSlots = new HashMap<>();

    public void display(Player player) {
        BlueprintGuiSession session = BlueprintGuiSession.getSession(player.getUniqueId());
        loadConfigIfNeeded();

        // Track tutorial progress - shop opened
        com.palmergames.bukkit.towny.object.Resident resident = com.palmergames.bukkit.towny.TownyAPI.getInstance().getResident(player);
        if (resident != null && resident.hasTown() && resident.isMayor()) {
            com.palmergames.bukkit.towny.object.Town town = resident.getTownOrNull();
            if (town != null) {
                tutorialManager.onShopOpened(town, player);
            }
        }

        createAndShowGui(player, session);
    }

    /**
     * Clears all player sessions. Called during reload.
     */
    public void clearCache() {
        BlueprintGuiSession.clearAllSessions();
        guiConfig = null;
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
        guiConfig = null;
        loadConfigIfNeeded();
    }

    private void loadConfigIfNeeded() {
        if (guiConfig != null) return;

        Path configPath = TownyCivs.INSTANCE.getDataFolder().toPath().resolve("guis/" + CONFIG_FILE);

        try {
            if (!Files.exists(configPath)) {
                Path parentDir = configPath.getParent();
                if (parentDir != null && !Files.exists(parentDir)) {
                    Files.createDirectories(parentDir);
                }

                try (InputStream is = getClass().getClassLoader().getResourceAsStream("gui/" + CONFIG_FILE)) {
                    if (is != null) {
                        String content = new String(is.readAllBytes());
                        Files.writeString(configPath, content);
                    }
                }
            }

            if (Files.exists(configPath)) {
                try (FileConfig fileConfig = FileConfig.of(configPath)) {
                    fileConfig.load();
                    guiConfig = new ObjectConverter().toObject(fileConfig, GuiConfig::new);
                }
            } else {
                try (InputStream is = getClass().getClassLoader().getResourceAsStream("gui/" + CONFIG_FILE)) {
                    if (is != null) {
                        String content = new String(is.readAllBytes());
                        HoconParser parser = new HoconParser();
                        try (StringReader reader = new StringReader(content)) {
                            guiConfig = new ObjectConverter().toObject(parser.parse(reader), GuiConfig::new);
                        }
                    }
                }
            }
        } catch (IOException e) {
            TownyCivs.logger.severe("Failed to load Blueprints GUI config: " + e.getMessage());
            e.printStackTrace();
        }

        if (guiConfig != null) {
            parseLayout();
        }
    }

    private void parseLayout() {
        contentSlots.clear();
        controlSlots.clear();

        if (guiConfig == null || guiConfig.inventory == null) return;

        Map<Character, String> charToSupplier = new HashMap<>();
        if (guiConfig.mask != null) {
            for (GuiConfig.MaskConfig mask : guiConfig.mask) {
                if (mask.C != null && !mask.C.isEmpty() && mask.supplier != null) {
                    charToSupplier.put(mask.C.charAt(0), mask.supplier);
                }
            }
        }

        for (int row = 0; row < guiConfig.inventory.size(); row++) {
            String rowStr = guiConfig.inventory.get(row);
            for (int col = 0; col < rowStr.length(); col++) {
                char c = rowStr.charAt(col);
                String supplier = charToSupplier.get(c);

                if (supplier != null) {
                    switch (supplier.toLowerCase()) {
                        case "blueprint":
                            contentSlots.add(new int[]{col, row});
                            break;
                        case "return":
                        case "back":
                        case "next":
                        case "search":
                        case "clearsearch":
                        case "toggle":
                        case "pageinfo":
                            controlSlots.put(supplier.toLowerCase(), new int[]{col, row});
                            break;
                    }
                }
            }
        }

        itemsPerPage = contentSlots.size();
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

        List<StructureEntry> structures = getFilteredAndSortedStructures(player, town, session);
        int totalPages = Math.max(1, (int) Math.ceil((double) structures.size() / Math.max(1, itemsPerPage)));

        if (session.getCurrentPage() >= totalPages) {
            session.setCurrentPage(Math.max(0, totalPages - 1));
        }

        int guiRows = guiConfig != null ? guiConfig.inventory.size() : 6;
        String title = createTitle(town, session, totalPages);
        ChestGui gui = new ChestGui(guiRows, ComponentHolder.of(MiniMessage.miniMessage().deserialize(title)));
        gui.setOnGlobalClick(event -> event.setCancelled(true));

        // Background pane
        StaticPane backgroundPane = new StaticPane(0, 0, 9, guiRows, Pane.Priority.LOWEST);
        fillBackgroundFromConfig(backgroundPane);
        gui.addPane(backgroundPane);

        // Content pane for blueprints
        StaticPane contentPane = new StaticPane(0, 0, 9, guiRows, Pane.Priority.NORMAL);
        int startIndex = session.getCurrentPage() * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, structures.size());

        int slotIdx = 0;
        for (int i = startIndex; i < endIndex && slotIdx < contentSlots.size(); i++) {
            StructureEntry entry = structures.get(i);
            int[] pos = contentSlots.get(slotIdx);
            contentPane.addItem(createBlueprintItem(player, town, entry), pos[0], pos[1]);
            slotIdx++;
        }
        gui.addPane(contentPane);

        // Control pane
        StaticPane controlPane = new StaticPane(0, 0, 9, guiRows, Pane.Priority.HIGH);

        if (controlSlots.containsKey("return")) {
            int[] pos = controlSlots.get("return");
            controlPane.addItem(createReturnButton(player), pos[0], pos[1]);
        }

        if (controlSlots.containsKey("back")) {
            int[] pos = controlSlots.get("back");
            controlPane.addItem(createBackButton(player, session, totalPages), pos[0], pos[1]);
        }

        if (controlSlots.containsKey("search")) {
            int[] pos = controlSlots.get("search");
            controlPane.addItem(createSearchButton(player, session), pos[0], pos[1]);
        }

        if (controlSlots.containsKey("clearsearch") && session.hasSearchFilter()) {
            int[] pos = controlSlots.get("clearsearch");
            controlPane.addItem(createClearSearchButton(player, session), pos[0], pos[1]);
        }

        if (controlSlots.containsKey("toggle")) {
            int[] pos = controlSlots.get("toggle");
            controlPane.addItem(createToggleButton(player, session), pos[0], pos[1]);
        }

        if (controlSlots.containsKey("pageinfo")) {
            int[] pos = controlSlots.get("pageinfo");
            controlPane.addItem(createPageInfoItem(session.getCurrentPage() + 1, totalPages), pos[0], pos[1]);
        }

        if (controlSlots.containsKey("next")) {
            int[] pos = controlSlots.get("next");
            controlPane.addItem(createNextButton(player, session, totalPages), pos[0], pos[1]);
        }

        gui.addPane(controlPane);
        gui.show(player);
    }

    private void fillBackgroundFromConfig(StaticPane pane) {
        if (guiConfig == null || guiConfig.inventory == null) return;

        Map<Character, ItemStack> charToItem = new HashMap<>();
        if (guiConfig.mask != null) {
            for (GuiConfig.MaskConfig mask : guiConfig.mask) {
                if (mask.C != null && !mask.C.isEmpty() && mask.id != null) {
                    if (mask.supplier != null) continue;

                    Material mat = Material.matchMaterial(mask.id);
                    if (mat != null && mat != Material.AIR) {
                        ItemStack item = new ItemStack(mat);
                        item.editMeta(meta -> {
                            if (mask.translationKey != null) {
                                meta.displayName(MiniMessage.miniMessage().deserialize(mask.translationKey));
                            } else {
                                meta.displayName(Component.text(" "));
                            }
                        });
                        charToItem.put(mask.C.charAt(0), item);
                    }
                }
            }
        }

        for (int row = 0; row < guiConfig.inventory.size(); row++) {
            String rowStr = guiConfig.inventory.get(row);
            for (int col = 0; col < rowStr.length(); col++) {
                char c = rowStr.charAt(col);
                ItemStack item = charToItem.get(c);
                if (item != null) {
                    pane.addItem(new GuiItem(item.clone(), e -> e.setCancelled(true)), col, row);
                }
            }
        }
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
            int upgradeChainCount = structureService.countStructuresInUpgradeChain(town, structure);

            entries.add(new StructureEntry(structure, canShow, requiredLevel, buildCount, upgradeChainCount));
        }

        if (session.hasSearchFilter()) {
            String filter = session.getSearchFilter();
            entries = entries.stream()
                    .filter(e -> e.structure.name.toLowerCase().contains(filter) ||
                                 e.structure.id.toLowerCase().contains(filter))
                    .collect(Collectors.toList());
        }

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
        return 1;
    }

    private GuiItem createBlueprintItem(Player player, Town town, StructureEntry entry) {
        ItemStack itemStack = structureService.toItemStack(entry.structure, entry.buildCount);

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

        // Show build count with upgrade chain info
        int upgradedCount = entry.upgradeChainCount - entry.buildCount;

        if (entry.maxBuildCount != Integer.MAX_VALUE) {
            // Has a max count limit
            if (entry.upgradeChainCount >= entry.maxBuildCount) {
                lore.add(Component.text("Max build count reached (" + entry.maxBuildCount + ")", NamedTextColor.RED));
            }

            // Show: Built: 2/5 (base) or Built: 3/5 (2 base + 1 upgraded)
            if (upgradedCount > 0) {
                lore.add(Component.text("Built: " + entry.upgradeChainCount + "/" + entry.maxBuildCount, NamedTextColor.GRAY));
                lore.add(Component.text("  ↳ " + entry.buildCount + " base + " + upgradedCount + " upgraded", NamedTextColor.DARK_GRAY));
                lore.add(Component.text("  ↳ Upgrades share the same slot", NamedTextColor.GOLD));
            } else {
                lore.add(Component.text("Built: " + entry.buildCount + "/" + entry.maxBuildCount, NamedTextColor.GRAY));
            }
        } else {
            // No max count limit
            if (upgradedCount > 0) {
                lore.add(Component.text("Built: " + entry.buildCount + " (+ " + upgradedCount + " upgraded)", NamedTextColor.GRAY));
            } else {
                lore.add(Component.text("Built: " + entry.buildCount, NamedTextColor.GRAY));
            }
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

                // Track tutorial progress - blueprint purchased
                if (townContext.resident != null && townContext.resident.isMayor()) {
                    tutorialManager.onBlueprintPurchased(town, player, entry.structure.id);
                }

                // Refresh the GUI
                BlueprintGuiSession session = BlueprintGuiSession.getSession(player.getUniqueId());
                createAndShowGui(player, session);
            }
        });
    }

    private GuiItem createReturnButton(Player player) {
        ItemStack item = new ItemStack(Material.ARROW);
        item.editMeta(meta -> {
            meta.displayName(Component.text("Return to Main Menu", NamedTextColor.GREEN));
            meta.lore(List.of(Component.text("Click to go back", NamedTextColor.GRAY)));
        });
        return new GuiItem(item, event -> {
            event.setCancelled(true);
            player.closeInventory();
            TownyCivs.MORE_PAPER_LIB.scheduling().entitySpecificScheduler(player)
                    .run(() -> player.performCommand("townycivs"), null);
        });
    }

    private GuiItem createBackButton(Player player, BlueprintGuiSession session, int totalPages) {
        ItemStack item = new ItemStack(Material.ARROW);
        item.editMeta(meta -> {
            boolean canGoBack = session.getCurrentPage() > 0;
            meta.displayName(Component.text("◀ Previous Page", canGoBack ? NamedTextColor.GREEN : NamedTextColor.GRAY));
            meta.lore(List.of(Component.text(canGoBack ? "Click to go to page " + session.getCurrentPage() : "You are on the first page", NamedTextColor.GRAY)));
        });
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
        item.editMeta(meta -> {
            boolean canGoNext = session.getCurrentPage() < totalPages - 1;
            meta.displayName(Component.text("Next Page ▶", canGoNext ? NamedTextColor.GREEN : NamedTextColor.GRAY));
            meta.lore(List.of(Component.text(canGoNext ? "Click to go to page " + (session.getCurrentPage() + 2) : "You are on the last page", NamedTextColor.GRAY)));
        });
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
        item.editMeta(meta -> {
            meta.displayName(Component.text("🔍 Search Blueprints", NamedTextColor.YELLOW));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Click to search for blueprints", NamedTextColor.GRAY));
            if (session.hasSearchFilter()) {
                lore.add(Component.empty());
                lore.add(Component.text("Current filter: ", NamedTextColor.WHITE)
                        .append(Component.text(session.getSearchFilter(), NamedTextColor.AQUA)));
            }
            meta.lore(lore);
        });
        return new GuiItem(item, event -> {
            event.setCancelled(true);
            openSearchGui(player, session);
        });
    }

    private GuiItem createClearSearchButton(Player player, BlueprintGuiSession session) {
        ItemStack item = new ItemStack(Material.BARRIER);
        item.editMeta(meta -> {
            meta.displayName(Component.text("✗ Clear Search", NamedTextColor.RED));
            meta.lore(List.of(
                    Component.text("Current: " + session.getSearchFilter(), NamedTextColor.GRAY),
                    Component.text("Click to clear search filter", NamedTextColor.YELLOW)
            ));
        });
        return new GuiItem(item, event -> {
            event.setCancelled(true);
            session.clearSearchFilter();
            createAndShowGui(player, session);
        });
    }

    private GuiItem createToggleButton(Player player, BlueprintGuiSession session) {
        ItemStack item = new ItemStack(Material.COMPASS);
        BlueprintSortMode currentMode = session.getSortMode();
        BlueprintSortMode nextMode = currentMode.next();

        item.editMeta(meta -> {
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
        });

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
        item.editMeta(meta -> {
            meta.displayName(Component.text("Page " + currentPage + "/" + totalPages, NamedTextColor.WHITE));
        });
        return new GuiItem(item, event -> event.setCancelled(true));
    }

    private void openSearchGui(Player player, BlueprintGuiSession session) {
        AnvilGui anvilGui = new AnvilGui(ComponentHolder.of(
                Component.text("Search Blueprints", NamedTextColor.DARK_PURPLE)));

        StaticPane firstPane = new StaticPane(0, 0, 1, 1);
        ItemStack searchItem = new ItemStack(Material.PAPER);
        searchItem.editMeta(meta -> meta.displayName(Component.text(session.hasSearchFilter() ? session.getSearchFilter() : "Type to search...", NamedTextColor.WHITE)));
        firstPane.addItem(new GuiItem(searchItem, e -> e.setCancelled(true)), 0, 0);
        anvilGui.getFirstItemComponent().addPane(firstPane);

        StaticPane resultPane = new StaticPane(0, 0, 1, 1);
        ItemStack confirmItem = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
        confirmItem.editMeta(meta -> {
            meta.displayName(Component.text("✓ Click to Search", NamedTextColor.GREEN));
            meta.lore(List.of(Component.text("Click to apply your search", NamedTextColor.GRAY)));
        });

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
            TownyCivs.MORE_PAPER_LIB.scheduling().entitySpecificScheduler(player)
                    .run(() -> createAndShowGui(player, session), null);
        });

        anvilGui.show(player);
    }

    private static class StructureEntry {
        final Structure structure;
        final boolean canBuy;
        final int requiredLevel;
        final int buildCount;
        final int maxBuildCount;
        final int upgradeChainCount;  // Total count including upgraded versions

        StructureEntry(Structure structure, boolean canBuy, int requiredLevel, int buildCount, int upgradeChainCount) {
            this.structure = structure;
            this.canBuy = canBuy;
            this.requiredLevel = requiredLevel;
            this.buildCount = buildCount;
            this.maxBuildCount = structure.maxCount != null ? structure.maxCount : Integer.MAX_VALUE;
            this.upgradeChainCount = upgradeChainCount;
        }
    }
}
