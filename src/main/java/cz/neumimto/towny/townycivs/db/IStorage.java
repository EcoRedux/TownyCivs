package cz.neumimto.towny.townycivs.db;

import cz.neumimto.towny.townycivs.model.LoadedStructure;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public sealed interface IStorage permits Flatfile, Database {
    void init();

    void save(LoadedStructure structure);

    void remove(UUID uuid);

    Collection<LoadedStructure> allStructures();

    default void savePowerNetwork(UUID townId, List<Map<String, String>> lines) {}
    default List<Map<String, String>> loadPowerNetwork(UUID townId) { return List.of(); }
}
