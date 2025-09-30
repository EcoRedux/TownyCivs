# TownyCivs Structure Building Guide - How to Build Structures In-Game

## Complete Structure Building Process

Based on the codebase analysis, TownyCivs uses a **blueprint system** with GUIs rather than direct build commands. Here's exactly how players build structures:

### Step 1: Access the Main Menu
```
/townycivs
/toco
/tco
/tci
```
This opens the main TownyCivs GUI menu (requires being in a town).

### Step 2: Navigate to Blueprints
From the main menu, players navigate to the **"Buy Blueprints"** section. This shows all available structures that the town can purchase.

### Step 3: Purchase a Blueprint
In the BlueprintsGui, players can:
- See all available structures with their requirements
- Click on a structure to **buy its blueprint** (if requirements are met)
- The blueprint item is added to their inventory

### Step 4: Place the Structure
After getting the blueprint item, players must:
- Go to the desired location in their town
- **Right-click with the blueprint item** to place the structure
- The system checks placement requirements and builds the structure

## Key Files and Classes

### 1. Command Entry Point
- **StructureCommands.java**: Main command handler
  - `/townycivs` - Opens main menu
  - `/townycivs structures` - Opens structures overview
  - `/townycivs structure <id>` - Shows specific structure info

### 2. GUI System
- **BlueprintsGui.java**: Handles blueprint purchasing
  - Shows available structures based on `BuyRequirements`
  - Processes blueprint purchases when clicked
  - Gives blueprint items to players

- **StructuresGui.java**: Shows town's existing structures
- **StructureGui.java**: Shows individual structure details

### 3. Core Services
- **StructureService.java**: Main structure management
  - `canBuy()` - Checks if player can buy structure
  - `buyBlueprint()` - Processes blueprint purchase
  - `toItemStack()` - Creates blueprint items
  - `findTownStructures()` - Gets town's structures

### 4. Structure Building Process
From the BlueprintsGui code, here's what happens when a player clicks a structure:

```java
// 1. Check if player can buy the structure
if (structureService.canBuy(townContext)) {
    // 2. Create blueprint item
    ItemStack clone = structureService.buyBlueprint(townContext);
    // 3. Add to player inventory
    whoClicked.getInventory().addItem(clone);
}
```

## Structure Configuration Requirements

Each structure (like coal-mine.conf) has:

### BuyRequirements
```hocon
BuyRequirements: [
  {
    Mechanic: permission
    Value: "townycivs.builds.farms.cactus"
  }
  {
    Mechanic: price
    Value: 500
  }
]
```

### Block Placement
```hocon
Blocks: {
  "minecraft:cactus": 9
  "tc:fence": 5
  "tc:fence_gate": 1
  "tc:sand": 3
  "!tc:container": 1
}
```

## How Players Actually Build Structures

### Method 1: GUI System (Primary)
1. Player runs `/townycivs` to open main menu
2. Navigate to "Buy Blueprints" section
3. Click on desired structure to purchase blueprint
4. Blueprint item appears in inventory
5. Go to build location and right-click with blueprint
6. Structure is built automatically if placement requirements are met

### Method 2: Direct Structure Commands
```
/townycivs structures    # View town's existing structures
/townycivs structure coalmine    # View specific structure details
```

## Requirements for Building

### Town Requirements
- Player must be in a town
- Town must meet structure's requirements (level, resources, etc.)

### Permission Requirements
- Player needs `townycivs.commands.common.mainmenu` permission
- Structure-specific permissions defined in config

### Resource Requirements
- Money (from `price` mechanic in BuyRequirements)
- Items (if specified in requirements)
- Town level/rank requirements

### Placement Requirements
- Must be within town boundaries
- Area must be clear (defined by `AreaRadius`)
- Respect `MaxCount` limits per structure type

## Technical Implementation

### Blueprint Item Creation
The system creates special items with:
- Custom model data
- Structure name as display name
- Lore with structure information
- NBT data identifying the structure type

### Block Placement System
When blueprint is used:
1. System reads `Blocks` section from structure config
2. Places blocks in the defined area pattern
3. Creates special blocks with "tc:" prefix for custom functionality
4. Registers structure in town's structure database

### Structure Registration
- Structure gets UUID and is stored in town's structure list
- Production/upkeep systems start running
- Structure appears in town's structure overview

## Missing Components (Need Implementation)

Based on the codebase analysis, these components may need implementation:

1. **Blueprint Item Usage Handler**: Event listener for right-clicking blueprint items
2. **Block Placement Logic**: System to actually place blocks from config
3. **Area Validation**: Check if placement area is suitable
4. **Structure Registration**: Add built structure to town's database

This is the complete structure building system in TownyCivs - it's GUI-driven with a blueprint purchase and placement workflow!