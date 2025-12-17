package cz.neumimto.towny.townycivs.gui;

/**
 * Enum representing the different sorting modes for blueprints in the shop.
 */
public enum BlueprintSortMode {
    AVAILABLE("Available", "Shows only blueprints you can currently buy"),
    ALL("All", "Shows all blueprints"),
    ALPHABETICAL("A-Z", "Sorted alphabetically"),
    REVERSE_ALPHABETICAL("Z-A", "Sorted reverse alphabetically"),
    LEVEL_ASCENDING("Level ↑", "Sorted by required level (low to high)"),
    LEVEL_DESCENDING("Level ↓", "Sorted by required level (high to low)"),
    LEVEL_1("Level 1", "Only Level 1 structures"),
    LEVEL_2("Level 2", "Only Level 2 structures"),
    LEVEL_3("Level 3", "Only Level 3 structures"),
    LEVEL_4("Level 4", "Only Level 4 structures"),
    LEVEL_5("Level 5", "Only Level 5 structures"),
    LEVEL_6("Level 6", "Only Level 6 structures"),
    LEVEL_7("Level 7", "Only Level 7 structures"),
    LEVEL_8("Level 8", "Only Level 8 structures"),
    LEVEL_9("Level 9", "Only Level 9 structures");

    private final String displayName;
    private final String description;

    BlueprintSortMode(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Get the next sort mode in the cycle.
     */
    public BlueprintSortMode next() {
        BlueprintSortMode[] values = values();
        int nextIndex = (this.ordinal() + 1) % values.length;
        return values[nextIndex];
    }

    /**
     * Get the previous sort mode in the cycle.
     */
    public BlueprintSortMode previous() {
        BlueprintSortMode[] values = values();
        int prevIndex = (this.ordinal() - 1 + values.length) % values.length;
        return values[prevIndex];
    }

    /**
     * Check if this mode filters by a specific level.
     */
    public boolean isLevelFilter() {
        return this.ordinal() >= LEVEL_1.ordinal();
    }

    /**
     * Get the level number if this is a level filter, or -1 otherwise.
     */
    public int getLevelFilter() {
        if (!isLevelFilter()) {
            return -1;
        }
        return this.ordinal() - LEVEL_1.ordinal() + 1;
    }
}

