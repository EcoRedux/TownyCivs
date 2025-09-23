package cz.neumimto.towny.townycivs;

import co.aikar.commands.PaperCommandManager;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.palmergames.bukkit.towny.TownyAPI;
import cz.neumimto.towny.townycivs.commands.StructureCommands;
import cz.neumimto.towny.townycivs.config.ConfigurationService;
import cz.neumimto.towny.townycivs.Listeners.InventoryListener;
import cz.neumimto.towny.townycivs.Listeners.TownListener;
import cz.neumimto.towny.townycivs.mechanics.MechanicService;
import cz.neumimto.towny.townycivs.schedulers.FolliaScheduler;
import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.java.JavaPluginLoader;
import org.bukkit.scheduler.BukkitTask;
import space.arim.morepaperlib.MorePaperLib;

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
    private static BukkitTask task;
    public boolean reloading;
    @Inject
    public ConfigurationService configurationService;
    @Inject
    public FolliaScheduler structureScheduler;
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
        getLogger().info("""
                  
                  ______                          ______      __            _         \s
                 /_  __/___ _      ______  __  __/ ____/___  / /___  ____  (_)__  _____
                  / / / __ \\ | /| / / __ \\/ / / / /   / __ \\/ / __ \\/ __ \\/ / _ \\/ ___/
                 / / / /_/ / |/ |/ / / / / /_/ / /___/ /_/ / / /_/ / / / / /  __(__  )\s
                /_/  \\____/|__/|__/_/ /_/\\__, /\\____/\\____/_/\\____/_/ /_/_/\\___/____/ \s
                                        /____/                                        \s
                """);
        getLogger().info("Townycivs starting");

        if (!reloading) {
            injector = Guice.createInjector(new AbstractModule() {
                @Override
                protected void configure() {
                    bind(ConfigurationService.class);
                    bind(FolliaScheduler.class);
                    bind(StructureService.class);
                    bind(MechanicService.class);
                    bind(ItemService.class);
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

        injector.getInstance(StructureService.class).loadAll();
        if (!reloading) {
            PaperCommandManager manager = new PaperCommandManager(this);
            manager.registerCommand(injector.getInstance(StructureCommands.class));

            Map<String, Map<String, String>> translations = new HashMap<>();
            try (var is = getClass().getClassLoader().getResourceAsStream("lang/en-US.properties")) {
                Properties properties = new Properties();
                properties.load(is);

                translations.put("en_US", new HashMap<>((Map) properties));
            } catch (IOException e) {
                e.printStackTrace();
            }
            TownyAPI.getInstance().addTranslations(this, translations);


            Bukkit.getPluginManager().registerEvents(injector.getInstance(TownListener.class), this);
            Bukkit.getPluginManager().registerEvents(injector.getInstance(InventoryListener.class), this);
        }

        injector.getInstance(ItemService.class).registerRecipes();

        if (task != null) {
            task.cancel();
        }

        MORE_PAPER_LIB.scheduling().asyncScheduler().runAtFixedRate(
                injector.getInstance(FolliaScheduler.class),
                Duration.ZERO,
                Duration.of(1, ChronoUnit.SECONDS));


        reloading = true;
        getLogger().info("Townycivs started");
    }


    @Override
    public void onDisable() {
        getLogger().info("Townycivs disabled");
    }
}
