package h34r7l3s.freakyworld;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

public class GuildSaver {
    private final JavaPlugin plugin;
    private Connection dbConnection;

    public GuildSaver(JavaPlugin plugin, Connection dbConnection) {
        this.plugin = plugin;
        this.dbConnection = dbConnection;
        createGuildsTable(); // Erstellen Sie die Gilden-Tabelle in der Datenbank

    }

    public void createGuildsTable() {
        String createTableSQL = "CREATE TABLE IF NOT EXISTS guilds (" +
                "name VARCHAR(255) PRIMARY KEY," +
                "description TEXT," +
                "leader VARCHAR(255)," +
                "members TEXT," +
                "messages TEXT," +
                "home_location TEXT" +
                ")";

        try (Statement statement = dbConnection.createStatement()) {
            statement.execute(createTableSQL);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Fehler beim Erstellen der Gilden-Tabelle", e);
        }
    }
    public void createGuildTasksTable() {
        String createTableSQL = "CREATE TABLE IF NOT EXISTS guild_tasks (" +
                "id INT AUTO_INCREMENT PRIMARY KEY," +
                "guild_name VARCHAR(255)," +
                "description TEXT," +
                "assigned_member VARCHAR(255)," +
                "status VARCHAR(255)," +
                "FOREIGN KEY (guild_name) REFERENCES guilds(name)" +
                ")";

        try (Statement statement = dbConnection.createStatement()) {
            statement.execute(createTableSQL);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Fehler beim Erstellen der Guild-Tasks-Tabelle", e);
        }
    }

    public void saveGuildData(Guild guild) {
        String name = guild.getName();
        String description = guild.getDescription();
        String leader = guild.getLeader();
        String members = String.join(",", guild.getMembers());
        String messages = String.join(",", guild.getMessages());
        String homeLocation = serializeLocation(guild.getHomeLocation());

        String insertSQL = "INSERT INTO guilds (name, description, leader, members, messages, home_location) " +
                "VALUES (?, ?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE " +
                "description = VALUES(description), " +
                "leader = VALUES(leader), " +
                "members = VALUES(members), " +
                "messages = VALUES(messages), " +
                "home_location = VALUES(home_location)";

        try (PreparedStatement preparedStatement = dbConnection.prepareStatement(insertSQL)) {
            preparedStatement.setString(1, name);
            preparedStatement.setString(2, description);
            preparedStatement.setString(3, leader);
            preparedStatement.setString(4, members);
            preparedStatement.setString(5, messages);
            preparedStatement.setString(6, homeLocation);
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Fehler beim Speichern der Gilden-Daten", e);
        }
    }

    public Guild loadGuildData(String guildName) {
        String selectSQL = "SELECT * FROM guilds WHERE name = ?";

        try (PreparedStatement preparedStatement = dbConnection.prepareStatement(selectSQL)) {
            preparedStatement.setString(1, guildName);
            ResultSet resultSet = preparedStatement.executeQuery();
            if (resultSet.next()) {
                String description = resultSet.getString("description");
                String leader = resultSet.getString("leader");
                String membersString = resultSet.getString("members");
                String messagesString = resultSet.getString("messages");
                String homeLocationString = resultSet.getString("home_location");

                ItemManager itemManager = new ItemManager(dbConnection);
                Guild guild = new Guild(guildName, leader, itemManager);
                guild.setDescription(description);
                for (String member : membersString.split(",")) {
                    guild.addMember(member, Guild.GuildRank.MEMBER);
                }
                for (String message : messagesString.split(",")) {
                    guild.addMessage(message);
                }
                guild.setHomeLocation(deserializeLocation(homeLocationString));
                loadGuildTasks(guild);
                return guild;
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Fehler beim Laden der Gilden-Daten", e);
        }
        return null;
    }

    private String serializeLocation(Location location) {
        if (location == null) {
            return null;
        }
        return location.getWorld().getName() + ";" + location.getX() + ";" + location.getY() + ";" + location.getZ();
    }

    private Location deserializeLocation(String locationString) {
        if (locationString == null) {
            return null;
        }
        String[] parts = locationString.split(";");
        if (parts.length == 4) {
            String worldName = parts[0];
            double x = Double.parseDouble(parts[1]);
            double y = Double.parseDouble(parts[2]);
            double z = Double.parseDouble(parts[3]);
            return new Location(plugin.getServer().getWorld(worldName), x, y, z);
        }
        return null;
    }
    public void saveGuildTask(Guild.GuildTask task, String guildName) {
        String insertSQL = "INSERT INTO guild_tasks (guild_name, description, assigned_member, status) " +
                "VALUES (?, ?, ?, ?)";

        try (PreparedStatement preparedStatement = dbConnection.prepareStatement(insertSQL, Statement.RETURN_GENERATED_KEYS)) {
            preparedStatement.setString(1, guildName);
            preparedStatement.setString(2, task.getDescription());
            preparedStatement.setString(3, task.getAssignedMember());
            preparedStatement.setString(4, task.getStatus());
            int affectedRows = preparedStatement.executeUpdate();

            if (affectedRows == 0) {
                throw new SQLException("Creating task failed, no rows affected.");
            }

            try (ResultSet generatedKeys = preparedStatement.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    task.setId(generatedKeys.getInt(1)); // Hier setzen Sie die ID auf das GuildTask-Objekt
                } else {
                    throw new SQLException("Creating task failed, no ID obtained.");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Fehler beim Speichern der Gilden-Aufgaben", e);
        }
    }
    public void createAlliancesTable() {
        String createTableSQL = "CREATE TABLE IF NOT EXISTS alliances (" +
                "guild_name_1 VARCHAR(255)," +
                "guild_name_2 VARCHAR(255)," +
                "status VARCHAR(255)," +
                "timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "PRIMARY KEY (guild_name_1, guild_name_2)," +
                "FOREIGN KEY (guild_name_1) REFERENCES guilds(name)," +
                "FOREIGN KEY (guild_name_2) REFERENCES guilds(name)" +
                ")";

        try (Statement statement = dbConnection.createStatement()) {
            statement.execute(createTableSQL);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Fehler beim Erstellen der Allianzen-Tabelle", e);
        }
    }



    public void loadGuildTasks(Guild guild) {
        String selectSQL = "SELECT * FROM guild_tasks WHERE guild_name = ?";

        try (PreparedStatement preparedStatement = dbConnection.prepareStatement(selectSQL)) {
            preparedStatement.setString(1, guild.getName());
            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                int id = resultSet.getInt("id");
                String description = resultSet.getString("description");
                String assignedMember = resultSet.getString("assigned_member");
                String status = resultSet.getString("status");

                Guild.GuildTask task = guild.new GuildTask(id, description);
                task.setAssignedMember(assignedMember);
                task.setStatus(status);
                guild.addTask(task);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Fehler beim Laden der Gilden-Aufgaben", e);
        }
    }



}
