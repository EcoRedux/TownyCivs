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


        // Return FALSE if biome is in the banned list (structure cannot be placed here)
        // Return TRUE if biome is NOT in the banned list (structure can be placed)
        boolean result = !configContext.configItems.contains(biomeKey);
        return result;
    }

    @Override
    public void nokmessage(TownContext townContext, StringList configuration) {
        if (townContext.player != null) {
            org.bukkit.block.Biome computedBiome = townContext.structureCenterLocation.getBlock().getComputedBiome();
            townContext.player.sendMessage(MiniMessage.miniMessage().deserialize(
                "<gold>[TownyCivs]</gold> <red>Cannot place " + townContext.structure.name + " in biome: " + computedBiome.getKey().asString() + "</red>"));
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
