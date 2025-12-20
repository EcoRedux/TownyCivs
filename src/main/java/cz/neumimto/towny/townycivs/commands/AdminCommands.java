package cz.neumimto.towny.townycivs.commands;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.*;
import cz.neumimto.towny.townycivs.TownyCivs;
import cz.neumimto.towny.townycivs.config.ConfigurationService;
import cz.neumimto.towny.townycivs.gui.BlueprintsGui;
import cz.neumimto.towny.townycivs.gui.HelpMenu1Gui;
import cz.neumimto.towny.townycivs.gui.HelpMenu2Gui;
import cz.neumimto.towny.townycivs.gui.MainMenuGui;
import cz.neumimto.towny.townycivs.gui.RegionGui;
import cz.neumimto.towny.townycivs.gui.StructureGui;
import cz.neumimto.towny.townycivs.gui.StructuresGui;
import cz.neumimto.towny.townycivs.schedulers.FoliaScheduler;
import cz.neumimto.towny.townycivs.ItemService;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Duration;
import java.time.temporal.ChronoUnit;

@CommandAlias("townycivs|toco|tciv")
@Singleton
public class AdminCommands extends BaseCommand {

    @Inject
    private ConfigurationService configurationService;

    @Subcommand("reload")
    @CommandPermission("townycivs.admin.reload")
    @Description("Reloads all configurations including GUI and structures from disk")
    public void onReload(CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "Reloading TownyCivs configurations...");

        // Stop the current scheduler
        sender.sendMessage(ChatColor.YELLOW + "Stopping FoliaScheduler...");
        stopScheduler();

        // First, clear the cache of all GUI instances
        MainMenuGui mainMenuGui = TownyCivs.injector.getInstance(MainMenuGui.class);
        BlueprintsGui blueprintsGui = TownyCivs.injector.getInstance(BlueprintsGui.class);
        HelpMenu1Gui helpMenu1Gui = TownyCivs.injector.getInstance(HelpMenu1Gui.class);
        HelpMenu2Gui helpMenu2Gui = TownyCivs.injector.getInstance(HelpMenu2Gui.class);
        RegionGui regionGui = TownyCivs.injector.getInstance(RegionGui.class);
        StructureGui structureGui = TownyCivs.injector.getInstance(StructureGui.class);
        StructuresGui structuresGui = TownyCivs.injector.getInstance(StructuresGui.class);

        // Clear caches
        mainMenuGui.clearCache();
        blueprintsGui.clearCache();
        helpMenu1Gui.clearCache();
        helpMenu2Gui.clearCache();
        regionGui.clearCache();
        structureGui.clearCache();
        structuresGui.clearCache();

        TownyCivs.logger.info("Cleared all GUI configuration caches");

        // Reload structure configurations (including area dimensions)
        try {
            // Use the proper method to reload structures
            configurationService.reloadStructures(TownyCivs.INSTANCE.getDataFolder().toPath());

            // Reload all structures
            TownyCivs.injector.getInstance(cz.neumimto.towny.townycivs.StructureService.class).loadAll();

            sender.sendMessage(ChatColor.GREEN + "Structure configurations successfully reloaded (including area dimensions)");
        } catch (Exception e) {
            TownyCivs.logger.severe("Failed to reload structure configurations: " + e.getMessage());
            e.printStackTrace();
            sender.sendMessage(ChatColor.RED + "Failed to reload structure configurations. See console for details.");
        }

        // Now reload all GUI configurations
        mainMenuGui.reloadGuiConfig();
        blueprintsGui.reloadGuiConfig();
        helpMenu1Gui.reloadGuiConfig();
        helpMenu2Gui.reloadGuiConfig();
        regionGui.reloadGuiConfig();
        structureGui.reloadGuiConfig();

        TownyCivs.logger.info("Reloaded all GUI configurations");

        // Restart the scheduler
        sender.sendMessage(ChatColor.YELLOW + "Restarting FoliaScheduler...");
        restartScheduler();

