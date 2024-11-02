package h34r7l3s.freakyworld;

import java.sql.*;
import java.io.File;
import java.util.UUID;

public class DatabaseManager {
    private Connection connection;

    public DatabaseManager(File dataFolder) {
        try {
            File dbFile = new File(dataFolder, "freakyworld.db");
            String jdbcUrl = "jdbc:sqlite:" + dbFile.getAbsolutePath();
            connection = DriverManager.getConnection(jdbcUrl);

            initializeDatabase();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void initializeDatabase() {
        try (Statement statement = connection.createStatement()) {
            // Erstellen der Tabellen
            String sqlCreateScores = "CREATE TABLE IF NOT EXISTS scores (" +
                    "uuid TEXT PRIMARY KEY," +
                    "category TEXT," +
                    "score INTEGER);";
            statement.executeUpdate(sqlCreateScores);

            // Weitere Tabellen und Initialisierungen nach Bedarf
        } catch (SQLException e) {
            e.printStackTrace();
        }
        try (Statement statement = connection.createStatement()) {
            String sqlCreatePlayerData = "CREATE TABLE IF NOT EXISTS player_data (" +
                    "uuid TEXT PRIMARY KEY," +
                    "kills INTEGER DEFAULT 0," +
                    "sword_level INTEGER DEFAULT 0);";
            statement.executeUpdate(sqlCreatePlayerData);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void updatePlayerData(UUID uuid, int kills, int swordLevel) {
        String sql = "INSERT INTO player_data (uuid, kills, sword_level) VALUES(?,?,?) " +
                "ON CONFLICT(uuid) DO UPDATE SET kills=?, sword_level=?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, uuid.toString());
            pstmt.setInt(2, kills);
            pstmt.setInt(3, swordLevel);
            pstmt.setInt(4, kills);
            pstmt.setInt(5, swordLevel);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public int[] getPlayerData(UUID uuid) {
        String sql = "SELECT kills, sword_level FROM player_data WHERE uuid=?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, uuid.toString());
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                int kills = rs.getInt("kills");
                int swordLevel = rs.getInt("sword_level");
                return new int[]{kills, swordLevel};
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return new int[]{0, 0}; // Standardwerte, falls keine Daten gefunden wurden
    }


// In der DatabaseManager-Klasse

    public void updatePlayerScore(UUID playerUUID, String category, int scoreToAdd, boolean isTeamMode) {
        // Holen Sie zuerst die aktuelle Punktzahl des Spielers
        int currentScore = getPlayerScore(playerUUID, category);
        int newScore = currentScore + scoreToAdd;

        String sql = "INSERT INTO scores (uuid, category, score) VALUES (?, ?, ?) " +
                "ON CONFLICT(uuid, category) DO UPDATE SET score = score + ?;";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, playerUUID.toString());
            pstmt.setString(2, category);
            pstmt.setInt(3, newScore);
            pstmt.setInt(4, scoreToAdd);

            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }




    public int getPlayerScore(UUID playerUUID, String category) {
        String sql = "SELECT score FROM scores WHERE uuid = ? AND category = ?;";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, playerUUID.toString());
            pstmt.setString(2, category);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("score");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }


    // Weitere Methoden zum Interagieren mit der Datenbank
}
