package h34r7l3s.freakyworld;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.Barrel;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import io.th0rgal.oraxen.api.OraxenItems;
import io.th0rgal.oraxen.mechanics.MechanicsManager;
import org.bukkit.Bukkit;

import javax.security.auth.login.LoginException;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public final class FreakyWorld extends JavaPlugin {

    private List<RestartInfo> restartInfos;
    private DiscordBot discordBot;
    private HCFW hcfw;
    private boolean isRestartScheduled = false;  // Hilft dabei, Mehrfachwarnungen zu verhindern
    private String nitradoAPIKey;  // Deklaration hier
    private String serverID;
    private String discordToken;
    private QuestVillager questVillager;
    private JavaPlugin plugin;
    private GuildGUIListener guildListener;
    private CustomBookManager customBookManager;

    Logger logger = this.getLogger();
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
            discordBot = new DiscordBot(secretsConfig);
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
        guildListener.spawnGuildMasterVillager();  // Fügen Sie diese Zeile hinzu, um den Villager zu spawnen
        logger.info("Registered GildenSystem");


        logger.info("QuestVil");
        questVillager = new QuestVillager(this, discordBot); // Pass both 'this' (FreakyWorld instance) and 'discordBot'
        questVillager.spawnVillager();
        // Übergeben Sie die Instanz von DiscordBot
        // Initialisieren des QuestVillager-Listeners
        getServer().getPluginManager().registerEvents(questVillager, this);  // Registrieren des QuestVillager-Listeners
        logger.info("Registered QuestVil");
        // VampirZepter Initialisierung und Registrierung
        VampirZepter vampirZepterListener = new VampirZepter();
        getServer().getPluginManager().registerEvents(vampirZepterListener, this);
        logger.info("Registered VampirZepter event listener");

        // Starten Sie den visuellen Effekt für das VampirZepter
        vampirZepterListener.startVampirZepterEffectLoop(this);

        getServer().getPluginManager().registerEvents(new LegendaryAxe(), this);


        //ab hier Testing

        //nitradoAPIKey = secretsConfig.getString("nitradoAPIKey");
        //serverID = secretsConfig.getString("serverID");




        restartInfos = new ArrayList<>();
        restartInfos.add(new RestartInfo(LocalTime.of(0, 0), "Oha - noch wach? Freaky! Na los, ab mit dir!!"));
        restartInfos.add(new RestartInfo(LocalTime.of(6, 0), "Hört ihr Sie schon zwitschern... Na los, ab mit dir!!"));
        restartInfos.add(new RestartInfo(LocalTime.of(12, 0), "HappaHappa :3 Na los, ab mit dir!!"));
        restartInfos.add(new RestartInfo(LocalTime.of(16, 0), "Lets get Freaky!! Na los, ab mit dir!!"));
        restartInfos.add(new RestartInfo(LocalTime.of(20, 0), "PowerNap >.< Na los, ab mit dir!!"));
        scheduleDailyRestarts();

        logger.info("FreakyWorld Loading Complete");
        logger.info("Keine Fehler gefunden - Features gestartet");

        //
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
        // Check if questVillager is not null before trying to remove the Quest Villager
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



}