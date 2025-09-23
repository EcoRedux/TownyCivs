# TownyCivs Player Guide

## What is TownyCivs?

TownyCivs is a powerful Minecraft plugin that extends the Towny plugin by adding automated structures, production systems, and town management features. It allows towns to build various automated farms, mines, and storage facilities that produce resources over time, creating a more complex and engaging town economy system.

## Prerequisites

- You must be a resident of a Towny town to use TownyCivs features
- Your town needs sufficient "Town Points" to build structures
- You need appropriate permissions within your town

## Getting Started

### 1. Obtaining the Basic Tools

TownyCivs provides two essential tools for managing structures:

#### Town Administration Tool
- **Recipe**: Book + Emerald = Town Administration Tool
- **Use**: Right-click to access the main menu and buy new blueprints
- **Appearance**: Enchanted Book with custom model

#### Structure Edit Tool
- **Recipe**: Paper + Wooden Shovel = Structure Edit Tool
- **Use**: Right-click within a structure area to open the structure menu
- **Appearance**: Paper with custom model

### 2. Basic Commands

- `/townycivs` or `/toco` - Opens the main menu
- `/toco structures` - View available structures
- `/toco structure <id>` - View specific structure information
- `/toco help [page]` - Display help information
- `/toco reload` - Admin command to reload the plugin

## Understanding the Town Points System

Different town sizes have different Town Point allocations:

- **Ruins**: 0 points
- **Settlement**: 10 points
- **Hamlet**: 20 points
- **Village**: 60 points
- **Town**: 100 points
- **Large Town**: 140 points
- **City**: 200 points
- **Large City**: 240 points
- **Metropolis**: 280 points

Each structure costs Town Points to build, so plan your town's growth carefully!

## Available Structures

### Farms

#### Wheat Farm
- **ID**: wheat_farm
- **Cost**: 250 coins + 2 Town Points
- **Production**: 30 wheat every 20 ticks
- **Max Count**: 5 per town
- **Area**: 2x2x1 blocks
- **Requirements**: `townycivs.builds.farms.wheat` permission
- **Upkeep**: 1 town upkeep cost
- **Description**: Automatic wheat production

#### Apple Farm
- **Production**: Apples over time
- **Similar mechanics to wheat farm**

#### Cactus Farm  
- **Production**: Automatic cactus harvesting
- **Efficient for renewable resources**

#### Potato Farm
- **Production**: Automatic potato harvesting
- **Food production for your town**

### Mines

#### Coal Mine
- **ID**: coalmine
- **Cost**: 500 coins + 2 Town Points
- **Production**: 10 coal every 100 ticks
- **Max Count**: 1 per town
- **Area**: 5x6x5 blocks
- **Requirements**: `townycivs.builds.farms.cactus` permission
- **Additional Tax**: 20 per cycle
- **Tags**: Factory
- **Description**: Automatic coal extraction

#### Iron Mine
- **Production**: Iron ore extraction
- **Higher tier mining operation**

### Storage

#### Warehouse
- **ID**: warehouse
- **Cost**: 1000 coins
- **Function**: Central storage for farm and factory output
- **Max Count**: 1 per town
- **Area**: 5x5x5 blocks
- **Requirements**: 
  - `townycivs.builds.storage.warehouse` permission
  - Must have a wheat farm built first
- **Special**: Contains 20 container blocks
- **Upkeep**: 1 town upkeep cost

## How Structures Work

### Building Process

1. **Purchase Blueprint**: Use the Town Administration Tool to buy structure blueprints
2. **Check Requirements**: Ensure you have:
   - Sufficient coins in town bank
   - Required permissions
   - Available Town Points
   - Any prerequisite structures
3. **Place Structure**: Use blueprints to place structures in valid locations
4. **Maintain**: Pay upkeep costs to keep structures running

### Production Mechanics

- **Tick System**: Structures produce items on a timer (measured in server ticks)
- **Automatic Collection**: Items are automatically added to structure inventories
- **Warehouse Integration**: Production can be directed to warehouses for centralized storage
- **Upkeep Required**: Structures need regular maintenance to continue producing

