package cz.neumimto.towny.townycivs.mechanics.common;

import com.electronwill.nightconfig.core.conversion.Path;
import com.typesafe.config.Optional;

import java.util.ArrayList;
import java.util.List;

public class StringList implements Wrapper {

    @Path("List")
    public List<String> configItems = new ArrayList<>();

    @Path("Whitelist")
    @Optional
    public boolean whitelist = false;

    @Override
    public boolean isObject() {
        return true;
    }
}
