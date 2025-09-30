# TownyCivs Developer Guide

## Project Overview
TownyCivs is a Minecraft plugin that extends the Towny plugin, adding civilization-style structures and mechanics to towns. The plugin allows players to build and manage various structures like farms, mines, and warehouses that provide automated resource generation and town management features.

## Main Entry Point
**Main Class:** `cz.neumimto.towny.townycivs.TownyCivs`
- Extends `JavaPlugin` (Bukkit/Spigot plugin framework)
- Uses Google Guice for dependency injection
- Entry points: `onEnable()` and `onDisable()`

## Architecture Overview

### 1. Core Services (Singleton Pattern)
The plugin uses a service-oriented architecture with dependency injection:

```
TownyCivs (Main Class)
├── ConfigurationService - Handles all configuration loading/management
├── StructureService - Core business logic for structure management
├── MechanicService - Manages structure mechanics/requirements
├── ItemService - Handles custom items and recipes
├── FolliaScheduler - Manages timed operations (structure ticking)
└── SubclaimService - Handles region/claim management
```

### 2. Package Structure

#### `/mechanics` - Core Game Logic
- **Mechanic Interface**: Base contract for all structure requirements/effects
- **Concrete Mechanics**: Permission, ItemProduction, ItemUpkeep, Price, etc.
- **TownContext**: Provides context data for mechanic evaluation
- **MechanicService**: Registry and manager for all mechanics

#### `/model` - Data Models
- **LoadedStructure**: Runtime representation of placed structures
- **Region**: Defines structure boundaries and areas
- **BlueprintItem**: Represents structure blueprints
- **StructureAndCount**: Utility for structure counting

#### `/config` - Configuration System
- **Structure**: Defines structure properties, requirements, and behaviors
- **PluginConfig**: Main plugin configuration
- **ConfigurationService**: Loads and manages all configurations

