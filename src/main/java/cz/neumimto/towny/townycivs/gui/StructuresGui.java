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
import cz.neumimto.towny.townycivs.StructureService;
import cz.neumimto.towny.townycivs.TownyCivs;
import cz.neumimto.towny.townycivs.gui.api.GuiConfig;
import cz.neumimto.towny.townycivs.model.LoadedStructure;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
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
public class StructuresGui {

    private static final String CONFIG_FILE = "Structures.conf";

    @Inject
    private StructureService structureService;

    private GuiConfig guiConfig;
    private int itemsPerPage = 28;
    private List<int[]> contentSlots = new ArrayList<>();
    private Map<String, int[]> controlSlots = new HashMap<>();

    public void display(Player player) {
        StructuresGuiSession session = StructuresGuiSession.getSession(player.getUniqueId());
        loadConfigIfNeeded();
        createAndShowGui(player, session);
    }

    public void clearCache() {
        StructuresGuiSession.clearAllSessions();
        guiConfig = null;
    }

    public void clearCache(UUID uuid) {
        StructuresGuiSession.clearSession(uuid);
    }

    public void reloadGuiConfig() {
        guiConfig = null;
        loadConfigIfNeeded();
    }

    private void loadConfigIfNeeded() {
        if (guiConfig != null) return;

        Path configPath = TownyCivs.INSTANCE.getDataFolder().toPath().resolve("guis/" + CONFIG_FILE);

        try {
            if (!Files.exists(configPath)) {
                // Create from JAR resource
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
                // Fallback: load from JAR
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
            TownyCivs.logger.severe("Failed to load Structures GUI config: " + e.getMessage());
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

        // Build supplier map from mask
        Map<Character, String> charToSupplier = new HashMap<>();
        if (guiConfig.mask != null) {
            for (GuiConfig.MaskConfig mask : guiConfig.mask) {
                if (mask.C != null && !mask.C.isEmpty() && mask.supplier != null) {
                    charToSupplier.put(mask.C.charAt(0), mask.supplier);
                }
            }
        }

        // Parse inventory layout
        for (int row = 0; row < guiConfig.inventory.size(); row++) {
            String rowStr = guiConfig.inventory.get(row);
            for (int col = 0; col < rowStr.length(); col++) {
                char c = rowStr.charAt(col);
                String supplier = charToSupplier.get(c);

                if (supplier != null) {
                    switch (supplier.toLowerCase()) {
                        case "structures":
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

    private void createAndShowGui(Player player, StructuresGuiSession session) {
        var resident = TownyAPI.getInstance().getResident(player);
        if (resident == null) {
            player.sendMessage(Component.text("You are not registered as a resident!", NamedTextColor.RED));
            return;
        }

        Town town = resident.getTownOrNull();
        if (town == null) {
            player.sendMessage(Component.text("You must be in a town to view structures!", NamedTextColor.RED));
            return;
        }

        List<LoadedStructure> structures = getFilteredAndSortedStructures(town, session);
        int totalPages = Math.max(1, (int) Math.ceil((double) structures.size() / Math.max(1, itemsPerPage)));

        if (session.getCurrentPage() >= totalPages) {
            session.setCurrentPage(Math.max(0, totalPages - 1));
        }

        int guiRows = guiConfig != null ? guiConfig.inventory.size() : 6;
        String title = createTitle(town, session, structures.size());
        ChestGui gui = new ChestGui(guiRows, ComponentHolder.of(MiniMessage.miniMessage().deserialize(title)));
        gui.setOnGlobalClick(event -> event.setCancelled(true));

        // Create background pane from config
        StaticPane backgroundPane = new StaticPane(0, 0, 9, guiRows, Pane.Priority.LOWEST);
        fillBackgroundFromConfig(backgroundPane);
        gui.addPane(backgroundPane);

        // Content pane for structures
        StaticPane contentPane = new StaticPane(0, 0, 9, guiRows, Pane.Priority.NORMAL);
        int startIndex = session.getCurrentPage() * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, structures.size());

        int slotIdx = 0;
        for (int i = startIndex; i < endIndex && slotIdx < contentSlots.size(); i++) {
            LoadedStructure structure = structures.get(i);
            int[] pos = contentSlots.get(slotIdx);
            contentPane.addItem(createStructureItem(player, structure), pos[0], pos[1]);
            slotIdx++;
        }
        gui.addPane(contentPane);

        // Control pane
        StaticPane controlPane = new StaticPane(0, 0, 9, guiRows, Pane.Priority.HIGH);

        // Return to main menu button
        if (controlSlots.containsKey("return")) {
            int[] pos = controlSlots.get("return");
            controlPane.addItem(createReturnButton(player), pos[0], pos[1]);
        }

        // Previous page button
        if (controlSlots.containsKey("back")) {
            int[] pos = controlSlots.get("back");
            controlPane.addItem(createBackButton(player, session, totalPages), pos[0], pos[1]);
        }

        // Search button
        if (controlSlots.containsKey("search")) {
            int[] pos = controlSlots.get("search");
            controlPane.addItem(createSearchButton(player, session), pos[0], pos[1]);
        }

        // Clear search button
        if (controlSlots.containsKey("clearsearch") && session.hasSearchFilter()) {
            int[] pos = controlSlots.get("clearsearch");
            controlPane.addItem(createClearSearchButton(player, session), pos[0], pos[1]);
        }

        // Toggle sort button
        if (controlSlots.containsKey("toggle")) {
            int[] pos = controlSlots.get("toggle");
            controlPane.addItem(createToggleButton(player, session), pos[0], pos[1]);
        }

        // Page info
        if (controlSlots.containsKey("pageinfo")) {
            int[] pos = controlSlots.get("pageinfo");
            controlPane.addItem(createPageInfoItem(session.getCurrentPage() + 1, totalPages, structures.size()), pos[0], pos[1]);
        }

        // Next page button
        if (controlSlots.containsKey("next")) {
            int[] pos = controlSlots.get("next");
            controlPane.addItem(createNextButton(player, session, totalPages), pos[0], pos[1]);
        }

        gui.addPane(controlPane);
        gui.show(player);
    }

    private void fillBackgroundFromConfig(StaticPane pane) {
        if (guiConfig == null || guiConfig.inventory == null) return;

        // Build mask character to item map
        Map<Character, ItemStack> charToItem = new HashMap<>();
        if (guiConfig.mask != null) {
            for (GuiConfig.MaskConfig mask : guiConfig.mask) {
                if (mask.C != null && !mask.C.isEmpty() && mask.id != null) {
                    // Skip content slots and control suppliers
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

        // Fill background
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

    private String createTitle(Town town, StructuresGuiSession session, int total) {
        StringBuilder title = new StringBuilder();
        title.append("<white>").append(town.getName()).append(" - Structures (").append(total).append(")");
        if (session.hasSearchFilter()) {
            title.append(" <gray>[").append(session.getSearchFilter()).append("]");
        }
        return title.toString();
    }

    private List<LoadedStructure> getFilteredAndSortedStructures(Town town, StructuresGuiSession session) {
        Collection<LoadedStructure> allStructures = structureService.getAllStructures(town);
        List<LoadedStructure> structures = new ArrayList<>(allStructures);

        if (session.hasSearchFilter()) {
            String filter = session.getSearchFilter();
            structures = structures.stream()
                    .filter(s -> s.structureDef != null &&
                            (s.structureDef.name.toLowerCase().contains(filter) ||
                             s.structureId.toLowerCase().contains(filter)))
                    .collect(Collectors.toList());
        }

        switch (session.getSortMode()) {
            case ALPHABETICAL:
                structures.sort((a, b) -> getName(a).compareToIgnoreCase(getName(b)));
                break;
            case REVERSE_ALPHABETICAL:
                structures.sort((a, b) -> getName(b).compareToIgnoreCase(getName(a)));
                break;
            case NEWEST_FIRST:
                structures.sort((a, b) -> Long.compare(b.lastTickTime, a.lastTickTime));
                break;
            case OLDEST_FIRST:
                structures.sort(Comparator.comparingLong(s -> s.lastTickTime));
                break;
            case BY_TYPE:
                structures.sort((a, b) -> {
                    int cmp = a.structureId.compareToIgnoreCase(b.structureId);
                    return cmp != 0 ? cmp : a.uuid.compareTo(b.uuid);
                });
                break;
        }
        return structures;
    }

    private String getName(LoadedStructure s) {
        return s.structureDef != null ? s.structureDef.name : s.structureId;
    }

    private GuiItem createStructureItem(Player player, LoadedStructure structure) {
        ItemStack itemStack;
        if (structure.structureDef != null) {
            itemStack = structureService.toItemStack(structure.structureDef, 1);
        } else {
            itemStack = new ItemStack(Material.BARRIER);
            ItemMeta meta = itemStack.getItemMeta();
            meta.displayName(Component.text("Unknown: " + structure.structureId, NamedTextColor.RED));
            itemStack.setItemMeta(meta);
        }

        ItemMeta meta = itemStack.getItemMeta();
        List<Component> lore = meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Component.text("Location: ", NamedTextColor.GRAY)
                .append(Component.text(formatLocation(structure.center), NamedTextColor.WHITE)));
        if (structure.editMode.get()) {
            lore.add(Component.text("⚠ Edit Mode Active", NamedTextColor.YELLOW));
        }
        lore.add(Component.empty());
        lore.add(Component.text("Click to manage", NamedTextColor.GREEN));
        meta.lore(lore);
        itemStack.setItemMeta(meta);

        return new GuiItem(itemStack, event -> {
            event.setCancelled(true);
            player.closeInventory();
            TownyCivs.MORE_PAPER_LIB.scheduling().entitySpecificScheduler(player)
                    .run(() -> player.performCommand("townycivs structure " + structure.uuid), null);
        });
    }

    private String formatLocation(org.bukkit.Location loc) {
        return loc == null ? "Unknown" : String.format("%d, %d, %d", loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
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

    private GuiItem createBackButton(Player player, StructuresGuiSession session, int totalPages) {
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

    private GuiItem createNextButton(Player player, StructuresGuiSession session, int totalPages) {
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

    private GuiItem createSearchButton(Player player, StructuresGuiSession session) {
        ItemStack item = new ItemStack(Material.SPYGLASS);
        item.editMeta(meta -> {
            meta.displayName(Component.text("🔍 Search Structures", NamedTextColor.YELLOW));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Click to search", NamedTextColor.GRAY));
            if (session.hasSearchFilter()) {
                lore.add(Component.empty());
                lore.add(Component.text("Current: ", NamedTextColor.WHITE).append(Component.text(session.getSearchFilter(), NamedTextColor.AQUA)));
            }
            meta.lore(lore);
        });
        return new GuiItem(item, event -> {
            event.setCancelled(true);
            openSearchGui(player, session);
        });
    }

    private GuiItem createClearSearchButton(Player player, StructuresGuiSession session) {
        ItemStack item = new ItemStack(Material.BARRIER);
        item.editMeta(meta -> {
            meta.displayName(Component.text("✗ Clear Search", NamedTextColor.RED));
            meta.lore(List.of(Component.text("Current: " + session.getSearchFilter(), NamedTextColor.GRAY)));
        });
        return new GuiItem(item, event -> {
            event.setCancelled(true);
            session.clearSearchFilter();
            createAndShowGui(player, session);
        });
    }

    private GuiItem createToggleButton(Player player, StructuresGuiSession session) {
        ItemStack item = new ItemStack(Material.COMPASS);
        StructureSortMode current = session.getSortMode();
        StructureSortMode next = current.next();

        item.editMeta(meta -> {
            meta.displayName(Component.text("⚙ Sort: " + current.getDisplayName(), NamedTextColor.GREEN));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(current.getDescription(), NamedTextColor.GRAY));
            lore.add(Component.empty());
            lore.add(Component.text("Left-click: ", NamedTextColor.WHITE).append(Component.text(next.getDisplayName(), NamedTextColor.YELLOW)));
            lore.add(Component.text("Right-click: ", NamedTextColor.WHITE).append(Component.text("Previous", NamedTextColor.YELLOW)));
            lore.add(Component.empty());
            for (StructureSortMode mode : StructureSortMode.values()) {
                lore.add(Component.text((mode == current ? " ▶ " : "   ") + mode.getDisplayName(), mode == current ? NamedTextColor.GREEN : NamedTextColor.GRAY));
            }
            meta.lore(lore);
        });

        return new GuiItem(item, event -> {
            event.setCancelled(true);
            if (event.isRightClick()) {
                session.setSortMode(current.previous());
            } else {
                session.cycleSortMode();
            }
            createAndShowGui(player, session);
        });
    }

    private GuiItem createPageInfoItem(int currentPage, int totalPages, int total) {
        ItemStack item = new ItemStack(Material.PAPER);
        item.editMeta(meta -> {
            meta.displayName(Component.text("Page " + currentPage + "/" + totalPages, NamedTextColor.WHITE));
            meta.lore(List.of(Component.text("Total: " + total + " structures", NamedTextColor.GRAY)));
        });
        return new GuiItem(item, event -> event.setCancelled(true));
    }

    private void openSearchGui(Player player, StructuresGuiSession session) {
        AnvilGui anvilGui = new AnvilGui(ComponentHolder.of(Component.text("Search Structures", NamedTextColor.DARK_PURPLE)));

        StaticPane firstPane = new StaticPane(0, 0, 1, 1);
        ItemStack searchItem = new ItemStack(Material.PAPER);
        searchItem.editMeta(meta -> meta.displayName(Component.text(session.hasSearchFilter() ? session.getSearchFilter() : "Type to search...", NamedTextColor.WHITE)));
        firstPane.addItem(new GuiItem(searchItem, e -> e.setCancelled(true)), 0, 0);
        anvilGui.getFirstItemComponent().addPane(firstPane);

        StaticPane resultPane = new StaticPane(0, 0, 1, 1);
        ItemStack confirmItem = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
        confirmItem.editMeta(meta -> {
            meta.displayName(Component.text("✓ Click to Search", NamedTextColor.GREEN));
            meta.lore(List.of(Component.text("Click to apply", NamedTextColor.GRAY)));
        });

        resultPane.addItem(new GuiItem(confirmItem, event -> {
            event.setCancelled(true);
            String text = anvilGui.getRenameText();
            if (text != null && !text.isEmpty() && !text.equals("Type to search...")) {
                session.setSearchFilter(text);
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
}
