package h34r7l3s.freakyworld;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class CustomDatabaseManager {

    private final FreakyWorld plugin;
    private Connection connection;

    public CustomDatabaseManager(FreakyWorld plugin) {
        this.plugin = plugin;
        initializeDatabase();
    }

    private void initializeDatabase() {
        try {
            File dataFolder = new File(plugin.getDataFolder(), "game_data");
            if (!dataFolder.exists()) {
                dataFolder.mkdirs();
            }
            File databaseFile = new File(dataFolder, "game_system.db");
            connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.getPath());

            // Tabellen erstellen, falls nicht vorhanden
            String createPlayerTable = "CREATE TABLE IF NOT EXISTS player_scores (" +
                    "player_uuid TEXT," +
                    "category TEXT," +
                    "score INTEGER," +
                    "PRIMARY KEY (player_uuid, category))";

            String createGuildTable = "CREATE TABLE IF NOT EXISTS guild_scores (" +
                    "guild_name TEXT," +
                    "category TEXT," +
                    "score INTEGER," +
                    "PRIMARY KEY (guild_name, category))";

            try (PreparedStatement stmt1 = connection.prepareStatement(createPlayerTable);
                 PreparedStatement stmt2 = connection.prepareStatement(createGuildTable)) {
                stmt1.executeUpdate();
                stmt2.executeUpdate();
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            try {
                File dataFolder = new File(plugin.getDataFolder(), "game_data");
                if (!dataFolder.exists()) {
                    dataFolder.mkdirs();
                }
                File databaseFile = new File(dataFolder, "game_system.db");
                connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.getPath());
                plugin.getLogger().info("Datenbankverbindung erfolgreich wiederhergestellt.");
            } catch (SQLException e) {
                throw new SQLException("Fehler beim Herstellen der Verbindung zur Datenbank: " + e.getMessage(), e);
            }
        }
        return connection;
    }

    public void updatePlayerScore(UUID playerUUID, String category, int score) {
        try (Connection conn = getConnection()) {
            String updateSQL = "INSERT INTO player_scores (player_uuid, category, score) VALUES (?, ?, ?) " +
                    "ON CONFLICT(player_uuid, category) DO UPDATE SET score = score + ?";

            try (PreparedStatement stmt = conn.prepareStatement(updateSQL)) {
                stmt.setString(1, playerUUID.toString());
                stmt.setString(2, category);
                stmt.setInt(3, score);
                stmt.setInt(4, score);
                stmt.executeUpdate();
            }

        } catch (SQLException e) {
            plugin.getLogger().severe("Fehler beim Aktualisieren des Spieler-Scores: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public int getPlayerScore(UUID playerUUID, String category) {
        try (Connection conn = getConnection()) {
            String selectSQL = "SELECT score FROM player_scores WHERE player_uuid = ? AND category = ?";
            try (PreparedStatement stmt = conn.prepareStatement(selectSQL)) {
                stmt.setString(1, playerUUID.toString());
                stmt.setString(2, category);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    return rs.getInt("score");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Fehler beim Laden des Spieler-Scores aus der Datenbank: " + e.getMessage());
            e.printStackTrace();
        }
        return 0;
    }

    public void updateGuildScore(String guildName, String category, int score) {
        try (Connection conn = getConnection()) {
            String updateSQL = "INSERT INTO guild_scores (guild_name, category, score) VALUES (?, ?, ?) " +
                    "ON CONFLICT(guild_name, category) DO UPDATE SET score = score + ?";

            try (PreparedStatement stmt = conn.prepareStatement(updateSQL)) {
                stmt.setString(1, guildName);
                stmt.setString(2, category);
                stmt.setInt(3, score);
                stmt.setInt(4, score);
                stmt.executeUpdate();
            }

        } catch (SQLException e) {
            plugin.getLogger().severe("Fehler beim Aktualisieren des Gilden-Scores: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public int getGuildScore(String guildName, String category) {
        try (Connection conn = getConnection()) {
            String selectSQL = "SELECT score FROM guild_scores WHERE guild_name = ? AND category = ?";
            try (PreparedStatement stmt = conn.prepareStatement(selectSQL)) {
                stmt.setString(1, guildName);
                stmt.setString(2, category);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    return rs.getInt("score");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Fehler beim Laden des Gilden-Scores aus der Datenbank: " + e.getMessage());
            e.printStackTrace();
        }
        return 0;
    }

    public void closeConnection() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                plugin.getLogger().info("Datenbankverbindung geschlossen.");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Fehler beim Schließen der Datenbankverbindung: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
