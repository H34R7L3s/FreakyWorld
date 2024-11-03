package h34r7l3s.freakyworld;

import org.bukkit.*;
import org.bukkit.block.Barrel;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import io.th0rgal.oraxen.api.OraxenItems;
import io.th0rgal.oraxen.mechanics.MechanicsManager;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.scheduler.BukkitRunnable;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;


import javax.security.auth.login.LoginException;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalTime;
import java.util.*;
import java.util.logging.Logger;

public final class FreakyWorld extends JavaPlugin {

    private List<RestartInfo> restartInfos;
    private DiscordBot discordBot;
    private Connection dbConnection;
    private FreakyBuilder freakyBuilder;
    private GuildManager guildManager;
    private CustomDatabaseManager customDatabaseManager;

    private GuildGUIListener guildGUIListener;

    private CustomVillagerTrader customVillagerTrader;
    private GameLoop gameLoop;
    private VillagerCategoryManager villagerCategoryManager;
    private CategoryTaskHandler categoryTaskHandler;
    private CategoryManager categoryManager;
    private EventLogic eventLogic;
    private VillagerInteractionHandler villagerInteractionHandler;

    private HCFW hcfw;
    private WeaponAttributeManager weaponAttributeManager;

    private WeaponAttributeHandler weaponAttributeHandler;

    private boolean isRestartScheduled = false;  // Hilft dabei, Mehrfachwarnungen zu verhindern
    private String nitradoAPIKey;  // Deklaration hier
    private String serverID;
    private String discordToken;
    private QuestVillager questVillager;

