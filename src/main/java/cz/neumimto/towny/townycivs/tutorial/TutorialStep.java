package cz.neumimto.towny.townycivs.tutorial;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import java.util.ArrayList;
import java.util.List;

/**
 * Defines all tutorial steps for TownyCivs
 * Each step guides the mayor through learning the plugin mechanics
 */
public enum TutorialStep {
    NOT_STARTED(0, "Not Started", "Tutorial not started"),

    WELCOME(1, "Welcome",
        "<gold>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gold>",
        "<aqua><bold>Welcome to TownyCivs!</bold></aqua>",
        "",
        "<gray>TownyCivs is a plugin unique to Interbellum that adds structures, automated farms, factories and administrative and useful buildings to Towny!</gray>",
        "",
        "<yellow>Tutorial Progress:</yellow> <green>Step 1/14</green>",
        "",
        "<gold>Let's start by claiming land!</gold>",
        "<gray>Use <white>/t claim</white> to claim chunks.</gray>",
        "<gray>Claim at least <white>3 chunks</white> to continue.</gray>",
        "<gray>If you are familiar with the Civs plugin by Multitallented, you will be familiar with this system.</gray>",
        "<gold>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gold>"),

    CLAIM_LAND(2, "Claim Land",
        "<gold>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gold>",
        "<aqua><bold>Step 2/14: Claim Land</bold></aqua>",
        "",
        "<yellow>Current claims: %claims%/3</yellow>",
        "",
        "<gray>Use <white>/t claim</white> while standing in unclaimed land.</gray>",
        "<gray>Claim at least <white>3 chunks</white> to continue.</gray>",
        "<gold>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gold>"),

    CRAFT_TOWN_TOOL(3, "Craft Town Tool",
        "<gold>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gold>",
        "<aqua><bold>Step 3/14: Craft Town Administration Tool</bold></aqua>",
        "",
        "<gray>Craft the Town Administration tool to access blueprints! You can type /townycivs and click on the recipe/knowledge book to learn the recipe for it!</gray>",
        "",
        "<yellow>Recipe:</yellow>",
        "<white>Book + Paper (shapeless)</white>",
        "",
        "<gray>This tool opens the Blueprint Shop.</gray>",
        "<gold>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gold>"),

    OPEN_SHOP(4, "Open Shop",
        "<gold>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gold>",
        "<aqua><bold>Step 4/14: Open Blueprint Shop</bold></aqua>",
        "",
        "<yellow>How to use:</yellow>",
        "<green>→</green> <white>Hold Town Administration tool</white>",
        "<green>→</green> <white>Right-click anywhere</white>",
        "",
        "<gray>This opens the Blueprint Shop!</gray>",
        "<gold>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gold>"),

    BUY_BLUEPRINT(5, "Buy Blueprint",
        "<gold>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gold>",
        "<aqua><bold>Step 5/14: Buy a Blueprint</bold></aqua>",
        "",
        "<yellow>Purchase any structure blueprint!</yellow>",
        "",
        "<gray>Recommended: Wheat Farm or Coal Mine</gray>",
        "<gold>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gold>"),

    PLACE_STRUCTURE(6, "Place Structure",
        "<gold>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gold>",
        "<aqua><bold>Step 6/14: Place Your Structure</bold></aqua>",
        "",
        "<yellow>How to place:</yellow>",
        "<green>→</green> <white>Hold the blueprint</white>",
        "<green>→</green> <white>Right-click to preview area</white>",
        "<green>→</green> <white>Left-click to confirm placement</white>",
        "<gold>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gold>"),

    CRAFT_EDIT_TOOL(7, "Craft Edit Tool",
        "<gold>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gold>",
        "<aqua><bold>Step 7/14: Craft Structure Edit Tool</bold></aqua>",
        "",
        "<gray>You can type /townycivs and click on the recipe/knowledge book to learn the recipe!</gray>",
        "",
        "<yellow>Recipe:</yellow>",
        "<white>Paper + Wooden Shovel (shapeless)</white>",
        "",
        "<gray>This tool manages your structures!</gray>",
        "<gold>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gold>"),

    OPEN_STRUCTURE_GUI(8, "Manage Structure",
        "<gold>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gold>",
        "<aqua><bold>Step 8/14: Open Structure GUI</bold></aqua>",
        "",
        "<yellow>How to use:</yellow>",
        "<green>→</green> <white>Hold Structure Edit tool</white>",
        "<green>→</green> <white>Right-click your structure</white>",
        "<gold>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gold>"),

    BUY_WHEAT_FARM(9, "Buy Wheat Farm Blueprint",
        "<gold>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gold>",
        "<aqua><bold>Step 9/14: Buy Wheat Farm Blueprint</bold></aqua>",
        "",
        "<gray>Now let's build a proper production structure!</gray>",
        "",
        "<yellow>Steps:</yellow>",
        "<green>→</green> <white>Open the Blueprint Shop (right-click with Town Tool)</white>",
        "<green>→</green> <white>Find and purchase the <aqua>Wheat Farm</aqua> blueprint. If you have placed it already, try using the Structure Tool within the structure.</white>",
        "",
        "<gray>Wheat Farms produce wheat automatically!</gray>",
        "<gold>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gold>"),

