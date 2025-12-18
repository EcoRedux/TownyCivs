package cz.neumimto.towny.townycivs.gui;

/**
 * Enum representing the different sorting modes for placed structures.
 */
public enum StructureSortMode {
    ALPHABETICAL("A-Z", "Sorted alphabetically by name"),
    REVERSE_ALPHABETICAL("Z-A", "Sorted reverse alphabetically"),
    NEWEST_FIRST("Newest", "Most recently placed first"),
    OLDEST_FIRST("Oldest", "Oldest structures first"),
    BY_TYPE("By Type", "Grouped by structure type");

    private final String displayName;
    private final String description;

    StructureSortMode(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public StructureSortMode next() {
        StructureSortMode[] values = values();
        int nextIndex = (this.ordinal() + 1) % values.length;
        return values[nextIndex];
    }

    public StructureSortMode previous() {
        StructureSortMode[] values = values();
        int prevIndex = (this.ordinal() - 1 + values.length) % values.length;
        return values[prevIndex];
    }
}

