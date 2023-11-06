package h34r7l3s.freakyworld;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import io.th0rgal.oraxen.api.OraxenItems;
import io.th0rgal.oraxen.mechanics.MechanicsManager;
import org.bukkit.Bukkit;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
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

    private boolean isRestartScheduled = false;  // Hilft dabei, Mehrfachwarnungen zu verhindern
    private String nitradoAPIKey;  // Deklaration hier
    private String serverID;

    private QuestVillager questVillager;
    private JavaPlugin plugin;
    private GuildGUIListener guildListener;
    Logger logger = this.getLogger();
    @Override
    public void onEnable() {
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
        questVillager = new QuestVillager(this);  // Initialisieren des QuestVillager-Listeners
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
        FileConfiguration secretsConfig = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "secrets.yml"));
        nitradoAPIKey = secretsConfig.getString("nitradoAPIKey");
        serverID = secretsConfig.getString("serverID");




        restartInfos = new ArrayList<>();
        restartInfos.add(new RestartInfo(LocalTime.of(22, 0), "Oha - noch wach? Freaky!"));
        restartInfos.add(new RestartInfo(LocalTime.of(4, 0), "Hört ihr Sie schon zwitschern..."));
        restartInfos.add(new RestartInfo(LocalTime.of(10, 0), "HappaHappa"));
        restartInfos.add(new RestartInfo(LocalTime.of(14, 0), "Lets get Freaky!!"));
        restartInfos.add(new RestartInfo(LocalTime.of(18, 0), "PowerNap"));
        scheduleDailyRestarts();
        //
    }


    @Override
    public void onDisable() {
        try {
            CustomVillagerTrader.removeVillagers();
        } catch (Exception e) {
            e.printStackTrace();  // Oder irgendeine andere Form der Fehlerprotokollierung
        }

        try {
            guildListener.removeGuildVillager();
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            questVillager.removeQuestVillager();
        } catch (Exception e) {
            e.printStackTrace();
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
        for (RestartInfo info : restartInfos) {
            LocalTime now = LocalTime.now();
            long delay = Duration.between(now, info.getRestartTime()).toMinutes();

            if (delay <= 0) {
                delay += 24 * 60;  // Add 24 hours if the delay is negative or zero
            }

            // Warnung 5 Minuten vor Neustart
            if (delay > 5) {
                Bukkit.getScheduler().runTaskLater(this, () -> warnPlayersBeforeRestart(5), 20 * 60 * (delay - 5));
            }

            // Warnung 3 Minuten vor Neustart
            if (delay > 3) {
                Bukkit.getScheduler().runTaskLater(this, () -> warnPlayersBeforeRestart(3), 20 * 60 * (delay - 3));
            }

            // Warnung 1 Minute vor Neustart
            if (delay > 1) {
                Bukkit.getScheduler().runTaskLater(this, () -> warnPlayersBeforeRestart(1), 20 * 60 * (delay - 1));
            }

            // Tatsächlicher Neustart und Benachrichtigung
            Bukkit.getScheduler().runTaskLater(this, () -> {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    player.sendMessage(ChatColor.RED + info.getMessage());
                }
                sendNitradoRestartRequest();
            }, 20 * 60 * delay);
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