### Structure Management

- **Structure Edit Tool**: Right-click within a structure's area to:
  - View production status
  - Check inventory
  - Manage settings
  - Monitor upkeep requirements

## Permission System

### Player Roles

#### Town Administrative (`townycivs.administrative`)
- Can manage town structures
- Administrative oversight

#### Architect (`townycivs.architect`)
- Can design and place structures
- Technical building permissions

#### Mayor (`townycivs.mayor`)
- Can buy new blueprints using town funds
- Highest level town management

### Structure-Specific Permissions

- `townycivs.builds.farms.wheat` - Build wheat farms
- `townycivs.builds.farms.cactus` - Build cactus farms  
- `townycivs.builds.storage.warehouse` - Build warehouses
- `townycivs.commands.common.mainmenu` - Access main GUI

## GUI System

### Main Menu
Access with `/townycivs` or right-click Town Administration Tool:
- **Help** (H) - Access help pages
- **Bought Blueprints** (B) - View purchased blueprints
- **Available Structures** (S) - Browse all structures

### Structure Browser
- View all available structures
- See build requirements
- Check production rates
- Compare costs and benefits

### Structure Information
- Detailed stats for each structure type
- Production schedules
- Requirement lists
- Upkeep costs

## Economic Considerations

### Costs
- **Initial Purchase**: Coins from town bank
- **Town Points**: Limited resource based on town size
- **Upkeep**: Ongoing maintenance costs
- **Additional Taxes**: Some structures have extra fees

### Benefits
- **Automated Production**: Continuous resource generation
- **Town Growth**: Support larger populations with resources
- **Economic Stability**: Reliable income streams
- **Strategic Advantage**: Competitive edge over other towns

## Tips for Success

### Planning Your Town
1. **Start Small**: Begin with basic farms like wheat
2. **Build Storage Early**: Warehouses prevent resource overflow
3. **Balance Growth**: Don't use all Town Points immediately
4. **Plan Locations**: Structure areas cannot overlap

### Resource Management
1. **Monitor Upkeep**: Ensure town bank has sufficient funds
2. **Optimize Production**: Choose structures that complement each other
3. **Storage Strategy**: Build warehouses before high-production structures
4. **Upgrade Path**: Plan structure progression as town grows

### Teamwork
1. **Assign Roles**: Give trusted members appropriate permissions
2. **Coordinate Building**: Avoid duplicate structures
3. **Share Resources**: Use warehouses for community storage
4. **Plan Together**: Discuss town development strategy

## Troubleshooting

### Common Issues

#### "You need to be resident of a town for this action"
- Join a Towny town before using TownyCivs features

#### "The town has ran out of space to build"
- Upgrade your town size to get more Town Points
- Remove unused structures to free up points

#### Structure not producing
- Check upkeep payments are current
- Verify structure meets placement requirements
- Ensure structure area has proper blocks

#### Cannot access menus
- Check you have the required permissions
- Ensure you're using the correct tools
- Verify you're within structure boundaries when using edit tool

### Getting Help
- Use `/toco help` for in-game assistance
- Check with town mayors for permission issues
- Contact server administrators for technical problems

## Advanced Features

### Biome Restrictions
Some structures may be restricted in certain biomes. Check structure information for biome requirements.

### Block Requirements
Structures may require specific block types in their build area:
- Containers: Chests, barrels, trapped chests
- Building materials: Various wood, stone, and specialized blocks
- Infrastructure: Walls, fences, gates as specified

### Multi-Structure Dependencies
Some structures require others to be built first:
- Warehouses typically require farms
- Advanced structures may need basic infrastructure
- Plan your building order accordingly

## Configuration for Admins

The plugin uses several configuration files:
- `settings.conf` - Main plugin settings
- `structures/*.conf` - Individual structure definitions
- `gui/*.conf` - Interface layouts
- Language files for internationalization

This comprehensive system creates a rich town-building experience that rewards planning, cooperation, and economic management within the Towny framework.
