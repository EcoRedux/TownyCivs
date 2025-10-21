# TownyCivs

[![Java CI with Gradle](https://github.com/EcoRedux/TownyCivs/actions/workflows/gradle.yml/badge.svg?branch=master)](https://github.com/EcoRedux/TownyCivs/actions/workflows/gradle.yml) [![CodeFactor](https://www.codefactor.io/repository/github/ecoredux/townycivs/badge/master)](https://www.codefactor.io/repository/github/ecoredux/townycivs/overview/master)

Towny addon that adds automated farms, factories and administrative buildings into the Towny Ecosystem

The plugin has been inspired by the mod [MineColonies](https://www.curseforge.com/minecraft/mc-mods/minecolonies) and plugin [Civs](https://www.spigotmc.org/resources/civs.67350/) (hence its name) 

Basic gameplay:

 - Players buy structure blueprint from the ingame shop
 - Each blueprint has its own building requirements (specific block palette, required region size, biome placement, ....)
 - A town mayor/an assistant choose location for the structure within town claim
 - Residents proceeds to build the structure 
 - If minimal building requirements are met the structure becomes active (item rewards, permission node for town residents, ... )

The main idea is to encourage and reward players to build actual buildings instead of making one large cobblestone house in the middle of their town.

Everything is configurable - you can (and you should) create your own blueprints
For documentation check wiki

 - Region processing is done in an asychronous thread
 - Unlike Civs TownyCivs wont load any addition chunks when distributing region production

**Alphabuilds are not suitable for production env.**

For any questions ping ItsJules at the towny discord

## Requirements ##

Tested on:

- Paper 1.21.7
- Towny 0.101.2.0
- DecentHolograms

## Building from source

- `gradlew shadowJar`
- The jar is then located in path `build/libs/townycivs-{version}-all.jar`

## Installation

- drop the jar into plugins folder
- default configs might not be balanced to suit yours server economy

### Permissions

- Permission: `townycivs.administrative`
  - Ability to buy new blueprints

- Permission: `townycivs.architect`
  - Ability to place blueprint

Both permission should be given to town co/mayor/assistant

## Commands

- Theres only one command - `/toco` - an entrypoint for opening an inventory menu

## Dev Roadmap

- Add more default blueprints
- SQL storage
- Add warehouses and item filters via Item Frames
- Convoys and trade routes between towns/outposts 
- Option to spawn an immobilized NPC within a structure region instead of placing a container chest 
- Create a new war system that is based around raiding town supplies, stealing town production and attacking trade routes