    PLACE_WHEAT_FARM(10, "Place Wheat Farm",
        "<gold>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gold>",
        "<aqua><bold>Step 10/14: Place Wheat Farm</bold></aqua>",
        "",
        "<gray>Place the Wheat Farm blueprint in your town!</gray>",
        "",
        "<yellow>How to place:</yellow>",
        "<green>→</green> <white>Hold the Wheat Farm blueprint</white>",
        "<green>→</green> <white>Right-click to preview the area</white>",
        "<green>→</green> <white>Left-click to confirm placement</white>",
        "",
        "<gray>Make sure you have enough space!</gray>",
        "<gold>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gold>"),

    SATISFY_BUILD_REQUIREMENTS(11, "Build the Structure",
        "<gold>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gold>",
        "<aqua><bold>Step 11/14: Satisfy Build Requirements</bold></aqua>",
        "",
        "<gray>Your Wheat Farm needs blocks to function!</gray>",
        "",
        "<yellow>How to build:</yellow>",
        "<green>→</green> <white>Open the Structure GUI (right-click with Edit Tool)</white>",
        "<green>→</green> <white>Check the required blocks in the GUI</white>",
        "<green>→</green> <white>Toggle Edit mode within the GUI to Active and Place the required blocks within the structure area</white>",
        "",
        "<gray>Common requirements: Farmland, Water, Fences, etc.</gray>",
        "<gold>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gold>"),

    ADD_TOOLS_TO_STRUCTURE(12, "Add Tools & Resources",
        "<gold>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gold>",
        "<aqua><bold>Step 12/14: Add Tools & Resources</bold></aqua>",
        "",
        "<gray>Structures need tools and resources to produce!</gray>",
        "",
        "<yellow>For the Wheat Farm:</yellow>",
        "<green>→</green> <white>Toggle the Edit mode now to Inactive and Open the structure's container (chest)</white>",
        "<green>→</green> <white>Add a <aqua>Wooden Hoe</aqua> (or any hoe)</white>",
        "<green>→</green> <white>Add <aqua>Bone Meal</aqua> for faster growth</white>",
        "",
        "<gray>The farm will now produce wheat automatically!</gray>",
        "<gray>Collect your wheat from the container!</gray>",
        "<gold>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gold>"),

    COLLECT_PRODUCTION(13, "Collect Your Production",
        "<gold>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gold>",
        "<aqua><bold>Step 13/14: Collect Production</bold></aqua>",
        "",
        "<gray>Wait for production and collect your wheat!</gray>",
        "",
        "<yellow>How production works:</yellow>",
        "<green>→</green> <white>Structures produce on a timer</white>",
        "<green>→</green> <white>Products appear in the structure's container</white>",
        "<green>→</green> <white>Open the container to collect your items</white>",
        "",
        "<gray>Collect at least <aqua>1 wheat</aqua> to continue!</gray>",
        "<gold>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gold>"),

    BUILD_SURVEYORS_DESK(14, "Build Surveyor's Desk",
        "<gold>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gold>",
        "<aqua><bold>Final Step: Build Surveyor's Desk</bold></aqua>",
        "",
        "<gray>Build a Surveyor's Desk to grow your town!</gray>",
        "",
        "<yellow>The Surveyor's Desk allows you to:</yellow>",
        "<green>→</green> <white>Invite more residents</white>",
        "<green>→</green> <white>Expand your town</white>",
        "<green>→</green> <white>Level up your town</white>",
        "",
        "<gray>Buy and place a Surveyor's Desk to complete!</gray>",
        "<gold>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gold>"),

    COMPLETED(15, "Complete!",
        "<gold>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gold>",
        "<aqua><bold>✓ Tutorial Complete!</bold></aqua>",
        "",
        "<green>All commands are now unlocked!</green>",
        "",
        "<yellow>You can now:</yellow>",
        "<green>→</green> <white>Build more structures</white>",
        "<green>→</green> <white>Upgrade existing structures</white>",
        "<green>→</green> <white>Invite residents to your town</white>",
        "<gold>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gold>");

    private final int step;
    private final String name;
    private final String[] messages;

    TutorialStep(int step, String name, String... messages) {
        this.step = step;
        this.name = name;
        this.messages = messages;
    }

    public int getStep() { return step; }
    public String getName() { return name; }

    public List<Component> getFormattedMessages(int claims) {
        MiniMessage mm = MiniMessage.miniMessage();
        List<Component> components = new ArrayList<>();
        for (String message : messages) {
            components.add(mm.deserialize(message.replace("%claims%", String.valueOf(claims))));
        }
        return components;
    }

    public void sendTo(org.bukkit.entity.Player player, int claims) {
        for (Component comp : getFormattedMessages(claims)) {
            player.sendMessage(comp);
        }
    }

    public static TutorialStep fromStep(int step) {
        for (TutorialStep ts : values()) {
            if (ts.step == step) return ts;
        }
        return NOT_STARTED;
    }

    public TutorialStep next() {
        return fromStep(this.step + 1);
    }

    public boolean isCompleted() {
        return this == COMPLETED;
    }

    public boolean isActive() {
        return this != NOT_STARTED && this != COMPLETED;
    }
}

