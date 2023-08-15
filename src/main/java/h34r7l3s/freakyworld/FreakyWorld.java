package h34r7l3s.freakyworld;

import org.bukkit.plugin.java.JavaPlugin;

import io.th0rgal.oraxen.api.OraxenItems;
import io.th0rgal.oraxen.mechanics.MechanicsManager;
import org.bukkit.Bukkit;

import java.util.logging.Logger;

public final class FreakyWorld extends JavaPlugin {

    private GuildGUIListener guildListener;
    Logger logger = this.getLogger();
    @Override
    public void onEnable() {
        logger.info("Before creating factory");
        LightningArrowMechanicFactory factory;
        factory = new LightningArrowMechanicFactory("lightning_arrow_mechanic");
        logger.info("After creating factory");

        MechanicsManager.registerMechanicFactory("lightning_arrow_mechanic", factory, true);

        // Debugging: Print after the mechanic is registered
        logger.info("Registered mechanic");

        LightningArrowMechanicsManager manager = new LightningArrowMechanicsManager(this, factory);
        Bukkit.getPluginManager().registerEvents(manager, this);

        // Debugging: Print after the event listener is registered
        logger.info("Registered event listener");

        OraxenItems.loadItems();

        // Debugging: Print after items are loaded
        logger.info("Loaded items");

        // Register the ArmorEnhancements event listener
        ArmorEnhancements armorListener = new ArmorEnhancements(this);

        getServer().getPluginManager().registerEvents(armorListener, this);
        logger.info("Registered ArmorEnhancements event listener");

        logger.info("Villager");
        MyVillager villagerListener = new MyVillager(this);
        getServer().getPluginManager().registerEvents(villagerListener, this);
        logger.info("Loaded VillagerPlug");

        logger.info("BloomAura");
        BloomAura bloomAuraListener = new BloomAura(this);
        getServer().getPluginManager().registerEvents(bloomAuraListener, this);
        logger.info("Registered BloomAura event listener");
        logger.info("Trading Villager");

        Bukkit.getPluginManager().registerEvents(new CustomVillagerTrader(this), this);
        logger.info("Registered Trading Villager event listener");
        logger.info("Gilden System");
        guildListener = new GuildGUIListener(this);
        getServer().getPluginManager().registerEvents(guildListener, this);
        logger.info("Registered GildenSystem");
    }


    @Override
    public void onDisable() {
        CustomVillagerTrader.removeCustomVillager();
        guildListener.removeGuildVillager();
        // Plugin shutdown logic
    }
}