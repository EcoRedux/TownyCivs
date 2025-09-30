package cz.neumimto.towny.townycivs.gui;

import com.github.stefvanschie.inventoryframework.gui.type.ChestGui;
import cz.neumimto.towny.townycivs.TownyCivs;
import cz.neumimto.towny.townycivs.gui.api.GuiConfig;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import javax.inject.Singleton;

@Singleton
public class MainMenuGui extends TCGui {

    public MainMenuGui() {
        super("Main.conf", TownyCivs.INSTANCE.getDataFolder().toPath());
    }

    @Override
    protected String getTitle(CommandSender commandSender, GuiConfig guiConfig, String param) {
        return "TownyCivs";
    }

    public void display(Player player, String townName) {
        ChestGui chestGui = loadGui(player, townName);
        chestGui.show(player);
    }
}
