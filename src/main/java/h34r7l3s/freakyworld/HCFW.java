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
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (isPlayerLocked(player)) {
            if (isInHCFW(player)) {
                blockPlayerAccess(player);
            } else {
                informPlayerOfWaitTime(player);
            }
        } else {
            welcomePlayer(player);
        }
    }

    private void informPlayerOfWaitTime(Player player) {
        long timeRemaining = TimeUnit.MINUTES.toMillis(30) - (System.currentTimeMillis() - playerDeathTimes.get(player.getUniqueId()));
        if (timeRemaining > 0) {
            player.sendMessage(ChatColor.RED + "Du kannst noch nicht in die HCFW gelangen. Bitte warte noch " +
                    TimeUnit.MILLISECONDS.toMinutes(timeRemaining) + " Minuten.");
        }
    }

    @EventHandler
    public void onPlayerDeathInHCFW(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (isInHCFW(player)) {
            UUID playerId = player.getUniqueId();
            playerDeathTimes.put(playerId, System.currentTimeMillis());
            savePlayerDeathInfo(playerId);
            // Löschen Sie alle Items, die der Spieler fallen lässt
            event.getDrops().clear();

            player.sendMessage(ChatColor.RED + "Du kannst nicht erneut in die HCFW gelangen, nachdem du gestorben bist.");
        }
    }

    @EventHandler
    public void onPlayerRespawnInHCFW(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (isInHCFW(player)) {
            event.setRespawnLocation(plugin.getServer().getWorld("world").getSpawnLocation());
        }
    }

    private enum LockStatus {
        LOCKED, UNLOCKED, NOT_LOCKED
    }

    private LockStatus getPlayerLockStatus(Player player) {
        Long deathTime = playerDeathTimes.get(player.getUniqueId());
        if (deathTime == null) {
            return LockStatus.NOT_LOCKED;
        }
        long timeSinceDeath = System.currentTimeMillis() - deathTime;
        if (timeSinceDeath < TimeUnit.MINUTES.toMillis(30)) {
            return LockStatus.LOCKED;
        } else {
            return LockStatus.UNLOCKED;
        }
    }

    private void checkAndTeleportPlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (isInHCFW(player)) {
                LockStatus status = getPlayerLockStatus(player);
                if (status == LockStatus.LOCKED) {
                    blockPlayerAccess(player);
                } else if (status == LockStatus.UNLOCKED) {
                    playersInHCFW.add(player.getUniqueId()); // Spieler zur HCFW-Liste hinzufügen
                    welcomePlayer(player); // Willkommen heißen, wenn gerade entsperrt
                }
            }
        }
    }


    private boolean isPlayerLocked(Player player) {
        Long deathTime = playerDeathTimes.get(player.getUniqueId());
        if (deathTime == null) {
            return false;
        }
        long timeSinceDeath = System.currentTimeMillis() - deathTime;
        return timeSinceDeath < TimeUnit.MINUTES.toMillis(30);
    }

    private void blockPlayerAccess(Player player) {
        player.teleport(plugin.getServer().getWorld("world").getSpawnLocation());
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 1.0F, 1.0F);
        long timeRemaining = TimeUnit.MINUTES.toMillis(30) - (System.currentTimeMillis() - playerDeathTimes.get(player.getUniqueId()));
        player.sendMessage(ChatColor.RED + "Du kannst noch nicht in die HCFW gelangen. Bitte warte noch " +
                TimeUnit.MILLISECONDS.toMinutes(timeRemaining) + " Minuten.");
    }

    private void welcomePlayer(Player player) {
        if (!isInHCFW(player)) return;
        int eventProbability = plugin.getDiscordBot().getEventProbability();
        if (eventProbability >= 1) {
            player.sendTitle(ChatColor.GREEN + "Ein besonderes Event tritt ein!",
                    ChatColor.WHITE + "Event-Wahrscheinlichkeit: " + eventProbability + "%", 10, 70, 20);
            player.playSound(player.getLocation(), Sound.ENTITY_GHAST_SCREAM, 1.0F, 1.0F);
        } else {
            player.sendTitle(ChatColor.GREEN + "Willkommen in der HCFW!", "", 10, 70, 20);
        }
    }

    private void savePlayerDeathInfo(UUID playerId) {
        long timestamp = System.currentTimeMillis();
        try {
            PreparedStatement checkStmt = connection.prepareStatement("SELECT COUNT(*) FROM player_deaths WHERE uuid = ?");
            checkStmt.setString(1, playerId.toString());
            ResultSet rs = checkStmt.executeQuery();
            if (rs.next() && rs.getInt(1) > 0) {
                PreparedStatement updateStmt = connection.prepareStatement("UPDATE player_deaths SET death_time = ? WHERE uuid = ?");
                updateStmt.setLong(1, timestamp);
                updateStmt.setString(2, playerId.toString());
                updateStmt.executeUpdate();
            } else {
                PreparedStatement insertStmt = connection.prepareStatement("INSERT INTO player_deaths (uuid, death_time) VALUES (?, ?)");
                insertStmt.setString(1, playerId.toString());
                insertStmt.setLong(2, timestamp);
                insertStmt.executeUpdate();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private boolean isInHCFW(Player player) {
        boolean inHCFW = player.getWorld().getName().equalsIgnoreCase("hcfw");
        if (!inHCFW) {
            //plugin.getLogger().info("Spieler ist nicht in der Welt 'hcfw'.");
        }
        return inHCFW;
    }
-
    //weitere Hardocre Elemente

}

