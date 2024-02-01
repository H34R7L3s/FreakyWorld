package h34r7l3s.freakyworld;

import org.bukkit.*;
import org.bukkit.block.Barrel;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import io.th0rgal.oraxen.api.OraxenItems;
import io.th0rgal.oraxen.mechanics.MechanicsManager;

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

    Logger logger = this.getLogger();
    private GuildSaver guildSaver;

    @Override
    public void onEnable() {
        FileConfiguration secretsConfig = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "secrets.yml"));

        // Check if the secrets.yml file exists
        if (!new File(getDataFolder(), "secrets.yml").exists()) {
            getLogger().severe("Die Datei secrets.yml wurde nicht gefunden: " + getDataFolder().getPath() + "/secrets.yml");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Initialize the Discord Bot
        try {
            this.discordBot = new DiscordBot(secretsConfig);
            this.getServer().getPluginManager().registerEvents(new MyVillager(this, discordBot), this);
            getLogger().info("Discord Bot gestartet.");
        } catch (LoginException | InterruptedException e) {
            getLogger().severe("Konnte den Discord Bot nicht anmelden: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
        }

        //VampSpells
        logger.info("ElytraDoubleJumpEffect");
        ElytraDoubleJumpEffect elytraDoubleJumpEffectListener = new ElytraDoubleJumpEffect(this);
        getServer().getPluginManager().registerEvents(elytraDoubleJumpEffectListener, this);
        logger.info("Registered ElytraDoubleJumpEffect event listener");

        //getServer().getPluginManager().registerEvents(new ArmorEnhancements(this), this);
        this.saveDefaultConfig();
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

        logger.info("Starting Vils Questing");

        villagerCategoryManager = new VillagerCategoryManager(this);
        categoryManager = new CategoryManager();
        eventLogic = new EventLogic(this, categoryManager); // Verwende das Feld der Klasse, nicht eine lokale Variable

        categoryTaskHandler = new CategoryTaskHandler(this, categoryManager, eventLogic); // Setze das Feld der Klasse

        villagerInteractionHandler = new VillagerInteractionHandler(this, categoryManager, eventLogic);

        getServer().getPluginManager().registerEvents(villagerInteractionHandler, this);

        logger.info("Vils Questing Online");
        logger.info("HCFW ");
        hcfw = new HCFW(this);

        // Planen Sie eine regelmäßige Überprüfung
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            hcfw.checkAndStartEventBasedOnProbability();
        }, 0L, 20L * 60 * 60); // z.B. alle Stunde
        //hcfw.initializeEvent();
        logger.info("Loaded HCFW");

        // Register the ArmorEnhancements event listener
        ArmorEnhancements armorListener = new ArmorEnhancements(this);

        getServer().getPluginManager().registerEvents(armorListener, this);
        logger.info("Registered ArmorEnhancements event listener");

        myVillager = new MyVillager(this, discordBot);
        myVillager.createCustomVillager();

        logger.info("BloomAura");
        BloomAura bloomAuraListener = new BloomAura(this);
        getServer().getPluginManager().registerEvents(bloomAuraListener, this);
        logger.info("Registered BloomAura event listener");
        logger.info("Trading Villager");

        Bukkit.getPluginManager().registerEvents(new CustomVillagerTrader(this), this);
        logger.info("Registered Trading Villager event listener");
        logger.info("Gilden System");
        try {
            String jdbcUrl = "jdbc:mysql://localhost:3306/FreakyWorld"; // Ändere die URL entsprechend
            String dbUser = "mysql";
            String dbPassword = "Admin";
            dbConnection = DriverManager.getConnection(jdbcUrl, dbUser, dbPassword);
            getLogger().info("Datenbankverbindung hergestellt.");
        } catch (SQLException e) {
            getLogger().severe("Fehler beim Herstellen der Datenbankverbindung: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        guildListener = new GuildGUIListener(this, dbConnection);
        // Erstellen einer Guild-Instanz und Spawnen des Villagers
        spawnGuildMasterVillager();


        this.guildSaver = new GuildSaver(this, dbConnection);
        guildSaver.createGuildTasksTable();
        guildSaver.createAlliancesTable();

        getServer().getPluginManager().registerEvents(guildListener, this);


        logger.info("Registered GildenSystem");


        logger.info("QuestVil");
        questVillager = new QuestVillager(this, discordBot); // Pass both 'this' (FreakyWorld instance) and 'discordBot'
        questVillager.spawnVillager();
        // Übergeben Sie die Instanz von DiscordBot
        // Initialisieren des QuestVillager-Listeners
        getServer().getPluginManager().registerEvents(questVillager, this);  // Registrieren des QuestVillager-Listeners

        //this.getServer().getPluginManager().registerEvents(new MyVillager(this), this);

        logger.info("Registered QuestVil");
        // VampirZepter Initialisierung und Registrierung
        VampirZepter vampirZepterListener = new VampirZepter();
        getServer().getPluginManager().registerEvents(vampirZepterListener, this);
        logger.info("Registered VampirZepter event listener");

        // Starten Sie den visuellen Effekt für das VampirZepter
        vampirZepterListener.startVampirZepterEffectLoop(this);

        getServer().getPluginManager().registerEvents(new LegendaryAxe(), this);
        try {
            String jdbcUrl = "jdbc:mysql://localhost:3306/FreakyWorld"; // Ändern Sie die URL entsprechend
            String dbUser = "mysql";
            String dbPassword = "Admin";
            dbConnection = DriverManager.getConnection(jdbcUrl, dbUser, dbPassword);
            this.weaponAttributeManager = new WeaponAttributeManager(this, dbConnection);
            getLogger().info("WeaponAttributeManager initialisiert.");
        } catch (SQLException e) {
            getLogger().severe("Fehler beim Herstellen der Datenbankverbindung: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Initialisieren und Registrieren des WeaponAttributeHandlers
        this.weaponAttributeHandler = new WeaponAttributeHandler(this, weaponAttributeManager);
        getServer().getPluginManager().registerEvents(weaponAttributeHandler, this);
        getServer().getPluginManager().registerEvents(new WeaponAttributeHandler(this, weaponAttributeManager), this);

        // Initialisieren und Registrieren des DamageApplicationListeners
        DamageApplicationListener damageApplicationListener = new DamageApplicationListener(this, weaponAttributeManager);

        getServer().getPluginManager().registerEvents(damageApplicationListener, this);






        //ab hier Testing

        //nitradoAPIKey = secretsConfig.getString("nitradoAPIKey");
        //serverID = secretsConfig.getString("serverID");




        restartInfos = new ArrayList<>();
        restartInfos.add(new RestartInfo(LocalTime.of(23, 59), "Oha - noch wach? Freaky! Na los, ab mit dir!!"));
        restartInfos.add(new RestartInfo(LocalTime.of(6, 0), "Hört ihr Sie schon zwitschern... Na los, ab mit dir!!"));
        restartInfos.add(new RestartInfo(LocalTime.of(12, 0), "HappaHappa :3 Na los, ab mit dir!!"));
        restartInfos.add(new RestartInfo(LocalTime.of(16, 0), "Lets get Freaky!! Na los, ab mit dir!!"));
        restartInfos.add(new RestartInfo(LocalTime.of(20, 0), "PowerNap >.< Na los, ab mit dir!!"));
        scheduleDailyRestarts();

        logger.info("FreakyWorld Loading Complete");
        buttonActions = new HashMap<>();
        logger.info("Keine Fehler gefunden - Features gestartet");

        //
    }
    public void spawnGuildMasterVillager() {
        World world = Bukkit.getWorld("world"); // Stellen Sie sicher, dass diese Welt existiert
        Location villagerLocation = new Location(world, -42, 369, 14); // Ändern Sie die Koordinaten entsprechend

        Villager guildMasterVillager = world.spawn(villagerLocation, Villager.class);
        guildMasterVillager.setCustomName("Gildenmeister");
        guildMasterVillager.setInvulnerable(true);
        guildMasterVillager.setAI(false);
        // Hier können Sie dem Villager andere Eigenschaften hinzufügen, wenn gewünscht
    }
    public DiscordBot getDiscordBot() {
        return discordBot;
    }
    @Override
    public void onDisable() {
        try {
            CustomVillagerTrader.removeVillagers();
        } catch (Exception e) {
            e.printStackTrace();  // Or some other form of error logging
        }
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
        // Check if questVillager is not null before trying to remove the Quest Vi
        // Rufe die Methode removeVillagerAtPosition auf, um den Villager zu entfernen
        Location villagerLocation = new Location(Bukkit.getWorld("world"), -42, 69, 4);
        myVillager.removeVillagerAtPosition(villagerLocation);
        villagerCategoryManager.removeVillager();

        //
        // llager
        if (this.questVillager != null) {
            try {
                 this.questVillager.removeQuestVillager();
            } catch (Exception e) {
                e.printStackTrace();
            }
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
    // Teil des Initialisierungscodes




    private void warnPlayersBeforeRestart(int minutesLeft) {
        String message = ChatColor.RED + "Der Server wird in " + minutesLeft + " Minuten neugestartet!";
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(message);
        }
    }
    private void sendNitradoRestartRequest() {
        try {
            // Ersetzen Sie :id durch die tatsächliche ID Ihres Dienstes
            URL url = new URL("https://api.nitrado.net/services/" + serverID + "/gameservers/restart");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", "Bearer " + nitradoAPIKey);

            // Optional: Parameter hinzufügen
            conn.setDoOutput(true);
            String urlParameters = "message=Restart initiated&restart_message=Server is restarting!";
            byte[] postData = urlParameters.getBytes(StandardCharsets.UTF_8);
            try (DataOutputStream wr = new DataOutputStream(conn.getOutputStream())) {
                wr.write(postData);
            }

            int responseCode = conn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                logger.info("Server wird neugestartet.");
            } else if (responseCode == 401) {
                logger.warning("Der bereitgestellte Zugriffstoken ist nicht (mehr) gültig.");
            } else if (responseCode == 429) {
                logger.warning("Das Ratenlimit wurde überschritten.");
            } else if (responseCode == 503) {
                logger.warning("Wartung. API ist derzeit nicht verfügbar.");
            } else {
                logger.warning("Fehler beim Senden des Neustartbefehls über Nitrado API.");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
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

}