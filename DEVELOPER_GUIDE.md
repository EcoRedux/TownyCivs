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

### 3. Adding New Mechanics
To create a new mechanic:

1. Create a class implementing `Mechanic<T>` interface
2. Define your configuration type `T`
3. Register in `MechanicService.registerDefaults()`
4. Add mechanic ID to `Mechanics` class

Example:
```java
public class MyMechanic implements Mechanic<StringWrapper> {
    @Override
    public boolean check(TownContext ctx, StringWrapper config) {
        // Your logic here
        return true;
    }
    
    @Override
    public String id() {
        return "my_mechanic";
    }
    
    @Override
    public StringWrapper getNew() {
        return new StringWrapper();
    }
}
```

### 4. Adding New Structures
1. Create a `.conf` file in `src/main/resources/structures/`
2. Define structure properties, mechanics, and requirements
3. Restart/reload the plugin to load new structure

### 5. Debugging Tips
- Enable debug logging in plugin configuration
- Use `/townycivs reload` for quick config changes
- Check `TownyCivs.logger` for debug output
- Monitor structure ticking in `FolliaScheduler`

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

This guide provides the foundation for understanding and contributing to the TownyCivs codebase. The modular architecture makes it easy to extend functionality while maintaining compatibility with the Towny ecosystem.
