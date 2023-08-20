package h34r7l3s.freakyworld;

import org.bukkit.plugin.java.JavaPlugin;

import io.th0rgal.oraxen.api.OraxenItems;
import io.th0rgal.oraxen.mechanics.MechanicsManager;
import org.bukkit.Bukkit;

import java.util.logging.Logger;

public final class FreakyWorld extends JavaPlugin {
    private QuestVillager questVillager;
    private JavaPlugin plugin;
    private GuildGUIListener guildListener;
    Logger logger = this.getLogger();
    @Override
    public void onEnable() {
        //getServer().getPluginManager().registerEvents(new ArmorEnhancements(this), this);

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

        logger.info("QuestVil");
        questVillager = new QuestVillager(this);  // Initialisieren des QuestVillager-Listeners
        getServer().getPluginManager().registerEvents(questVillager, this);  // Registrieren des QuestVillager-Listeners
        logger.info("Registered QuestVil");
        // VampirZepter Initialisierung und Registrierung
        VampirZepter vampirZepterListener = new VampirZepter();
        getServer().getPluginManager().registerEvents(vampirZepterListener, this);
        logger.info("Registered VampirZepter event listener");

        // Starten Sie den visuellen Effekt für das VampirZepter
        vampirZepterListener.startVampirZepterEffectLoop(this);
    }


    @Override
    public void onDisable() {
        CustomVillagerTrader.removeCustomVillager();
        guildListener.removeGuildVillager();
        questVillager.removeQuestVillager();
        // Plugin shutdown logic
    }
}