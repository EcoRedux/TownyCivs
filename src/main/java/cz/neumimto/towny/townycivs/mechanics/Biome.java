package cz.neumimto.towny.townycivs.mechanics;

import cz.neumimto.towny.townycivs.mechanics.common.StringList;
import net.kyori.adventure.text.minimessage.MiniMessage;

public class Biome implements Mechanic<StringList> {

    @Override
    public boolean check(TownContext townContext, StringList configContext) {
        if (townContext.structureCenterLocation == null) {
            return true; // Allow if we can't check
        }

        org.bukkit.block.Biome computedBiome = townContext.structureCenterLocation.getBlock().getComputedBiome();
        String biomeKey = computedBiome.getKey().asString();

        if (configContext.whitelist) {
            // Whitelist mode: Return TRUE if biome IS in the list
            return configContext.configItems.contains(biomeKey);
        } else {
            // Blacklist mode (default): Return TRUE if biome is NOT in the list
            return !configContext.configItems.contains(biomeKey);
        }
    }

    @Override
    public void nokmessage(TownContext townContext, StringList configuration) {
        if (townContext.player != null) {
            org.bukkit.block.Biome computedBiome = townContext.structureCenterLocation.getBlock().getComputedBiome();
            String biomeKey = computedBiome.getKey().asString();
            
            if (configuration.whitelist) {
                 townContext.player.sendMessage(MiniMessage.miniMessage().deserialize(
                    "<gold>[TownyCivs]</gold> <red>Cannot place " + townContext.structure.name + " in biome: " + biomeKey + ". Allowed biomes: " + String.join(", ", configuration.configItems) + "</red>"));
            } else {
                townContext.player.sendMessage(MiniMessage.miniMessage().deserialize(
                    "<gold>[TownyCivs]</gold> <red>Cannot place " + townContext.structure.name + " in biome: " + biomeKey + "</red>"));
            }
        }
    }

    @Override
    public String id() {
        return Mechanics.BIOME;
    }


    @Override
    public StringList getNew() {
        return new StringList();
    }
}
