package cz.neumimto.towny.townycivs.gui;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages per-player session state for the structures GUI.
 */
public class StructuresGuiSession {

    private int currentPage = 0;
    private StructureSortMode sortMode = StructureSortMode.ALPHABETICAL;
    private String searchFilter = "";

    private static final Map<UUID, StructuresGuiSession> sessions = new ConcurrentHashMap<>();

    public static StructuresGuiSession getSession(UUID playerUuid) {
        return sessions.computeIfAbsent(playerUuid, k -> new StructuresGuiSession());
    }

    public static void clearSession(UUID playerUuid) {
        sessions.remove(playerUuid);
    }

    public static void clearAllSessions() {
        sessions.clear();
    }

    public int getCurrentPage() {
        return currentPage;
    }

    public void setCurrentPage(int currentPage) {
        this.currentPage = Math.max(0, currentPage);
    }

    public void nextPage() {
        this.currentPage++;
    }

    public void previousPage() {
        if (this.currentPage > 0) {
            this.currentPage--;
        }
    }

    public StructureSortMode getSortMode() {
        return sortMode;
    }

    public void setSortMode(StructureSortMode sortMode) {
        this.sortMode = sortMode;
        this.currentPage = 0;
    }

    public void cycleSortMode() {
        this.sortMode = this.sortMode.next();
        this.currentPage = 0;
    }

    public String getSearchFilter() {
        return searchFilter;
    }

    public void setSearchFilter(String searchFilter) {
        this.searchFilter = searchFilter != null ? searchFilter.toLowerCase() : "";
        this.currentPage = 0;
    }

    public void clearSearchFilter() {
        this.searchFilter = "";
        this.currentPage = 0;
    }

    public boolean hasSearchFilter() {
        return searchFilter != null && !searchFilter.isEmpty();
    }
}

