package cz.neumimto.towny.townycivs.tutorial;

import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.object.metadata.IntegerDataField;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;

import javax.inject.Singleton;

/**
 * Manages tutorial state and progression for towns using Towny's Metadata API.
 * Tutorial progress persists across server restarts and player sessions.
 */
@Singleton
public class TutorialManager {

    private static final String TUTORIAL_STEP_KEY = "townycivs_tutorial_step";
    private final MiniMessage mm = MiniMessage.miniMessage();

    /**
     * Start tutorial for a new town
     */
    public void startTutorial(Town town) {
        setTutorialStep(town, TutorialStep.WELCOME);
    }

    /**
     * Get current tutorial step for a town (persistent via Towny Metadata)
     */
    public TutorialStep getTutorialStep(Town town) {
        if (town == null) return TutorialStep.NOT_STARTED;

        if (town.hasMeta(TUTORIAL_STEP_KEY)) {
            IntegerDataField idf = (IntegerDataField) town.getMetadata(TUTORIAL_STEP_KEY);
            if (idf != null) {
                return TutorialStep.fromStep(idf.getValue());
            }
        }
        return TutorialStep.NOT_STARTED;
    }

    /**
     * Set tutorial step for a town (persists to Towny database)
     */
    public void setTutorialStep(Town town, TutorialStep step) {
        if (town == null) return;

        if (town.hasMeta(TUTORIAL_STEP_KEY)) {
            town.removeMetaData(town.getMetadata(TUTORIAL_STEP_KEY));
        }
        town.addMetaData(new IntegerDataField(TUTORIAL_STEP_KEY, step.getStep()));
        town.save();
    }

    /**
     * Advance to next tutorial step
     */
    public void advanceTutorial(Town town, Player player) {
        TutorialStep current = getTutorialStep(town);
        TutorialStep next = current.next();

        setTutorialStep(town, next);

        // Play sound for advancement
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);

        if (next.isCompleted()) {
            player.sendMessage(mm.deserialize("<gold>[TownyCivs]</gold> <green><bold>Tutorial completed!</bold> All commands are now unlocked!</green>"));
            player.playSound(player.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        }

        showCurrentStep(player, town);
    }

    /**
     * Check if tutorial is active (in progress)
     */
    public boolean isTutorialActive(Town town) {
        TutorialStep step = getTutorialStep(town);
        return step.isActive();
    }

    /**
     * Complete the tutorial immediately
     */
    public void completeTutorial(Town town) {
        setTutorialStep(town, TutorialStep.COMPLETED);
    }

    /**
     * Show current tutorial step to player
     */
    public void showCurrentStep(Player player, Town town) {
        TutorialStep step = getTutorialStep(town);
        int claims = town.getTownBlocks().size();
        step.sendTo(player, claims);
    }

    /**
     * Send reminder on login if tutorial is in progress
     */
    public void sendReminder(Player player, Town town) {
        if (!isTutorialActive(town)) return;

        TutorialStep step = getTutorialStep(town);

        player.sendMessage(mm.deserialize(""));
        player.sendMessage(mm.deserialize("<gold>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gold>"));
        player.sendMessage(mm.deserialize("<yellow><bold>📚 Tutorial In Progress</bold></yellow>"));
        player.sendMessage(mm.deserialize("<gray>Welcome back! You're on step " + step.getStep() + "/14</gray>"));
        player.sendMessage(mm.deserialize("<gold>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gold>"));

        showCurrentStep(player, town);
    }

    /**
     * Called when town claims land - check if we should advance
     */
    public void onTownClaim(Town town) {
        TutorialStep step = getTutorialStep(town);
        if (step == TutorialStep.WELCOME || step == TutorialStep.CLAIM_LAND) {
            int claims = town.getTownBlocks().size();
            Player mayor = town.getMayor().getPlayer();

            if (step == TutorialStep.WELCOME) {
                setTutorialStep(town, TutorialStep.CLAIM_LAND);
                if (mayor != null && mayor.isOnline()) {
                    showCurrentStep(mayor, town);
                }
            }

            if (claims >= 3) {
                if (mayor != null && mayor.isOnline()) {
                    advanceTutorial(town, mayor);
                }
            }
        }
    }

    /**
     * Called when player crafts/obtains town administration tool
     */
    public void onTownToolObtained(Town town, Player player) {
        TutorialStep step = getTutorialStep(town);
        if (step == TutorialStep.CRAFT_TOWN_TOOL) {
            advanceTutorial(town, player);
        }
    }

