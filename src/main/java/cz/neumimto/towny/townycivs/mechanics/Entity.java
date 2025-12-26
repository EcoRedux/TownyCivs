package cz.neumimto.towny.townycivs.mechanics;

import cz.neumimto.towny.townycivs.mechanics.common.ItemList;
import cz.neumimto.towny.townycivs.mechanics.common.StringList;
import cz.neumimto.towny.townycivs.mechanics.common.StringWrapper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class Entity implements Mechanic<StringList> {

    @Override
    public boolean check(TownContext townContext, StringList configContext) {
        Location loc = townContext.structureCenterLocation;
        Block feet = loc.getBlock();
        Block head = feet.getRelative(BlockFace.UP);
        Block ground = feet.getRelative(BlockFace.DOWN);

        // Solid ground
        if (!ground.getType().isSolid()) return false;

        // Space for entity
        if (!feet.isPassable() || !head.isPassable()) return true;

        return true;
    }

    @Override
    public void postAction(TownContext townContext, StringList configContext) {
        Location centerLocation = townContext.structureCenterLocation;
        for(String entityName : configContext.configItems) {
            EntityType type = EntityType.valueOf(entityName.toUpperCase());
            centerLocation.getWorld().createEntity(centerLocation, type.getEntityClass());
        }
    }


    @Override
    public String id() {
        return Mechanics.ENTITY;
    }

    @Override
    public StringList getNew() {
        return null;
    }

    @Override
    public List<ItemStack> getGuiItems(TownContext townContext, StringList configContext) {
        List<ItemStack> guiItems = new ArrayList<>();
        MiniMessage mm = MiniMessage.miniMessage();

        for (String entityName: configContext.configItems) {
            EntityType type = EntityType.valueOf(entityName.toUpperCase());
            ItemStack entityItem = new ItemStack((Bukkit.getItemFactory().getSpawnEgg(type) != null) ? Bukkit.getItemFactory().getSpawnEgg(type) : Material.EGG);
            entityItem.editMeta(itemMeta -> {
                List<Component> lore = new ArrayList<>();
                lore.add(mm.deserialize("<yellow>Spawned Entity: " + type.name() + "</yellow>"));
                if (itemMeta.hasLore() && itemMeta.lore() != null) {
                    lore.addAll(itemMeta.lore());
                }
                itemMeta.lore(lore);
            });
            guiItems.add(entityItem);
        }

        return guiItems;
    }
}
