package cz.neumimto.towny.townycivs.tutorial;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import cz.neumimto.towny.townycivs.ItemService;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.inventory.ItemStack;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Set;

/**
 * Listener that blocks non-tutorial commands during tutorial mode
 * and tracks events for tutorial progression
 */
@Singleton
public class TutorialListener implements Listener {

    @Inject
    private TutorialManager tutorialManager;

    @Inject
    private ItemService itemService;

    private final MiniMessage mm = MiniMessage.miniMessage();

    // Commands that are allowed during tutorial (prefixes)
    private static final Set<String> ALLOWED_COMMAND_PREFIXES = Set.of(
        "/t claim",
        "/town claim",
        "/townycivs tutorial",
        "/toco tutorial",
        "/tciv tutorial",
        "/t spawn",
        "/town spawn",
        "/town deposit",
        "/t deposit",
        "/t toggle pvp off",
        "/town toggle pvp off",
        "/t toggle pvp"
    );

    // Exact commands allowed
    private static final Set<String> ALLOWED_EXACT_COMMANDS = Set.of(
        "/t",
        "/town",
        "/t ?",
        "/town ?",
        "/townycivs",
        "/toco",
        "/tciv",
         "/toci"
    );

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        Resident resident = TownyAPI.getInstance().getResident(player);

        if (resident == null || !resident.hasTown()) {
            return;
        }

        // Only restrict mayors during tutorial
        if (!resident.isMayor()) {
            return;
        }

        Town town = resident.getTownOrNull();
        if (town == null) {
            return;
        }

        if (!tutorialManager.isTutorialActive(town)) {
            return;
        }

        String command = event.getMessage().toLowerCase().trim();

        // Check if command is allowed
        if (isCommandAllowed(command)) {
            return;
        }

        // Block the command
        event.setCancelled(true);

        player.sendMessage(mm.deserialize(""));
        player.sendMessage(mm.deserialize("<gold>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gold>"));
        player.sendMessage(mm.deserialize("<red><bold>⚠ Command Locked</bold></red>"));
        player.sendMessage(mm.deserialize("<gold>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gold>"));
        player.sendMessage(mm.deserialize("<gray>Complete the tutorial to unlock all commands!</gray>"));

        tutorialManager.showCurrentStep(player, town);
    }

    /**
     * Track when players craft tutorial-related items
     */
    @EventHandler
    public void onCraftItem(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        Resident resident = TownyAPI.getInstance().getResident(player);
        if (resident == null || !resident.hasTown() || !resident.isMayor()) {
            return;
        }

        Town town = resident.getTownOrNull();
        if (town == null || !tutorialManager.isTutorialActive(town)) {
            return;
        }

        ItemStack result = event.getRecipe().getResult();
        ItemService.StructureTool toolType = itemService.getItemType(result);

        if (toolType == ItemService.StructureTool.TOWN_TOOL) {
            tutorialManager.onTownToolObtained(town, player);
        } else if (toolType == ItemService.StructureTool.EDIT_TOOL) {
            tutorialManager.onEditToolObtained(town, player);
        }
    }

    private boolean isCommandAllowed(String command) {
        // Check exact matches
        if (ALLOWED_EXACT_COMMANDS.contains(command)) {
            return true;
        }

        // Check prefix matches
        for (String prefix : ALLOWED_COMMAND_PREFIXES) {
            if (command.startsWith(prefix)) {
                return true;
            }
        }

        return false;
    }
}
