package cz.neumimto.towny.townycivs.mechanics.common;

import com.electronwill.nightconfig.core.conversion.Path;
import com.typesafe.config.Optional;

public class PowerStorageConfig implements Wrapper{
    @Path("Capacity")
    public double capacity;

    @Path("ChargeRate")
    @Optional
    public double chargeRate = -1.0; // Use -1.0 to indicate "not set"

    @Path("DischargeRate")
    @Optional
    public double dischargeRate = -1.0; // Use -1.0 to indicate "not set"

    @Override
    public boolean isObject() {
        return true;
    }
}
