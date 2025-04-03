package h34r7l3s.freakyworld;

import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import javax.sql.DataSource;
import java.sql.*;
import java.util.logging.Level;

public class GuildSaver {
    private final JavaPlugin plugin;
    private DataSource dataSource;

    public GuildSaver(JavaPlugin plugin, DataSource dataSource) {
        this.plugin = plugin;
        this.dataSource = dataSource;
        createGuildsTable(); // Create guilds table in the database
    }

    private Connection getConnection() throws SQLException {
        return dataSource.getConnection();
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

        try (Connection connection = getConnection(); Statement statement = connection.createStatement()) {
            statement.execute(createTableSQL);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error creating guilds table", e);
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

        try (Connection connection = getConnection(); Statement statement = connection.createStatement()) {
            statement.execute(createTableSQL);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error creating guild tasks table", e);
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

        try (Connection connection = getConnection(); PreparedStatement preparedStatement = connection.prepareStatement(insertSQL)) {
            preparedStatement.setString(1, name);
            preparedStatement.setString(2, description);
            preparedStatement.setString(3, leader);
            preparedStatement.setString(4, members);
            preparedStatement.setString(5, messages);
            preparedStatement.setString(6, homeLocation);
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error saving guild data", e);
        }
    }

    public Guild loadGuildData(String guildName) {
        String selectSQL = "SELECT * FROM guilds WHERE name = ?";

        try (Connection connection = getConnection(); PreparedStatement preparedStatement = connection.prepareStatement(selectSQL)) {
            preparedStatement.setString(1, guildName);
            ResultSet resultSet = preparedStatement.executeQuery();
            if (resultSet.next()) {
                String description = resultSet.getString("description");
                String leader = resultSet.getString("leader");
                String membersString = resultSet.getString("members");
                String messagesString = resultSet.getString("messages");
                String homeLocationString = resultSet.getString("home_location");

                ItemManager itemManager = new ItemManager(dataSource);
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
            plugin.getLogger().log(Level.SEVERE, "Error loading guild data", e);
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
        String upsertSQL = "INSERT INTO guild_tasks (id, guild_name, description, assigned_member, status) " +
                "VALUES (?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE " +
                "description = VALUES(description), " +
                "assigned_member = VALUES(assigned_member), " +
                "status = VALUES(status)";

        try (Connection connection = getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(upsertSQL)) {
            preparedStatement.setInt(1, task.getId());
            preparedStatement.setString(2, guildName);
            preparedStatement.setString(3, task.getDescription());
            preparedStatement.setString(4, task.getAssignedMember());
            preparedStatement.setString(5, task.getStatus());

            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error saving guild task", e);
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

        try (Connection connection = getConnection(); Statement statement = connection.createStatement()) {
            statement.execute(createTableSQL);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error creating alliances table", e);
        }
    }

    public void loadGuildTasks(Guild guild) {
        String selectSQL = "SELECT * FROM guild_tasks WHERE guild_name = ?";

        try (Connection connection = getConnection(); PreparedStatement preparedStatement = connection.prepareStatement(selectSQL)) {
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
            plugin.getLogger().log(Level.SEVERE, "Error loading guild tasks", e);
        }
    }

    public void deleteGuildTask(int taskId) {
        String deleteSQL = "DELETE FROM guild_tasks WHERE id = ?";
        try (Connection connection = getConnection(); PreparedStatement preparedStatement = connection.prepareStatement(deleteSQL)) {
            preparedStatement.setInt(1, taskId);
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error deleting guild task", e);
        }
    }

    public Guild.GuildRank getMemberRank(String playerName) {
        String selectSQL = "SELECT rank FROM guild_members WHERE member_name = ?"; // Annahme, dass die Tabelle `guild_members` einen `rank` für jedes Mitglied speichert

        try (Connection connection = getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(selectSQL)) {
            preparedStatement.setString(1, playerName);
            ResultSet resultSet = preparedStatement.executeQuery();

            if (resultSet.next()) {
                String rankString = resultSet.getString("rank");
                return Guild.GuildRank.valueOf(rankString.toUpperCase()); // Konvertiert den Datenbankwert in den GuildRank Enum
            }
        } catch (SQLException e) {
            //.getLogger().log(Level.SEVERE, "Error retrieving guild rank for player: " + playerName, e);
        }

        return Guild.GuildRank.MEMBER; // Fallback auf MEMBER, falls kein Rang gefunden oder ein Fehler auftritt
    }
}