#### `/commands` - Command System
- **StructureCommands**: Main command handler using ACF (Aikar's Command Framework)
- Commands: `/townycivs`, `/toco`, `/tco`, `/toc`, `/toci`, `/tci`

#### `/gui` - User Interface
- **TCGui**: Base GUI class
- **MainMenuGui**: Primary interface for players
- **StructuresGui**: Browse available structures
- **StructureGui**: Individual structure management
- **BlueprintsGui**: Blueprint purchasing interface

#### `/Listeners` - Event Handling
- **TownListener**: Handles Towny-related events
- **InventoryListener**: Manages GUI interactions

## Development Flow

### 1. Plugin Initialization (onEnable)
```
1. Setup Guice injector with service bindings
2. Register default mechanics via MechanicService
3. Load configurations (structures, settings)
4. Load existing structures from storage
5. Register commands and event listeners
6. Setup item recipes
7. Start structure scheduler for ticking
```

### 2. Structure Lifecycle
```
Structure Definition (Config) 
    ↓
Blueprint Creation (ItemService)
    ↓
Player Purchase (Commands + GUI)
    ↓
Structure Placement (StructureService)
    ↓
Runtime Management (LoadedStructure)
    ↓
Periodic Execution (FolliaScheduler)
```

### 3. Mechanic System
The plugin uses a flexible mechanic system for structure requirements and effects:

```
Mechanic Interface
├── check() - Validates if mechanic requirements are met
├── postAction() - Executes mechanic effects
├── nokmessage() - Sends failure message
└── okmessage() - Sends success message

Built-in Mechanics:
├── Permission - Requires specific permission
├── Price - Costs money/items
├── ItemProduction - Produces items
├── ItemUpkeep - Consumes items
├── TownUpkeep - Town maintenance costs
├── Biome - Requires specific biome
├── WorldReq - World restrictions
└── Y-level restrictions (YAbove, YBellow)
```

## Adding New Content - Comprehensive Guide

### 1. Creating New Structures

Structures are the core content of TownyCivs. Here's how to create them:

#### Step 1: Create Structure Configuration File
Create a new `.conf` file in `src/main/resources/structures/` using HOCON format:

```hocon
# Example: advanced-farm.conf
Id: advanced_farm
Name: "Advanced Farm"
Description: [
  "An advanced farming facility that produces"
  "<green>multiple crops</green> automatically"
  "Requires <yellow>fertilizer</yellow> to operate"
]

# Visual Properties
Material: golden_hoe
CustomModelData: 10
InventorySize: 27

# Placement Properties
MaxCount: 3
AreaRadius: 3x3x2
Period: 60  # Ticks between production cycles
SaveEachNTicks: 100

# Economy
TownPointPrice: 5

# Requirements to purchase blueprint
BuyRequirements: [
  {
    Mechanic: permission
    Value: "townycivs.builds.farms.advanced"
  }
  {
    Mechanic: price
    Value: 1000
  }
  {
    Mechanic: town_rank
    Value: 3
  }
]

# Requirements to place structure
PlaceRequirements: [
  {
    Mechanic: biome
    Value: ["plains", "forest"]
  }
  {
    Mechanic: y_above
    Value: 60
  }
]

# What the structure produces
Production: [
  {
    Mechanic: item
    Value: {
      Material: "wheat"
      Amount: 50
    }
  }
  {
    Mechanic: item
    Value: {
      Material: "carrot"
      Amount: 30
    }
  }
]

# What the structure consumes
Upkeep: [
  {
    Mechanic: item_required
    Value: {
      Material: "bone_meal"
      Amount: 5
    }
  }
  {
    Mechanic: town_upkeep
    Value: 2
  }
]

# Required blocks for building (optional)
Blocks: {
  "farmland": 9
  "water": 1
  "chest": 1
}
```

#### Step 2: Test and Reload
```bash
# Reload plugin configurations
/townycivs reload
```

### 2. Creating Custom Mechanics

Mechanics define the behavior and requirements for structures.

#### Step 1: Create Mechanic Class
```java
package cz.neumimto.towny.townycivs.mechanics;

import cz.neumimto.towny.townycivs.mechanics.common.StringWrapper;

public class WeatherRequirement implements Mechanic<StringWrapper> {
    
    @Override
    public boolean check(TownContext ctx, StringWrapper config) {
        String requiredWeather = config.value;
        String currentWeather = ctx.loadedStructure.getWorld().getEnvironment().name();
        
        return currentWeather.equalsIgnoreCase(requiredWeather);
    }
    
    @Override
    public void nokmessage(TownContext ctx, StringWrapper config) {
        ctx.resident.sendMessage("Weather must be " + config.value);
    }
    
    @Override
    public void okmessage(TownContext ctx, StringWrapper config) {
        ctx.resident.sendMessage("Weather requirement met!");
    }
    
    @Override
    public String id() {
        return "weather_req";
    }
    
    @Override
    public StringWrapper getNew() {
        return new StringWrapper();
    }
}
```

#### Step 2: Register Mechanic
Add to `MechanicService.registerDefaults()`:
```java
// In MechanicService.java
placeReq(new WeatherRequirement());
```

#### Step 3: Add Mechanic ID to Constants
```java
// In Mechanics.java class
public static final String WEATHER_REQ = "weather_req";
```

#### Step 4: Use in Structure Configuration
```hocon
PlaceRequirements: [
  {
    Mechanic: weather_req
    Value: "NORMAL"
  }
]
```

### 3. Creating Complex Production Mechanics

For advanced item production with custom logic:

```java
public class RandomProduction implements Mechanic<RandomProductionConfig> {
    
    @Override
    public boolean check(TownContext ctx, RandomProductionConfig config) {
        // Check if structure can produce (inventory space, etc.)
        return true;
    }
    
    @Override
    public void postAction(TownContext ctx, RandomProductionConfig config) {
        Random random = new Random();
        
        for (RandomProductionConfig.RandomItem item : config.items) {
            if (random.nextDouble() < item.chance) {
                ItemStack produced = new ItemStack(
                    Material.valueOf(item.material), 
                    item.amount
                );
                
                // Add to structure inventory
                StructureInventoryService service = TownyCivs.injector
                    .getInstance(StructureInventoryService.class);
                service.addItemProduction(ctx.loadedStructure, Set.of(produced));
            }
        }
    }
    
    @Override
    public String id() {
        return "random_production";
    }
    
    @Override
    public RandomProductionConfig getNew() {
        return new RandomProductionConfig();
    }
}

// Configuration class
public class RandomProductionConfig {
    public List<RandomItem> items = new ArrayList<>();
    
    public static class RandomItem {
        public String material;
        public int amount;
        public double chance; // 0.0 to 1.0
    }
}
```

### 4. Adding New GUI Elements

#### Step 1: Create Custom GUI Class
```java
public class CustomStructureGui extends TCGui {
    
    @Inject
    private StructureService structureService;
    
    public CustomStructureGui(Player player, LoadedStructure structure) {
        super(player, 54, "Custom Structure Management");
        this.structure = structure;
        build();
    }
    
    @Override
    protected void build() {
        // Add custom buttons and functionality
        ItemStack upgradeButton = new ItemStack(Material.DIAMOND_PICKAXE);
        ItemMeta meta = upgradeButton.getItemMeta();
        meta.setDisplayName("§6Upgrade Structure");
        meta.setLore(Arrays.asList(
            "§7Click to upgrade this structure",
            "§7Cost: §e500 coins"
        ));
        upgradeButton.setItemMeta(meta);
        
        inventory.setItem(22, upgradeButton);
        
        // Add close button
        addCloseButton();
    }
    
    @Override
    public void onClick(InventoryClickEvent event) {
        event.setCancelled(true);
        
        if (event.getSlot() == 22) {
            // Handle upgrade logic
            handleUpgrade();
        }
        
        super.onClick(event);
    }
    
    private void handleUpgrade() {
        // Custom upgrade logic
        player.sendMessage("§aStructure upgraded!");
        player.closeInventory();
    }
}
```

#### Step 2: Register GUI in Listener
Update `InventoryListener.java` to handle your new GUI.

### 5. Adding New Commands

#### Step 1: Add to StructureCommands
```java
@CommandAlias("tco|toc|townycivs")
public class StructureCommands extends BaseCommand {
    
    @Subcommand("inspect")
    @Description("Inspect nearby structures")
    @CommandPermission("townycivs.inspect")
    public void onInspect(Player player) {
        // Custom command logic
        Location loc = player.getLocation();
        List<LoadedStructure> nearby = structureService.getNearbyStructures(loc, 10);
        
        if (nearby.isEmpty()) {
            player.sendMessage("§cNo structures found nearby.");
            return;
        }
        
        player.sendMessage("§aNearby structures:");
        for (LoadedStructure structure : nearby) {
            player.sendMessage("§7- " + structure.getStructure().name + 
                " at " + structure.getLocation().toString());
        }
    }
    
    @Subcommand("upgrade")
    @Description("Upgrade a structure")
    @CommandPermission("townycivs.upgrade")
    @CommandCompletion("@structures")
    public void onUpgrade(Player player, String structureId) {
        // Upgrade logic
        LoadedStructure structure = structureService.getStructureAt(player.getLocation());
        if (structure == null) {
            player.sendMessage("§cNo structure found at your location.");
            return;
        }
        
        // Perform upgrade
        upgradeService.upgradeStructure(structure, player);
    }
}
```

### 6. Creating Structure Templates and Schematics

#### Step 1: Define Block Templates
```hocon
# In structure configuration
Blocks: {
  "stone": 64
  "iron_block": 9
  "chest": 4
  "redstone": 16
}

# Optional: Define exact schematic
Schematic: {
  File: "schematics/advanced_farm.schem"
  AutoBuild: true
  RequireExactMatch: false
}
```

#### Step 2: Implement Schematic Support
```java
public class SchematicService {
    
    public boolean validateSchematic(LoadedStructure structure, String schematicFile) {
        // Load and validate schematic against placed blocks
        // Return true if structure matches requirements
        return true;
    }
    
    public void buildSchematic(Location location, String schematicFile) {
        // Auto-build schematic at location
        // Useful for premium structures or admin placement
    }
}
```

### 7. Adding Progression and Upgrades

#### Step 1: Create Upgrade System
```java
public class StructureUpgrade {
    public String fromStructureId;
    public String toStructureId;
    public List<LoadedPair<Mechanic<?>, ?>> requirements;
    public boolean preserveInventory;
    public double costMultiplier;
}
```

#### Step 2: Define Upgrade Paths in Config
```hocon
# upgrades.conf
Upgrades: [
  {
    From: "wheat_farm"
    To: "advanced_farm"
    Requirements: [
      {
        Mechanic: price
        Value: 500
      }
      {
        Mechanic: item_required
        Value: {
          Material: "diamond"
          Amount: 2
        }
      }
    ]
    PreserveInventory: true
    CostMultiplier: 1.5
  }
]
```

### 8. Integration with External Plugins

#### Step 1: Add Plugin Dependencies
```java
// In onEnable()
if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
    new TownyCivsPlaceholders().register();
}
```

#### Step 2: Create Placeholder Integration
```java
public class TownyCivsPlaceholders extends PlaceholderExpansion {
    
    @Override
    public String getIdentifier() {
        return "townycivs";
    }
    
    @Override
    public String onPlaceholderRequest(Player player, String identifier) {
        if (identifier.equals("structures_owned")) {
            Resident resident = TownyAPI.getInstance().getResident(player);
            return String.valueOf(structureService.getOwnedStructures(resident).size());
        }
        
        return null;
    }
}
```

### 9. Testing New Content

#### Step 1: Unit Tests
```java
@Test
public void testCustomMechanic() {
    WeatherRequirement mechanic = new WeatherRequirement();
    TownContext context = createMockContext();
    StringWrapper config = new StringWrapper();
    config.value = "NORMAL";
    
    boolean result = mechanic.check(context, config);
    assertTrue(result);
}
```

#### Step 2: Integration Testing
1. Use `/townycivs reload` to test configuration changes
2. Test structure placement and functionality
3. Verify GUI interactions work correctly
4. Check command permissions and completions

### 10. Performance Considerations

#### Optimizing Structure Ticking
```java
// Use async processing for heavy operations
@Override
public void postAction(TownContext ctx, ConfigType config) {
    // Heavy computation
    CompletableFuture.runAsync(() -> {
        // Process production logic
        processProduction(ctx, config);
    }).thenRun(() -> {
        // Update on main thread
        Bukkit.getScheduler().runTask(plugin, () -> {
            updateStructureInventory(ctx);
        });
    });
}
```

#### Database Optimization
- Use batch operations for multiple structure updates
- Implement caching for frequently accessed data
- Consider async storage operations for large datasets

## Advanced Content Examples

### Multi-Block Structures
```hocon
Id: windmill
AreaRadius: 5x5x7
RequiredBlocks: {
  "oak_log": 12
  "oak_planks": 24
  "white_wool": 16
}
BuildRequirements: [
  {
    Mechanic: exact_blocks
    Value: true
  }
]
```

### Conditional Production
```hocon
Production: [
  {
    Mechanic: conditional_item
    Value: {
      Condition: "time_day"
      Items: [
        {
          Material: "wheat"
          Amount: 20
        }
      ]
    }
  }
]
```

## Key Concepts for New Developers

### 1. Structure Configuration
Structures are defined in HOCON format files in `/structures/` directory:
- Define requirements for buying, placing, and building
- Set production/upkeep mechanics
- Configure appearance and behavior

### 2. Dependency Injection
The plugin uses Google Guice for dependency management:
- Services are automatically injected with `@Inject`
- All services are singletons
- Main injector created in `TownyCivs.onEnable()`

### 3. Integration with Towny
- Depends on Towny plugin for town/resident data
- Uses TownyAPI for accessing town information
- Integrates with Towny's permission and messaging systems

### 4. Folia Support
- Plugin supports Folia (Paper fork for multi-threading)
- Uses MorePaperLib for scheduling compatibility
- Async scheduling for structure ticking

## Getting Started as a Developer

### 1. Environment Setup
```bash
# Clone the repository
git clone <repository-url>

# Build the project
./gradlew build

# The compiled JAR will be in build/libs/
```

### 2. Development Dependencies
- **Towny**: Core dependency for town management
- **Vault**: Economy integration (optional)
- **Google Guice**: Dependency injection
- **ACF**: Command framework
- **Night Config**: Configuration system

### 3. Content Creation Workflow
1. **Plan your content** - Define what type of structure/mechanic you want
2. **Create configuration** - Write the `.conf` file with all properties
3. **Implement mechanics** - Create custom mechanics if needed
4. **Test thoroughly** - Use `/townycivs reload` and test all functionality
5. **Add documentation** - Update guides and help files
6. **Submit for review** - Follow contribution guidelines

### 4. Debugging Tips
- Enable debug logging in plugin configuration
- Use `/townycivs reload` for quick config changes
- Check `TownyCivs.logger` for debug output
- Monitor structure ticking in `FolliaScheduler`
- Use `/townycivs debug` command for runtime information

## Testing
- Test structures are defined in `/test` directory
- Unit tests cover core mechanics and services
- Integration tests verify Towny compatibility

## Important Notes
- Plugin requires Java 17+ (Paper 1.19+)
- Folia compatibility maintained throughout
- Thread-safe operations for structure management
- Database support via Storage interface (currently Flatfile)

## Contributing Guidelines
1. Follow existing code style and patterns
2. Add unit tests for new mechanics
3. Update configuration documentation
4. Test Folia compatibility
5. Ensure Towny integration works properly
6. Document all new features and mechanics
7. Test performance impact of new content

This comprehensive guide provides everything needed to understand and extend the TownyCivs codebase. The modular architecture makes it easy to add new content while maintaining compatibility with the Towny ecosystem.