    private MyVillager myVillager;
    private JavaPlugin plugin;
    private GuildGUIListener guildListener;
    private CustomBookManager customBookManager;
    private DatabaseManager databaseManager;
    Logger logger = this.getLogger();
    private GuildSaver guildSaver;
    private FreakyBankRobber freakyBankRobber;
    private DragonEventManager dragonEventManager;
    private VampireVillager vampireVillager;
    // Methode, um Spieler zu blockieren, bis der Server vollständig geladen ist
    private void blockPlayerLoginUntilReady() {
        Bukkit.getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onPlayerLogin(PlayerLoginEvent event) {
                Player player = event.getPlayer();
                String bypassPermission = "freakyworld.earlyjoin"; // Define the permission needed to bypass the lock

                if (!allPluginsLoaded && !player.hasPermission(bypassPermission)) {
                    event.disallow(PlayerLoginEvent.Result.KICK_OTHER, ChatColor.RED + "Server ist noch nicht bereit. Bitte versuche es gleich erneut.");
                }
            }
        }, this);
    }

    // Methode, die prüft, ob alle Plugins geladen sind und dann den Befehl ausführt
    // Methode, die prüft, ob alle Plugins geladen sind und dann den Befehl ausführt
    private void waitForPluginsAndExecuteCommand() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (areAllPluginsLoaded()) {
                    Bukkit.getScheduler().runTask(FreakyWorld.this, () -> {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "oraxen reload items");
                    });
                    logger.info("Oraxen Reload ausgeführt");
                    allPluginsLoaded = false;
                    this.cancel(); // Beendet die wiederholte Prüfung
                }
            }
        }.runTaskTimer(this, 20L, 20L); // Alle 20 Ticks (1 Sekunde) prüfen
    }

    // Hilfsmethode zur Überprüfung, ob alle Plugins geladen sind
    private boolean areAllPluginsLoaded() {
        for (Plugin plugin : Bukkit.getPluginManager().getPlugins()) {
            if (!plugin.isEnabled()) {

                return false;
            }
        }
        return true;
    }

    // Variable zur Überprüfung, ob alle Plugins geladen sind
    private boolean allPluginsLoaded = false;
    @Override
    public void onEnable() {

        // Spieler blockieren, bis alle Plugins geladen und der Befehl ausgeführt wurde
        blockPlayerLoginUntilReady();

        FileConfiguration secretsConfig = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "secrets.yml"));
        databaseManager = new DatabaseManager(getDataFolder());

        if (!new File(getDataFolder(), "secrets.yml").exists()) {
            getLogger().severe("Die Datei secrets.yml wurde nicht gefunden: " + getDataFolder().getPath() + "/secrets.yml");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        try {
            this.discordBot = new DiscordBot(secretsConfig);
            this.getServer().getPluginManager().registerEvents(new MyVillager(this, discordBot), this);
            getLogger().info("Discord Bot gestartet.");
        } catch (LoginException | InterruptedException e) {
            getLogger().severe("Konnte den Discord Bot nicht anmelden: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
        }

        logger.info("ElytraDoubleJumpEffect");
        ElytraDoubleJumpEffect elytraDoubleJumpEffectListener = new ElytraDoubleJumpEffect(this);
        getServer().getPluginManager().registerEvents(elytraDoubleJumpEffectListener, this);
        logger.info("Registered ElytraDoubleJumpEffect event listener");

        this.saveDefaultConfig();
        logger.info("Before creating factory");
        LightningArrowMechanicFactory factory = new LightningArrowMechanicFactory("lightning_arrow_mechanic");
        logger.info("After creating factory");

        MechanicsManager.registerMechanicFactory("lightning_arrow_mechanic", factory, true);
        logger.info("Registered mechanic");

        LightningArrowMechanicsManager manager = new LightningArrowMechanicsManager(this, factory);
        Bukkit.getPluginManager().registerEvents(manager, this);
        logger.info("Registered event listener");

        OraxenItems.loadItems();
        logger.info("Loaded items");

        logger.info("Gilden System");

        try {
            DataSource dataSource = DataSourceProvider.getDataSource();
            if (dataSource == null) {
                throw new SQLException("Failed to establish database connection.");
            }
            getLogger().info("Datenbankverbindung hergestellt.");

            guildManager = new GuildManager(dataSource, this);
            gameLoop = new GameLoop(this, discordBot, guildManager, dataSource.getConnection());
            //gameLoop.updateDatabaseStructure();
            getServer().getPluginManager().registerEvents(gameLoop, this);

            categoryManager = new CategoryManager(); // Muss vor VillagerCategoryManager initialisiert werden
            customDatabaseManager = new CustomDatabaseManager(this); // Instanz des CustomDatabaseManager
            eventLogic = new EventLogic(this, categoryManager, customDatabaseManager, guildManager); // Verwendung des CustomDatabaseManager
            //guildManager = new GuildManager(dataSource, this); // Instanz des GuildManager
            villagerCategoryManager = new VillagerCategoryManager(this); // Jetzt nach categoryManager
            categoryTaskHandler = new CategoryTaskHandler(this, categoryManager, eventLogic, customDatabaseManager, guildManager); // Übergabe des CustomDatabaseManager
            villagerInteractionHandler = new VillagerInteractionHandler(this, categoryManager, eventLogic, guildManager);

            //getServer().getPluginManager().registerEvents(villagerInteractionHandler, this);
            freakyBuilder = new FreakyBuilder(this, gameLoop);


            hcfw = new HCFW(this);
            getCommand("portalwatcher").setExecutor(hcfw);


            hcfw.spawnHCFWVillager();


            Bukkit.getScheduler().runTaskTimer(this, () -> {
                hcfw.checkAndStartEventBasedOnProbability();
            }, 0L, 20L * 60 * 60);

            ArmorEnhancements armorListener = new ArmorEnhancements(this);
            getServer().getPluginManager().registerEvents(armorListener, this);

            myVillager = new MyVillager(this, discordBot);
            myVillager.createCustomVillager();

            BloomAura bloomAuraListener = new BloomAura(this);
            getServer().getPluginManager().registerEvents(bloomAuraListener, this);

            //customVillagerTrader = new CustomVillagerTrader(this); //doppelt vorhanden! daher auskommentiert. Testing
            Bukkit.getPluginManager().registerEvents(new CustomVillagerTrader(this), this);

            guildListener = new GuildGUIListener(this, dataSource);
            this.getCommand("gilde").setExecutor(guildListener);


            spawnGuildMasterVillager();

            guildSaver = new GuildSaver(this, dataSource);
            guildSaver.createGuildTasksTable();
            guildSaver.createAlliancesTable();

            getServer().getPluginManager().registerEvents(guildListener, this);

            gameLoop.initialize();



            getLogger().info("DragonEventManager.");
            // Initialisiere DragonEventManager
            dragonEventManager = new DragonEventManager(gameLoop, guildManager, this);
            gameLoop.setDragonEventManager(dragonEventManager);
            this.getCommand("dragon").setExecutor(dragonEventManager);

            // In der Hauptklasse
            Bukkit.getPluginManager().registerEvents(dragonEventManager, this);
            //VAmpir
            // Andere Initialisierungen
            vampireVillager = new VampireVillager(gameLoop);
            getServer().getPluginManager().registerEvents(vampireVillager, this);

            getLogger().info("DragonEventManager erfolgreich registriert.");

            questVillager = new QuestVillager(this, discordBot, gameLoop);
            questVillager.spawnVillager();
            getServer().getPluginManager().registerEvents(questVillager, this);

            VampirZepter vampirZepter = new VampirZepter(this, databaseManager, gameLoop, questVillager, guildManager);
            getServer().getPluginManager().registerEvents(vampirZepter, this);

            getServer().getPluginManager().registerEvents(new LegendaryAxe(), this);

            weaponAttributeManager = new WeaponAttributeManager(this, dataSource.getConnection());
            weaponAttributeHandler = new WeaponAttributeHandler(this, weaponAttributeManager);
            getServer().getPluginManager().registerEvents(weaponAttributeHandler, this);

            DamageApplicationListener damageApplicationListener = new DamageApplicationListener(this, weaponAttributeManager);
            getServer().getPluginManager().registerEvents(damageApplicationListener, this);

            restartInfos = new ArrayList<>();
            restartInfos.add(new RestartInfo(LocalTime.of(23, 59), "Oha - noch wach? Freaky! Na los, ab mit dir!!"));
            restartInfos.add(new RestartInfo(LocalTime.of(6, 0), "Hört ihr Sie schon zwitschern... Na los, ab mit dir!!"));
            restartInfos.add(new RestartInfo(LocalTime.of(12, 0), "HappaHappa :3 Na los, ab mit dir!!"));
            restartInfos.add(new RestartInfo(LocalTime.of(16, 0), "Lets get Freaky!! Na los, ab mit dir!!"));
            restartInfos.add(new RestartInfo(LocalTime.of(20, 0), "PowerNap >.< Na los, ab mit dir!!"));
            scheduleDailyRestarts();
            // Überprüfen, ob alle Plugins geladen sind und Befehl ausführen
            waitForPluginsAndExecuteCommand();
            logger.info("FreakyWorld Loading Complete");
            buttonActions = new HashMap<>();
            logger.info("Keine Fehler gefunden - Features gestartet");
        } catch (SQLException e) {
            getLogger().severe("Fehler beim Herstellen der Datenbankverbindung: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        logger.info("FreakyWorld BankRobber");
        //freakyBankRobber = new FreakyBankRobber();


        new BukkitRunnable() {
            @Override
            public void run() {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "oraxen reload all");
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "chunky continue");
                allPluginsLoaded = true;  // Spieler-Login freigeben
                getLogger().info("Oraxen-Reload abgeschlossen und Login freigegeben.");
                if (discordBot != null) {
                    //vor season start aktivieren!

                    discordBot.sendMessageToDiscordStartInfo(" -------- Tretet ein, Freaks! ------- ");
                    //discordBot.sendMessageToDiscordStartInfo(" -------- Tretet ein, <@&1046913269966852207>! ------- ");
                }
            }
        }.runTaskLater(this, 1400L); //
    }


    @Override
    public void onDisable() {
        //freakyBankRobber.removeBankRobberVillager();
        gameLoop.removeRankingVillager();
        vampireVillager.removeVillager();
        freakyBuilder.removeBuilderVillager();
        hcfw.removeHCFWVillager();


        if (discordBot != null) {
            discordBot.shutdown();
            getLogger().info("Discord Bot heruntergefahren.");
        }

        // Check if guildListener is not null before trying to remove the Guild Villager
        if (this.guildListener != null) {
            try {
                this.guildListener.removeGuildVillager();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        // Check if questVillager is not null before trying to remove the Quest Villager
        if (this.questVillager != null) {
            try {
                this.questVillager.removeQuestVillager();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        customVillagerTrader.removeVillagers();
        // Rufe die Methode removeVillagerAtPosition auf, um den Villager zu entfernen
        if (myVillager != null) {
            Location villagerLocation = new Location(Bukkit.getWorld("world"), -59, 14, -54);
            myVillager.removeVillagerAtPosition(villagerLocation);
        }


        villagerCategoryManager.removeVillager();


        //Gildenmeister entfernen
        removeGuildMasterVillagers();
        //freakyBankRobber.removeBankRobberVillager();
        // Ranglistenhändler entfernen
        if (gameLoop != null) {
            //gameLoop.removeRankingVillager();
            gameLoop.close();
            gameLoop.closeConnection();
        }

        if (hcfw != null) {
            hcfw.cleanupEvents();
        }

        if (dbConnection != null) {
            try {
                dbConnection.close();
                getLogger().info("Datenbankverbindung geschlossen.");
            } catch (SQLException e) {
                getLogger().severe("Fehler beim Schließen der Datenbankverbindung: " + e.getMessage());
            }
        }
        // Plugin shutdown logic
    }

    private void scheduleDailyRestarts() {
        // Planen Sie die Neustarts für heute
        scheduleRestartsForToday();

        // Planen Sie diese Methode, um alle 24 Stunden erneut ausgeführt zu werden
        Bukkit.getScheduler().runTaskTimer(this, this::scheduleRestartsForToday, 20 * 60 * 60 * 24, 20 * 60 * 60 * 24);
    }

    private void scheduleRestartsForToday() {
        LocalTime now = LocalTime.now();

        for (RestartInfo info : restartInfos) {
            long delay = Duration.between(now, info.getRestartTime()).toSeconds();

            // Überprüfe, ob die geplante Zeit heute bereits vorbei ist
            if (delay < 0) {
                continue; // Überspringe diesen Neustart, da er bereits vorbei ist
            }

            long delayInTicks = delay * 20; // Umrechnung von Sekunden in Minecraft-Ticks

            // Warnung 5 Minuten vor Neustart
            if (delay > 300) {
                Bukkit.getScheduler().runTaskLater(this, () -> warnPlayersBeforeRestart(5), delayInTicks - 6000);
            }

            // Warnung 3 Minuten vor Neustart
            if (delay > 180) {
                Bukkit.getScheduler().runTaskLater(this, () -> warnPlayersBeforeRestart(3), delayInTicks - 3600);
            }

            // Warnung 1 Minute vor Neustart
            if (delay > 60) {
                Bukkit.getScheduler().runTaskLater(this, () -> warnPlayersBeforeRestart(1), delayInTicks - 1200);
            }

            // Tatsächlicher Neustart und Benachrichtigung
            Bukkit.getScheduler().runTaskLater(this, () -> {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    player.sendMessage(ChatColor.RED + info.getMessage());
                }
            }, delayInTicks);
        }
    }

    private void warnPlayersBeforeRestart(int minutesLeft) {
        String message = ChatColor.RED + "Der Server wird in " + minutesLeft + " Minuten neugestartet!";
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(message);
        }
    }




    private List<Villager> guildMasters = new ArrayList<>();
    public void spawnGuildMasterVillager() {
        World world = Bukkit.getWorld("world"); // Stellen Sie sicher, dass diese Welt existiert
        Location villagerLocation = new Location(world, -21, 14, -54); // Ändern Sie die Koordinaten entsprechend

        Villager guildMasterVillager = world.spawn(villagerLocation, Villager.class);
        guildMasterVillager.setCustomName("Gildenmeister");
        guildMasterVillager.setInvulnerable(true);
        guildMasterVillager.setAI(false);

        // Hier können Sie dem Villager andere Eigenschaften hinzufügen, wenn gewünscht
        guildMasters.add(guildMasterVillager);
        initializeGuildMasterLookTask();
    }
    private void initializeGuildMasterLookTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Villager guildMaster : guildMasters) {
                    if (guildMaster != null && !guildMaster.isDead()) {
                        lookAtNearestPlayerGuildMaster(guildMaster);
                    }
                }
            }
        }.runTaskTimer(this, 0L, 40L); // alle 2 Sekunden (40 Ticks)
    }
    private void lookAtNearestPlayerGuildMaster(Villager guildMaster) {
        Collection<Player> nearbyPlayers = guildMaster.getWorld().getNearbyPlayers(guildMaster.getLocation(), 10);
        Player nearestPlayer = null;
        double nearestDistance = Double.MAX_VALUE;

        for (Player player : nearbyPlayers) {
            double distance = player.getLocation().distanceSquared(guildMaster.getLocation());
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearestPlayer = player;
            }
        }

        if (nearestPlayer != null) {
            Location guildMasterLocation = guildMaster.getLocation();
            Location playerLocation = nearestPlayer.getLocation();

            // Berechne die Richtung zum Spieler und setze die Rotation des Gildenmeisters
            double dx = playerLocation.getX() - guildMasterLocation.getX();
            double dz = playerLocation.getZ() - guildMasterLocation.getZ();
            float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            guildMasterLocation.setYaw(yaw);
            guildMaster.teleport(guildMasterLocation); // Aktualisiert die Blickrichtung des Gildenmeisters
        }
    }
    public void removeGuildMasterVillagers() {
        for (Villager guildMaster : guildMasters) {
            if (guildMaster != null && !guildMaster.isDead()) {
                guildMaster.remove();
            }
        }
        guildMasters.clear();
    }

    public DiscordBot getDiscordBot() {
        return discordBot;
    }

    private Map<UUID, Map<Integer, Runnable>> buttonActions;

    public Map<UUID, Map<Integer, Runnable>> getButtonActions() {
        return this.buttonActions;
    }

    public VillagerCategoryManager getVillagerCategoryManager() {
        return this.villagerCategoryManager;
    }
    public CategoryTaskHandler getCategoryTaskHandler() {
        return categoryTaskHandler;
    }

    public CategoryManager getCategoryManager() {
        return this.categoryManager;
    }
    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }
    public EventLogic getEventLogic() {
        return this.eventLogic;
    }

    class RestartInfo {
        LocalTime restartTime;
        String message;

        public RestartInfo(LocalTime restartTime, String message) {
            this.restartTime = restartTime;
            this.message = message;
        }

        public LocalTime getRestartTime() {
            return restartTime;
        }

        public String getMessage() {
            return message;
        }
    }

    public GameLoop getGameLoop() {
        return gameLoop;
    }




    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("serverlock")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
                return true;
            }

            Player player = (Player) sender;
            String togglePermission = "freakyworld.earlyjoin"; // Define the permission required for toggling the lock

            // Check if the player has the correct permission
            if (!player.hasPermission(togglePermission)) {
                player.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
                return true;
            }

            if (args.length != 1) {
                player.sendMessage(ChatColor.YELLOW + "Usage: /serverlock <open|close>");
                return true;
            }

            // Handle the "open" and "close" arguments
            if (args[0].equalsIgnoreCase("open")) {
                allPluginsLoaded = true; // Allow player login
                player.sendMessage(ChatColor.GREEN + "Server is now open to players.");
                getLogger().info("Server lock lifted by " + player.getName());
            } else if (args[0].equalsIgnoreCase("close")) {
                allPluginsLoaded = false; // Disallow player login
                player.sendMessage(ChatColor.RED + "Server is now closed to players.");
                getLogger().info("Server lock enabled by " + player.getName());
            } else {
                player.sendMessage(ChatColor.YELLOW + "Usage: /serverlock <open|close>");
            }
            return true;
        }
        return false;
    }




}