    /**
     * Called when blueprint shop is opened
     */
    public void onShopOpened(Town town, Player player) {
        TutorialStep step = getTutorialStep(town);
        if (step == TutorialStep.OPEN_SHOP) {
            advanceTutorial(town, player);
        }
    }

    /**
     * Called when blueprint is purchased
     */
    public void onBlueprintPurchased(Town town, Player player, String structureId) {
        TutorialStep step = getTutorialStep(town);
        if (step == TutorialStep.BUY_BLUEPRINT) {
            advanceTutorial(town, player);
        } else if (step == TutorialStep.BUY_WHEAT_FARM) {
            // Check if it's a wheat farm blueprint
            if (structureId != null && structureId.toLowerCase().contains("wheat")) {
                advanceTutorial(town, player);
            }
        }
    }

    /**
     * Called when structure is placed
     */
    public void onStructurePlaced(Town town, Player player, String structureId) {
        TutorialStep step = getTutorialStep(town);
        if (step == TutorialStep.PLACE_STRUCTURE) {
            advanceTutorial(town, player);
        } else if (step == TutorialStep.PLACE_WHEAT_FARM) {
            // Check if it's a wheat farm
            if (structureId != null && structureId.toLowerCase().contains("wheat")) {
                advanceTutorial(town, player);
            }
        } else if (step == TutorialStep.BUILD_SURVEYORS_DESK) {
            // Check if it's a surveyor's desk
            if (structureId != null && structureId.toLowerCase().contains("surveyor")) {
                advanceTutorial(town, player);
            }
        }
    }

    /**
     * Called when player crafts/obtains edit tool
     */
    public void onEditToolObtained(Town town, Player player) {
        TutorialStep step = getTutorialStep(town);
        if (step == TutorialStep.CRAFT_EDIT_TOOL) {
            advanceTutorial(town, player);
        }
    }

    /**
     * Called when structure GUI is opened
     */
    public void onStructureGuiOpened(Town town, Player player) {
        TutorialStep step = getTutorialStep(town);
        if (step == TutorialStep.OPEN_STRUCTURE_GUI) {
            advanceTutorial(town, player);
        }
    }

    /**
     * Called when structure GUI is opened for a specific structure
     * This can help detect if player already has a wheat farm and skip steps
     */
    public void onStructureGuiOpened(Town town, Player player, String structureId) {
        TutorialStep step = getTutorialStep(town);
        if (step == TutorialStep.OPEN_STRUCTURE_GUI) {
            advanceTutorial(town, player);
        }

        // If player is on BUY_WHEAT_FARM or PLACE_WHEAT_FARM step but already has a wheat farm,
        // skip to SATISFY_BUILD_REQUIREMENTS
        if ((step == TutorialStep.BUY_WHEAT_FARM || step == TutorialStep.PLACE_WHEAT_FARM)
            && structureId != null && structureId.toLowerCase().contains("wheat")) {
            player.sendMessage(mm.deserialize("<gold>[TownyCivs]</gold> <green>You already have a Wheat Farm! Skipping ahead...</green>"));
            setTutorialStep(town, TutorialStep.SATISFY_BUILD_REQUIREMENTS);
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
            showCurrentStep(player, town);
        }
    }

    /**
     * Called when build requirements are satisfied for a structure
     */
    public void onBuildRequirementsSatisfied(Town town, Player player) {
        TutorialStep step = getTutorialStep(town);
        if (step == TutorialStep.SATISFY_BUILD_REQUIREMENTS) {
            advanceTutorial(town, player);
        }
    }

    /**
     * Called when tools/resources are added to a structure container
     */
    public void onToolsAddedToStructure(Town town, Player player) {
        TutorialStep step = getTutorialStep(town);
        if (step == TutorialStep.ADD_TOOLS_TO_STRUCTURE) {
            advanceTutorial(town, player);
        }
    }

    /**
     * Called when production is collected from a structure
     */
    public void onProductionCollected(Town town, Player player) {
        TutorialStep step = getTutorialStep(town);
        if (step == TutorialStep.COLLECT_PRODUCTION) {
            advanceTutorial(town, player);
        }
    }

    /**
     * Skip the tutorial immediately
     */
    public void skipTutorial(Town town, Player player) {
        completeTutorial(town);
        player.sendMessage(mm.deserialize("<gold>[TownyCivs]</gold> <yellow>Tutorial skipped. All commands unlocked!</yellow>"));
    }

    /**
     * Reset tutorial to beginning
     */
    public void resetTutorial(Town town, Player player) {
        startTutorial(town);
        player.sendMessage(mm.deserialize("<gold>[TownyCivs]</gold> <yellow>Tutorial reset to beginning.</yellow>"));
        showCurrentStep(player, town);
    }
}

