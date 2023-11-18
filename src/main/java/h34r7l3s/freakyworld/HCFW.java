package h34r7l3s.freakyworld;

import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class HCFW implements Listener {

    private final FreakyWorld plugin;
    private final Set<UUID> playersInHCFW;
    private final Map<UUID, Long> playerDeathTimes;
    private Connection connection;

    public HCFW(FreakyWorld plugin) {
        this.plugin = plugin;
        this.playersInHCFW = new HashSet<>();
        this.playerDeathTimes = new HashMap<>();
        setupDatabase();
        loadPlayerDeathTimes();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        new BukkitRunnable() {
            @Override
            public void run() {
                checkPlayerLocks();
                checkAndTeleportPlayers();
            }
        }.runTaskTimer(plugin, 0L, 20L); // Alle 20 Ticks überprüfen
    }

    private void setupDatabase() {
        try {
            File dataFolder = plugin.getDataFolder();
            if (!dataFolder.exists()) {
                dataFolder.mkdirs();
            }
            File dbFile = new File(dataFolder, "player_deaths.db");

            String jdbcUrl = "jdbc:sqlite:" + dbFile.getAbsolutePath();
            connection = DriverManager.getConnection(jdbcUrl);

            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS player_deaths (uuid TEXT PRIMARY KEY, death_time BIGINT)");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        plugin.getLogger().info("Datenbank erfolgreich initialisiert.");
    }

    private void checkPlayerLocks() {
        long currentTimeMillis = System.currentTimeMillis();
        Set<UUID> playersToRemove = new HashSet<>();

        for (UUID playerId : playersInHCFW) {
            Long deathTime = playerDeathTimes.get(playerId);
            if (deathTime != null) {
                long timeSinceDeath = currentTimeMillis - deathTime;
                long lockDurationMillis = TimeUnit.MINUTES.toMillis(30);

                if (timeSinceDeath >= lockDurationMillis) {
                    playersToRemove.add(playerId);
                }
            }
        }

        // Verschieben Sie die Logik zum Entfernen von Spielern nach der Schleife, um ConcurrentModificationExceptions zu vermeiden
        for (UUID playerId : playersToRemove) {
            playersInHCFW.remove(playerId);
            playerDeathTimes.remove(playerId); // Stellen Sie sicher, dass der Todeszeitpunkt ebenfalls entfernt wird
        }
    }


    private void checkAndTeleportPlayers() {
        long currentTimeMillis = System.currentTimeMillis();
        for (Player player : Bukkit.getServer().getWorld("hcfw").getPlayers()) {
            UUID playerId = player.getUniqueId();
            if (playersInHCFW.contains(playerId)) {
                Long deathTime = playerDeathTimes.get(playerId);
                long lockDurationMillis = TimeUnit.MINUTES.toMillis(30);

                if (deathTime != null && currentTimeMillis - deathTime < lockDurationMillis) {
                    // Spieler zurück zur Hauptwelt teleportieren
                    player.teleport(plugin.getServer().getWorld("world").getSpawnLocation());
                    player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 1.0F, 1.0F);
                    long timeRemaining = lockDurationMillis - (currentTimeMillis - deathTime);
                    player.sendMessage(ChatColor.RED + "Du kannst noch nicht in die HCFW gelangen. Bitte warte noch " +
                            TimeUnit.MILLISECONDS.toMinutes(timeRemaining) + " Minuten.");
                }
            } else {
                // Reset playerWelcomedMap und lastWelcomeTimeMap, wenn notwendig
                resetWelcomeStateIfRequired(playerId);
                handlePlayerAllowedInHCFW(player);
            }
        }
    }


    private void resetWelcomeStateIfRequired(UUID playerId) {
        if (!playerDeathTimes.containsKey(playerId)) {
            playerWelcomedMap.remove(playerId);
            lastWelcomeTimeMap.remove(playerId);
        }
    }







    //Ändern in einmaliges ausführen, bei jedem beitreten / joinen in welt "hcfw"
    private final Map<UUID, Boolean> playerWelcomedMap = new HashMap<>();
    private final Map<UUID, Long> lastWelcomeTimeMap = new HashMap<>();

    private void handlePlayerAllowedInHCFW(Player player) {
        UUID playerId = player.getUniqueId();
        if (!playerWelcomedMap.containsKey(playerId) || shouldReWelcome(playerId)) {
            // Spieler wurde noch nicht begrüßt oder sollte erneut begrüßt werden
            int eventProbability = plugin.getDiscordBot().getEventProbability();
            if (eventProbability >= 1) {
                String title = ChatColor.GREEN + "Ein besonderes Event tritt ein!";
                String subtitle = ChatColor.WHITE + "Event-Wahrscheinlichkeit: " + eventProbability + "%";
                player.sendTitle(title, subtitle, 10, 70, 20);
                player.playSound(player.getLocation(), Sound.ENTITY_GHAST_SCREAM, 1.0F, 1.0F);
            } else {
                player.sendTitle(ChatColor.GREEN + "Willkommen in der HCFW!", "", 10, 70, 20);
            }
            playerWelcomedMap.put(playerId, true);
            lastWelcomeTimeMap.put(playerId, System.currentTimeMillis());
        }
    }








    private boolean shouldReWelcome(UUID playerId) {
        Long lastWelcomeTime = lastWelcomeTimeMap.get(playerId);
        long currentTimeMillis = System.currentTimeMillis();
        long reWelcomeDurationMillis = TimeUnit.MINUTES.toMillis(30);
        return (lastWelcomeTime != null && (currentTimeMillis - lastWelcomeTime) >= reWelcomeDurationMillis);
    }








    @EventHandler
    public void onPlayerDeathInHCFW(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (isInHCFW(player)) {
            UUID playerId = player.getUniqueId();
            player.getInventory().clear();
            savePlayerDeathInfo(player);

            // Setzen Sie hier den Spielerzustand zurück und fügen Sie ihn erneut zu playersInHCFW hinzu
            resetPlayerState(playerId);
            playersInHCFW.add(playerId);

            player.teleport(plugin.getServer().getWorld("world").getSpawnLocation());
            player.sendMessage(ChatColor.RED + "Du kannst nicht erneut in die HCFW gelangen, nachdem du gestorben bist.");
        }
    }

    private void resetPlayerState(UUID playerId) {
        playerWelcomedMap.remove(playerId);
        lastWelcomeTimeMap.remove(playerId);
    }
    private void savePlayerDeathInfo(Player player) {
        UUID playerId = player.getUniqueId();
        long timestamp = System.currentTimeMillis();
        try {
            // Überprüfen, ob ein Eintrag für diese UUID existiert
            PreparedStatement checkStmt = connection.prepareStatement("SELECT COUNT(*) FROM player_deaths WHERE uuid = ?");
            checkStmt.setString(1, playerId.toString());
            ResultSet rs = checkStmt.executeQuery();
            if (rs.next() && rs.getInt(1) > 0) {
                // Eintrag existiert, also aktualisieren
                PreparedStatement updateStmt = connection.prepareStatement("UPDATE player_deaths SET death_time = ? WHERE uuid = ?");
                updateStmt.setLong(1, timestamp);
                updateStmt.setString(2, playerId.toString());
                updateStmt.executeUpdate();
                plugin.getLogger().info("Spieler-Todesinformation für " + player.getName() + " aktualisiert.");
            } else {
                // Kein Eintrag, also neuen erstellen
                PreparedStatement insertStmt = connection.prepareStatement("INSERT INTO player_deaths (uuid, death_time) VALUES (?, ?)");
                insertStmt.setString(1, playerId.toString());
                insertStmt.setLong(2, timestamp);
                insertStmt.executeUpdate();
                plugin.getLogger().info("Spieler-Todesinformation für " + player.getName() + " gespeichert.");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Fehler beim Speichern der Spieler-Todesinformation: " + e.getMessage());
        }
    }


    @EventHandler
    public void onPlayerRespawnInHCFW(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (isInHCFW(player)) {
            event.setRespawnLocation(plugin.getServer().getWorld("world").getSpawnLocation());
        }
    }

    private void resetPlayerDeathInfo(Player player) {
        UUID playerId = player.getUniqueId();

        try {
            PreparedStatement statement = connection.prepareStatement("DELETE FROM player_deaths WHERE uuid = ?");
            statement.setString(1, playerId.toString());
            statement.executeUpdate();

            // Spieler aus der HCFW-Welt entfernt, Markierung entfernen
            playerWelcomedMap.remove(playerId);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        plugin.getLogger().info("Spieler-Todesinformation für " + player.getName() + " zurückgesetzt.");
    }


    private void loadPlayerDeathTimes() {
        try {
            PreparedStatement statement = connection.prepareStatement("SELECT * FROM player_deaths");
            ResultSet resultSet = statement.executeQuery();

            while (resultSet.next()) {
                UUID playerId = UUID.fromString(resultSet.getString("uuid"));
                Long deathTime = resultSet.getLong("death_time");
                playerDeathTimes.put(playerId, deathTime);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        plugin.getLogger().info("Spieler-Todeszeiten geladen.");
    }

    private boolean isInHCFW(Player player) {
        return player.getWorld().getName().equalsIgnoreCase("hcfw");
    }
//ab hier neue Funktionen
//impllementieren die gewünscht sind
//ab hier:



}
