package cz.neumimto.towny.townycivs.commands;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.*;
import com.palmergames.bukkit.towny.object.Translatable;
import cz.neumimto.towny.townycivs.TownyCivs;
import cz.neumimto.towny.townycivs.config.ConfigurationService;
import cz.neumimto.towny.townycivs.gui.BlueprintsGui;
import cz.neumimto.towny.townycivs.gui.HelpMenu1Gui;
import cz.neumimto.towny.townycivs.gui.HelpMenu2Gui;
import cz.neumimto.towny.townycivs.gui.MainMenuGui;
import cz.neumimto.towny.townycivs.gui.RegionGui;
import cz.neumimto.towny.townycivs.gui.StructureGui;
import cz.neumimto.towny.townycivs.gui.StructuresGui;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import javax.inject.Inject;
import javax.inject.Singleton;

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
        structuresGui.reloadGuiConfig();

        TownyCivs.logger.info("Reloaded all GUI configurations");

        sender.sendMessage(ChatColor.GREEN + "TownyCivs configurations reloaded successfully!");
    }
}
