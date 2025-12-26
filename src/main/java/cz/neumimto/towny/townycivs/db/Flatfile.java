package cz.neumimto.towny.townycivs.db;

import cz.neumimto.towny.townycivs.ItemService;
import cz.neumimto.towny.townycivs.StructureInventoryService;
import cz.neumimto.towny.townycivs.TownyCivs;
import cz.neumimto.towny.townycivs.config.ConfigurationService;
import cz.neumimto.towny.townycivs.model.LoadedStructure;
import org.bukkit.Location;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Level;

@Singleton
public final class Flatfile implements IStorage {

    private Path storage;

    @Inject private ItemService itemService;
    @Inject private ConfigurationService configurationService;
    @Inject private StructureInventoryService structureInventoryService;
    private final Object fileLock = new Object();

    @Override
    public void init() {
        storage = TownyCivs.INSTANCE.getDataFolder().toPath().resolve("storage");
        storage.toFile().mkdirs();
    }

    /**
     * Saves a structure under its town file.
     * storage/<town-uuid>.yml
     */
    @Override
    public void save(LoadedStructure structure) {
        Path file = storage.resolve(structure.town + ".yml");
        synchronized (fileLock) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file.toFile());

            String path = structure.uuid.toString();

            yaml.set(path + ".structureId", structure.structureId);
            yaml.set(path + ".center", structure.center);
            yaml.set(path + ".editMode", structure.editMode.get());
            yaml.set(path + ".lastTickTime", structure.lastTickTime);
            yaml.set(path + ".batteryCharge", structure.savedBatteryCharge);
            yaml.set(path + ".toggleTitle", structure.toggleTitle.get());

            // Save inventories
            List<Map<String, Object>> invList = new ArrayList<>();
            ItemStack inventoryBlocker = itemService.getInventoryBlocker();

            for (Map.Entry<Location, Inventory> entry : structure.inventory.entrySet()) {
                Map<String, Object> inv = new LinkedHashMap<>();
                inv.put("location", entry.getKey());

                List<ItemStack> contents = new ArrayList<>();
                for (ItemStack item : entry.getValue().getContents()) {
                    if (item == null || item.equals(inventoryBlocker)) continue;
                    contents.add(item);
                }
                inv.put("content", contents);
                invList.add(inv);
            }

            yaml.set(path + ".inventory", invList);

            try {
                yaml.save(file.toFile());
            } catch (IOException e) {
                TownyCivs.logger.log(Level.SEVERE, "Could not save structure " + structure.uuid + " for town " + structure.town, e);
            }
        }
    }
    /**
     * Removes a structure UUID from its town file.
     */
    @Override
    public void remove(UUID uuid) {
        TownyCivs.logger.info("Removing Structure " + uuid);

        File[] files = storage.toFile().listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) return;

        for (File file : files) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            if (yaml.contains(uuid.toString())) {
                yaml.set(uuid.toString(), null);
                try {
                    yaml.save(file);
                    TownyCivs.logger.info("Removed structure " + uuid + " from " + file.getName());
                } catch (IOException e) {
                    TownyCivs.logger.log(Level.SEVERE, "Could not update file " + file.getName(), e);
                }
                return;
            }
        }
    }

    /**
     * Loads all structures from all town files.
     */
    @Override
    public Collection<LoadedStructure> allStructures() {
        Set<LoadedStructure> set = new HashSet<>();
        File[] files = storage.toFile().listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) return set;

        for (File file : files) {
            YamlConfiguration yaml = new YamlConfiguration();
            try {
                yaml.load(file);
                // Town UUID from filename
                UUID townUUID = UUID.fromString(file.getName().replace(".yml", ""));

                for (String structKey : yaml.getKeys(false)) {

                    if (structKey.equals("power_grid")) {
                        continue;
                    }


                    String path = structKey;

                    var struct = new LoadedStructure(
                            UUID.fromString(structKey),
                            townUUID,
                            yaml.getString(path + ".structureId"),
                            yaml.getLocation(path + ".center"),
                            configurationService.findStructureById(yaml.getString(path + ".structureId")).orElse(null)
                    );

                    struct.editMode.set(yaml.getBoolean(path + ".editMode"));
                    struct.toggleTitle.set(yaml.getBoolean(path + ".toggleTitle"));
                    struct.lastTickTime = yaml.getLong(path + ".lastTickTime");
                    struct.savedBatteryCharge = yaml.getDouble(path + ".batteryCharge", 0.0);

                    List<Map<String, ?>> invList = (List<Map<String, ?>>) yaml.getList(path + ".inventory");
                    if (invList != null) {
                        for (Map<String, ?> invMap : invList) {
                            Location loc = (Location) invMap.get("location");
                            List<ItemStack> items = (List<ItemStack>) invMap.get("content");
                            if (loc != null && items != null) {
                                structureInventoryService.loadStructureInventory(
                                        struct, loc, items.toArray(ItemStack[]::new)
                                );
                            }
                        }
                    }

                    set.add(struct);
                }

            } catch (IOException | InvalidConfigurationException e) {
                TownyCivs.logger.log(Level.SEVERE, "Failed to load " + file.getName(), e);
            }
        }

        return set;
    }

    @Override
    public void savePowerNetwork(UUID townId, List<Map<String, String>> lines) {
        java.nio.file.Path file = storage.resolve(townId + ".yml");
        synchronized (fileLock) {
            try {
                org.bukkit.configuration.file.YamlConfiguration yaml = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file.toFile());
                yaml.set("power_grid", lines);
                yaml.save(file.toFile());
            } catch (java.io.IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public List<Map<String, String>> loadPowerNetwork(UUID townId) {
        java.nio.file.Path file = storage.resolve(townId + ".yml");
        if (!file.toFile().exists()) return new java.util.ArrayList<>();

        org.bukkit.configuration.file.YamlConfiguration yaml = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file.toFile());
        List<?> rawList = yaml.getMapList("power_grid");
        List<Map<String, String>> typedList = new java.util.ArrayList<>();

        for (Object obj : rawList) {
            if (obj instanceof Map) {
                Map<String, String> entry = new java.util.HashMap<>();
                ((Map<?, ?>) obj).forEach((k, v) -> entry.put(String.valueOf(k), String.valueOf(v)));
                typedList.add(entry);
            }
        }
        return typedList;
    }
}
