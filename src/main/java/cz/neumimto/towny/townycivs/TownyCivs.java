package cz.neumimto.towny.townycivs;

import co.aikar.commands.PaperCommandManager;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.palmergames.bukkit.towny.TownyAPI;
import cz.neumimto.towny.townycivs.Listeners.TownListener;
import cz.neumimto.towny.townycivs.commands.AdminCommands;
import cz.neumimto.towny.townycivs.commands.StructureCommands;
import cz.neumimto.towny.townycivs.config.ConfigurationService;
import cz.neumimto.towny.townycivs.Listeners.InventoryListener;
import cz.neumimto.towny.townycivs.mechanics.MechanicService;
import cz.neumimto.towny.townycivs.power.PowerService;
import cz.neumimto.towny.townycivs.schedulers.FoliaScheduler;
import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.java.JavaPluginLoader;
import org.bukkit.scheduler.BukkitTask;
import space.arim.morepaperlib.MorePaperLib;
import space.arim.morepaperlib.scheduling.ScheduledTask;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TownyCivs extends JavaPlugin {

    public static Logger logger;

    public static TownyCivs INSTANCE;

    public static Injector injector;
    public static MorePaperLib MORE_PAPER_LIB;
    private static ScheduledTask schedulerTask;
    public static volatile boolean schedulerEnabled = true;
    public boolean reloading;
    @Inject
    public ConfigurationService configurationService;
    @Inject
    public FoliaScheduler structureScheduler;
    @Inject
    public StructureService structureService;
    @Inject
    public MechanicService mechanicService;
    @Inject
    private ItemService itemService;

    public TownyCivs() {
        super();
    }

    protected TownyCivs(JavaPluginLoader loader, PluginDescriptionFile description, File dataFolder, File file) {
        super(loader, description, dataFolder, file);
    }


    @Override
    public void onEnable() {
        TownyCivs.logger = getLogger();
        INSTANCE = this;
        MORE_PAPER_LIB = new MorePaperLib(this);
        getLogger().info("TownyCivs starting");

        if (!reloading) {
            injector = Guice.createInjector(new AbstractModule() {
                @Override
                protected void configure() {
                    bind(cz.neumimto.towny.townycivs.db.Flatfile.class).in(javax.inject.Singleton.class);
                    bind(cz.neumimto.towny.townycivs.db.IStorage.class).to(cz.neumimto.towny.townycivs.db.Flatfile.class);
                    bind(ConfigurationService.class);
                    bind(FoliaScheduler.class);
                    bind(StructureService.class);
                    bind(MechanicService.class);
                    bind(ItemService.class);
                    bind(cz.neumimto.towny.townycivs.tutorial.TutorialManager.class);
                    bind(cz.neumimto.towny.townycivs.tutorial.TutorialListener.class);
                    bind(cz.neumimto.towny.townycivs.power.PowerService.class);
                }
            });
        }

        injector.injectMembers(this);

        injector.getInstance(MechanicService.class).registerDefaults();
        ConfigurationService configurationService = injector.getInstance(ConfigurationService.class);
        try {
            configurationService.load(getDataFolder().toPath());
        } catch (IOException e) {
            e.printStackTrace();
            logger.log(Level.SEVERE, "Unable to load configuration " + e.getMessage());
        }

        // Initialize Storage BEFORE loading structures
        injector.getInstance(cz.neumimto.towny.townycivs.db.Flatfile.class).init();

        // Load Structures
        injector.getInstance(StructureService.class).loadAll();

        if (!reloading) {
            PaperCommandManager manager = new PaperCommandManager(this);
            manager.registerCommand(injector.getInstance(StructureCommands.class));
            manager.registerCommand(injector.getInstance(AdminCommands.class));

            // --- RESTORED MISSING TRANSLATION CODE ---
            Map<String, Map<String, String>> translations = new HashMap<>();
            try (var is = getClass().getClassLoader().getResourceAsStream("lang/en-US.properties")) {
                if (is != null) {
                    Properties properties = new Properties();
                    properties.load(is);
                    translations.put("en_US", new HashMap<>((Map) properties));
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            TownyAPI.getInstance().addTranslations(this, translations);
            // -----------------------------------------

            Bukkit.getPluginManager().registerEvents(injector.getInstance(TownListener.class), this);
            Bukkit.getPluginManager().registerEvents(injector.getInstance(InventoryListener.class), this);
            Bukkit.getPluginManager().registerEvents(injector.getInstance(cz.neumimto.towny.townycivs.tutorial.TutorialListener.class), this);
            Bukkit.getPluginManager().registerEvents(injector.getInstance(cz.neumimto.towny.townycivs.Listeners.PowerToolListener.class), this);
        }

        injector.getInstance(ItemService.class).registerRecipes();

        startNewScheduler();

        reloading = true;
        getLogger().info("TownyCivs started");
    }


    @Override
    public void onDisable() {
        getLogger().info("TownyCivs disabled");
        stopCurrentScheduler();



        // Clean up all power line entities on shutdown
        try {
            PowerService powerService = injector.getInstance(PowerService.class);
            if (powerService != null) {
                // Save power data for every town
                for (com.palmergames.bukkit.towny.object.Town town : com.palmergames.bukkit.towny.TownyAPI.getInstance().getTowns()) {
                    powerService.saveTownPower(town.getUUID());
                }

                // Remove visual entities so they don't get stuck in the world while the plugin is off
                powerService.cleanupAllPowerLines();
            }
            getLogger().info("Power line entities cleaned up");
        } catch (Exception e) {
            getLogger().warning("Failed to clean up power lines and save the grid: " + e.getMessage());
        }
    }

    public static void stopCurrentScheduler() {
        schedulerEnabled = false;
        if (schedulerTask != null) {
            schedulerTask.cancel();
            schedulerTask = null;
        }
        logger.info("Stopped existing FoliaScheduler task");
    }

    public static void startNewScheduler() {
        if (schedulerTask != null && !schedulerTask.isCancelled()) {
            stopCurrentScheduler();
        }
        try {
            schedulerEnabled = true;
            schedulerTask = MORE_PAPER_LIB.scheduling().asyncScheduler().runAtFixedRate(
                    injector.getInstance(FoliaScheduler.class),
                    Duration.ZERO,
                    Duration.of(1, ChronoUnit.SECONDS)
            );
            logger.info("Started new FoliaScheduler task");
        } catch (Exception e) {
            logger.severe("Failed to start new scheduler task: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
