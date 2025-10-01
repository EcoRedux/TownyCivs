package cz.neumimto.towny.townycivs.model;

import java.util.Objects;
import java.util.UUID;

public final class PlayerBlueprintKey {
    private final UUID playerId;
    private final BlueprintItem blueprintItem;

    public PlayerBlueprintKey(UUID playerId, BlueprintItem blueprintItem) {
        this.playerId = playerId;
        this.blueprintItem = blueprintItem;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public BlueprintItem getBlueprintItem() {
        return blueprintItem;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PlayerBlueprintKey)) return false;
        PlayerBlueprintKey that = (PlayerBlueprintKey) o;
        return Objects.equals(playerId, that.playerId)
                && Objects.equals(blueprintItem, that.blueprintItem);
    }

    @Override
    public int hashCode() {
        return Objects.hash(playerId, blueprintItem);
    }
}