        sender.sendMessage(ChatColor.GREEN + "TownyCivs configurations reloaded successfully!");
    }

    @Subcommand("scheduler restart")
    @CommandPermission("townycivs.admin.scheduler")
    @Description("Stops and restarts the FoliaScheduler")
    public void onRestartScheduler(CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "Restarting FoliaScheduler...");

        stopScheduler();
        restartScheduler();

        sender.sendMessage(ChatColor.GREEN + "FoliaScheduler restarted successfully!");
    }

    @Subcommand("scheduler stop")
    @CommandPermission("townycivs.admin.scheduler")
    @Description("Stops the FoliaScheduler")
    public void onStopScheduler(CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "Stopping FoliaScheduler...");

        stopScheduler();

        sender.sendMessage(ChatColor.GREEN + "FoliaScheduler stopped!");
    }

    @Subcommand("scheduler start")
    @CommandPermission("townycivs.admin.scheduler")
    @Description("Starts the FoliaScheduler")
    public void onStartScheduler(CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "Starting FoliaScheduler...");

        restartScheduler();

        sender.sendMessage(ChatColor.GREEN + "FoliaScheduler started!");
    }

    private void stopScheduler() {
        TownyCivs.stopCurrentScheduler();
    }

    private void restartScheduler() {
        TownyCivs.startNewScheduler();
    }

    @Subcommand("give admin-tool")
    @CommandPermission("townycivs.admin.give")
    @Description("Gives the Town Administration tool to a player (or yourself if no player specified)")
    @Syntax("[player]")
    public void onGiveAdminTool(CommandSender sender, @Optional String playerName) {
        Player target;

        if (playerName == null || playerName.isEmpty()) {
            // Give to sender if they're a player
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "This command can only be used by players when no target is specified.");
                return;
            }
            target = (Player) sender;
        } else {
            // Give to specified player
            target = Bukkit.getPlayer(playerName);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Player '" + playerName + "' not found or not online.");
                return;
            }
        }

        target.getInventory().addItem(ItemService.getTownAdministrationTool());

        if (target == sender) {
            sender.sendMessage(ChatColor.GREEN + "You have received the Town Administration tool!");
        } else {
            sender.sendMessage(ChatColor.GREEN + "Gave Town Administration tool to " + target.getName());
            target.sendMessage(ChatColor.GREEN + "You have received the Town Administration tool!");
        }
    }

    @Subcommand("give structure-tool")
    @CommandPermission("townycivs.admin.give")
    @Description("Gives the Structure Edit tool to a player (or yourself if no player specified)")
    @Syntax("[player]")
    public void onGiveStructureTool(CommandSender sender, @Optional String playerName) {
        Player target;

        if (playerName == null || playerName.isEmpty()) {
            // Give to sender if they're a player
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "This command can only be used by players when no target is specified.");
                return;
            }
            target = (Player) sender;
        } else {
            // Give to specified player
            target = Bukkit.getPlayer(playerName);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Player '" + playerName + "' not found or not online.");
                return;
            }
        }

        target.getInventory().addItem(ItemService.getStructureTool());

        if (target == sender) {
            sender.sendMessage(ChatColor.GREEN + "You have received the Structure Edit tool!");
        } else {
            sender.sendMessage(ChatColor.GREEN + "Gave Structure Edit tool to " + target.getName());
            target.sendMessage(ChatColor.GREEN + "You have received the Structure Edit tool!");
        }
    }

    @Subcommand("give power-tool")
    @CommandPermission("townycivs.admin.give")
    @Description("Gives the Power tool to a player (or yourself if no player specified)")
    @Syntax("[player]")
    public void onGivePowerTool(CommandSender sender, @Optional String playerName) {
        Player target;

        if (playerName == null || playerName.isEmpty()) {
            // Give to sender if they're a player
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "This command can only be used by players when no target is specified.");
                return;
            }
            target = (Player) sender;
        } else {
            // Give to specified player
            target = Bukkit.getPlayer(playerName);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Player '" + playerName + "' not found or not online.");
                return;
            }
        }

        target.getInventory().addItem(ItemService.getPowerTool());

        if (target == sender) {
            sender.sendMessage(ChatColor.GREEN + "You have received the Power tool!");
        } else {
            sender.sendMessage(ChatColor.GREEN + "Gave Power tool to " + target.getName());
            target.sendMessage(ChatColor.GREEN + "You have received the Power tool!");
        }
    }

    // ==================== Tutorial Commands ====================

    @Inject
    private cz.neumimto.towny.townycivs.tutorial.TutorialManager tutorialManager;

    @Subcommand("tutorial start")
    @Description("Start or restart the tutorial for your town")
    public void onTutorialStart(Player player) {
        com.palmergames.bukkit.towny.object.Resident resident = com.palmergames.bukkit.towny.TownyAPI.getInstance().getResident(player);
        if (resident == null || !resident.hasTown()) {
            player.sendMessage(ChatColor.RED + "You must be in a town to use this command.");
            return;
        }

        if (!resident.isMayor()) {
            player.sendMessage(ChatColor.RED + "Only the mayor can manage the tutorial.");
            return;
        }

        com.palmergames.bukkit.towny.object.Town town = resident.getTownOrNull();
        tutorialManager.resetTutorial(town, player);
    }

    @Subcommand("tutorial skip")
    @Description("Skip the tutorial and unlock all commands")
    public void onTutorialSkip(Player player) {
        com.palmergames.bukkit.towny.object.Resident resident = com.palmergames.bukkit.towny.TownyAPI.getInstance().getResident(player);
        if (resident == null || !resident.hasTown()) {
            player.sendMessage(ChatColor.RED + "You must be in a town to use this command.");
            return;
        }

        if (!resident.isMayor()) {
            player.sendMessage(ChatColor.RED + "Only the mayor can manage the tutorial.");
            return;
        }

        com.palmergames.bukkit.towny.object.Town town = resident.getTownOrNull();
        tutorialManager.skipTutorial(town, player);
    }

    @Subcommand("tutorial status")
    @Description("Check your tutorial progress")
    public void onTutorialStatus(Player player) {
        com.palmergames.bukkit.towny.object.Resident resident = com.palmergames.bukkit.towny.TownyAPI.getInstance().getResident(player);
        if (resident == null || !resident.hasTown()) {
            player.sendMessage(ChatColor.RED + "You must be in a town to use this command.");
            return;
        }

        com.palmergames.bukkit.towny.object.Town town = resident.getTownOrNull();
        cz.neumimto.towny.townycivs.tutorial.TutorialStep step = tutorialManager.getTutorialStep(town);

        if (step == cz.neumimto.towny.townycivs.tutorial.TutorialStep.NOT_STARTED) {
            player.sendMessage(ChatColor.YELLOW + "Tutorial has not been started for your town.");
            player.sendMessage(ChatColor.GRAY + "Use /townycivs tutorial start to begin.");
        } else if (step == cz.neumimto.towny.townycivs.tutorial.TutorialStep.COMPLETED) {
            player.sendMessage(ChatColor.GREEN + "Tutorial is complete! All commands are unlocked.");
        } else {
            player.sendMessage(ChatColor.YELLOW + "Tutorial in progress: Step " + step.getStep() + "/14 - " + step.getName());
            tutorialManager.showCurrentStep(player, town);
        }
    }
}
