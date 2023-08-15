package h34r7l3s.freakyworld;

import io.th0rgal.oraxen.mechanics.Mechanic;
import io.th0rgal.oraxen.mechanics.MechanicFactory;
import org.bukkit.configuration.ConfigurationSection;

public class LightningArrowMechanicFactory extends MechanicFactory {

    public LightningArrowMechanicFactory(String mechanicId) { super(mechanicId); }

    @Override
    public Mechanic parse(ConfigurationSection itemMechanicConfiguration) {
        Mechanic mechanic = new LightningArrowMechanic(this, itemMechanicConfiguration);
        addToImplemented(mechanic);
        return mechanic;
    }
}
